package com.kksionek.photosyncer.sync;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.data.Contact;
import com.kksionek.photosyncer.data.Friend;
import com.kksionek.photosyncer.model.RxContacts;
import com.kksionek.photosyncer.model.SecurePreferences;
import com.kksionek.photosyncer.view.TabActivity;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

import static com.kksionek.photosyncer.view.TabActivity.PREF_LAST_AD;

public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SYNCADAPTER";
    private final ExecutorService mThreadPool = Executors.newFixedThreadPool(2);

    private static final MediaType URLENCODED
            = MediaType.parse("application/x-www-form-urlencoded");
    private OkHttpClient mOkHttpClient;
    private Pattern mUidPattern = Pattern.compile("\\?uid=(.*?)&");

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    public SyncAdapter(Context context, boolean autoInitialize,
                       boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String s,
                              ContentProviderClient contentProviderClient, SyncResult syncResult) {
        Log.d(TAG, "onPerformSync: START");
        Observable.zip(getAndRealmContacts(), getAndRealmFriends(), (contacts, friends) -> 1)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.immediate())
                .toBlocking()
                .subscribe(
                        o -> performSync(),
                        throwable -> {
                            Log.e(TAG, "onPerformSync: Wrong login/password");
                            throwable.printStackTrace();
                        });
        showNotification();
        Log.d(TAG, "onPerformSync: END");
    }

    private void showNotification() {
        long lastAd = PreferenceManager.getDefaultSharedPreferences(getContext()).getLong(PREF_LAST_AD, 0);

        if (lastAd == 0) {
            lastAd = System.currentTimeMillis();
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .edit()
                    .putLong(PREF_LAST_AD, lastAd)
                    .apply();
        }

        long diff = System.currentTimeMillis() - lastAd;
        long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

        if (days < 7)
            return;

        Intent intent = new Intent(getContext(), TabActivity.class);
        intent.putExtra("INTENT_AD", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext());
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getContext().getString(R.string.app_name))
                .setContentText(getContext().getString(R.string.notification_synchronisation_done))
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.FLAG_SHOW_LIGHTS)
                .setLights(0xff00ff00, 300, 100)
                .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builder.setPriority(Notification.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager notificationManager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

    @NonNull
    private Observable<List<Contact>> getContactsRx() {
        return RxContacts.fetch(getContext())
                .subscribeOn(Schedulers.io())
                .toList();
    }

    @NonNull
    private Observable<List<Contact>> getAndRealmContacts() {
        return getContactsRx()
                .doOnNext(contacts -> {
                    if (Looper.myLooper() == null)
                        Looper.prepare();
                    Log.d(TAG, "getAndRealmContacts: Found " + contacts.size() + " contacts");
                    Realm ioRealm = Realm.getDefaultInstance();
                    ioRealm.beginTransaction();

                    // Prepare Realm database so it knows which contacts were updated
                    ioRealm.where(Contact.class)
                            .findAll()
                            .asObservable()
                            .subscribeOn(Schedulers.immediate())
                            .flatMap(Observable::from)
                            .forEach(realmContact -> realmContact.setOld(true));

                    Contact preUpdateContact;
                    for (Contact newContact : contacts) {
                        preUpdateContact = ioRealm.where(Contact.class)
                                .equalTo("mId", newContact.getId())
                                .findFirst();
                        if (preUpdateContact != null) {
                            newContact.setRelated(preUpdateContact.getRelated());
                            newContact.setManual(preUpdateContact.isManual());
                        }
                    }

                    ioRealm.insertOrUpdate(contacts);

                    // Remove friends that doesn't exist anymore
                    ioRealm.where(Contact.class)
                            .equalTo("mOld", true)
                            .findAll()
                            .asObservable()
                            .subscribeOn(Schedulers.immediate())
                            .subscribe(RealmResults::deleteAllFromRealm);

                    ioRealm.commitTransaction();
                    ioRealm.close();
                });
    }

    public static Single<String> fbLogin(OkHttpClient okHttpClient, Context context) {
        return Single.fromEmitter(singleEmitter -> {
            SecurePreferences prefs = new SecurePreferences(context, "tmp", "NoTifiCationHandLer", true);
            if ((prefs.getString("PREF_LOGIN") == null || prefs.getString("PREF_LOGIN").isEmpty())
                    && (prefs.getString("PREF_PASSWORD") == null || prefs.getString("PREF_PASSWORD").isEmpty()))
                singleEmitter.onError(new Exception("Login and/or password not set"));

            Request req = new Request.Builder()
                    .url("https://m.facebook.com/login.php?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&refsrc=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&_rdr")
                    .build();

            String responseStr = null;
            try {
                Response response = okHttpClient.newCall(req).execute();
                if (response.isSuccessful())
                    responseStr = response.body().string();
                response.close();
            } catch (IOException e) {
                singleEmitter.onError(e);
                return;
            }

            if (responseStr == null) {
                singleEmitter.onError(new Exception("Response is null"));
                return;
            }

            Document doc = Jsoup.parse(responseStr);
            Element lsd = doc.select("input[name=lsd]").first();
            Element m_ts = doc.select("input[name=m_ts]").first();
            Element li = doc.select("input[name=li]").first();

            RequestBody reqBody;
            try {
                reqBody = RequestBody.create(URLENCODED,
                        "email=" + URLEncoder.encode(prefs.getString("PREF_LOGIN"), "utf-8") + "&" +
                                "pass=" + URLEncoder.encode(prefs.getString("PREF_PASSWORD"), "utf-8") + "&" +
                                "lsd=" + lsd.val() + "&" +
                                "version=1&" +
                                "width=0&" +
                                "pxr=0&" +
                                "gps=0&" +
                                "dimensions=0&" +
                                "ajax=0&" +
                                "m_ts=" + m_ts.val() + "&" +
                                "login=Zaloguj+si%C4%99&" +
                                "_fb_noscript=true&" +
                                "li=" + li.val() + "");
            } catch (UnsupportedEncodingException e) {
                singleEmitter.onError(e);
                return;
            }
            req = new Request.Builder()
                    .url("https://m.facebook.com/login.php?next=https://m.facebook.com/friends/center/friends/&refsrc=https://m.facebook.com/friends/center/friends/&lwv=100&login_try_number=1&refid=9")
                    .post(reqBody)
                    .build();

            try {
                Response response = okHttpClient.newCall(req).execute();
                if (response.isSuccessful())
                    responseStr = response.body().string();
                response.close();
            } catch (IOException e) {
                singleEmitter.onError(e);
                return;
            }
            if (responseStr == null) {
                singleEmitter.onError(new Exception("Response is null"));
                return;
            }
            if (responseStr.contains("login_form")) {
                Log.e(TAG, "fbLogin: Wrong login/password");
                prefs.clear();
                singleEmitter.onError(new Exception("Wrong login and/or password"));
            }
            singleEmitter.onSuccess(responseStr);
        });
    }

    private Observable<List<Friend>> getRxFriends() {
        if (mOkHttpClient == null) {

//            HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
//            httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            mOkHttpClient = new OkHttpClient.Builder()
                    .cookieJar(new CookieJar() {
                        private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

                        @Override
                        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                            if (cookies.size() == 1)
                                return;
                            cookieStore.put(url.host(), cookies);
                        }

                        @Override
                        public List<Cookie> loadForRequest(HttpUrl url) {
                            List<Cookie> cookies = cookieStore.get(url.host());
                            return cookies != null ? cookies : new ArrayList<>();
                        }
                    })
//                    .addInterceptor(httpLoggingInterceptor)
                    .build();
        }
        return fbLogin(mOkHttpClient, getContext())
                .subscribeOn(Schedulers.io())
                .flatMapObservable(resp -> {
                    int ppk = 0;
                    ArrayList<String> uids = new ArrayList<>();
                    do {
                        Matcher matcher = mUidPattern.matcher(resp);
                        while (matcher.find()) {
                            String group = matcher.group(1);
                            uids.add(group);
                        }
                        if (uids.isEmpty()) {
                            FirebaseCrash.log("[" + ppk + "] No friend uids were found in `resp` = " + resp);
                        }

                        ++ppk;
                        Request req = new Request.Builder()
                                .url("https://m.facebook.com/friends/center/friends/?ppk=" + ppk + "&bph=" + ppk + "#friends_center_main")
                                .build();
                        try {
                            Response response = mOkHttpClient.newCall(req).execute();
                            if (response.isSuccessful())
                                resp = response.body().string();
                            response.close();
                        } catch (IOException e) {
                            return Observable.error(e);
                        }
                    } while (resp.contains("?uid="));
                    Log.d(TAG, "getRxFriends: Found " + uids.size() + " friends.");
                    return Observable.from(uids);
                })
                .flatMap(s -> Observable.just(s)
                        .subscribeOn(Schedulers.io())
                        .flatMap(this::getRxFriend), 1)
                .filter(friend -> friend != null)
                .toList();
    }

    private Observable<Friend> getRxFriend(@NonNull String uid) {
        return Observable.fromCallable(() -> {
            Request req = new Request.Builder()
                    .url("https://m.facebook.com/friends/hovercard/mbasic/?uid=" + uid + "&redirectURI=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F%3Frefid%3D9%26mfl_act%3D1%23last_acted")
                    .build();

            String responseStr;

            Response response = mOkHttpClient.newCall(req).execute();
            if (response.isSuccessful())
                responseStr = response.body().string();
            else {
                response.close();
                return null;
            }
            response.close();

            int profPicIdx = responseStr.indexOf("profpic img");
            if (profPicIdx == -1)
                profPicIdx = responseStr.indexOf("class=\"w p\"");
            if (profPicIdx == -1) {
                FirebaseCrash.log("Cannot find picture for friend. Page = " + responseStr);
                return null;
            }
            responseStr = responseStr.substring(Math.max(0, profPicIdx - 400), Math.min(profPicIdx + 200, responseStr.length()));
            String photoUrl = null;
            Pattern p = Pattern.compile("src=\"(.+?_\\d\\d+?_.+?)\"");
            Matcher m = p.matcher(responseStr);
            if (m.find()) {
                photoUrl = m.group(1).replace("&amp;", "&");
            }

            String name = null;
            Pattern p2 = Pattern.compile("alt=\"(.+?)\"");
            m = p2.matcher(responseStr);
            if (m.find()) {
                name = StringEscapeUtils.unescapeHtml4(m.group(1));
            }
            return new Friend(uid, name, photoUrl);
        });
    }

    private Bitmap getBitmapFromURL(@NonNull String src) {
        try {
            Request req = new Request.Builder()
                    .url(src)
                    .build();
            Response resp = mOkHttpClient.newCall(req).execute();
            if (resp.isSuccessful()) {
                InputStream inputStream = resp.body().byteStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                resp.close();
                return bitmap;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NonNull
    private Observable<List<Friend>> getAndRealmFriends() {
        return getRxFriends()
                .doOnNext(friends -> {
                    if (Looper.myLooper() == null)
                        Looper.prepare();
                    Realm ioRealm = Realm.getDefaultInstance();
                    ioRealm.beginTransaction();

                    // Prepare Realm database so it knows which friends were updated
                    ioRealm.where(Friend.class)
                            .findAll()
                            .asObservable()
                            .subscribeOn(Schedulers.immediate())
                            .flatMap(Observable::from)
                            .forEach(realmContact -> realmContact.setOld(true));

                    for (Friend friend : friends)
                        ioRealm.insertOrUpdate(friend);

                    ioRealm.where(Friend.class)
                            .equalTo("mOld", true)
                            .findAll().deleteAllFromRealm();
                    ioRealm.commitTransaction();
                    ioRealm.close();
                });
    }

    private void performSync() {
        List<Callable<Void>> callables = new ArrayList<>();
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Contact> contacts = realm.where(Contact.class).findAll();
//        RealmResults<Friend> friends = realm.where(Friend.class).findAll();

        realm.beginTransaction();

        // All contacts in database are new => they have mSynced set to false
//        for (Contact contact : contacts)
//            contact.setSynced(false);
//        for (Friend friend : friends)
//            friend.setSynced(false);

        for (Contact contact : contacts) {
            if (contact.isManual()) {
                if (contact.getRelated() != null)
                    syncToRelated(callables, realm, contact, true);
            } else {
                RealmResults<Friend> sameNameFriends = realm.where(Friend.class)
                        .equalTo("mName", contact.getName())
                        .findAll();
                if (sameNameFriends.size() == 1) {
                    contact.setRelated(sameNameFriends.first());
                    syncToRelated(callables, realm, contact, false);
                } else {
                    if (sameNameFriends.isEmpty())
                        Log.d(TAG, "performSync: [" + contact.getName() + "] Friend doesn't exist on social network.");
                    else
                        Log.d(TAG, "performSync: [" + contact.getName() + "] Friend exists multiple times and connot be synced automatically.");
                    contact.setSynced(false);
                }
            }
        }
        realm.commitTransaction();
        realm.close();

        try {
            mThreadPool.invokeAll(callables);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void syncToRelated(List<Callable<Void>> callables, Realm realm, Contact contact, boolean manual) {
        if (manual)
            Log.d(TAG, "syncToRelated: [" + contact.getName() + "] - syncing using manual settings.");
        else
            Log.d(TAG, "syncToRelated: [" + contact.getName() + "] - syncing using auto settings.");
        final Contact copiedContact = realm.copyFromRealm(contact);
        callables.add(() -> {
            setContactPhoto(copiedContact.getId(), copiedContact.getRelated().getPhoto());
            return null;
        });
        contact.setSynced(true);
        contact.setManual(manual);
    }

    private void setContactPhoto(@NonNull final String rawContactId, @NonNull final String photo) {
        Bitmap bitmap = getBitmapFromURL(photo);
        if (bitmap == null)
            return;

        ByteArrayOutputStream streamy = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, streamy);
        byte[] photoData = streamy.toByteArray();
        ContentValues values = new ContentValues();

        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
        values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);

        Log.d(TAG, "setContactPhoto: id = " + rawContactId);

        int photoRow = -1;
        String where = ContactsContract.Data.RAW_CONTACT_ID + " = " + rawContactId
                + " AND " + ContactsContract.Data.MIMETYPE + "=='"
                + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";
        Cursor cursor = getContext().getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Contacts.Data._ID},
                where,
                null,
                null);
        if (cursor != null) {
            if (cursor.moveToFirst())
                photoRow = cursor.getInt(0);
            cursor.close();
        }

        if (photoRow >= 0) {
            getContext().getContentResolver().update(
                    ContactsContract.Data.CONTENT_URI,
                    values,
                    ContactsContract.Data._ID + " = " + photoRow, null);
        } else {
            Log.d(TAG, "setContactPhoto: INSERT " + rawContactId);
            getContext().getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
        }
        try {
            streamy.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
