package com.kksionek.fbsyncer.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.kksionek.fbsyncer.data.Contact;
import com.kksionek.fbsyncer.data.Friend;
import com.kksionek.fbsyncer.model.RxContacts;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmResults;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SYNCADAPTER";
    private final ExecutorService mThreadPool = Executors.newFixedThreadPool(2);

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
                .subscribe(o -> performSync());
        Log.d(TAG, "onPerformSync: END");
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
                    Realm ioRealm = Realm.getDefaultInstance();
                    ioRealm.beginTransaction();

                    // Prepare Realm database so it knows which contacts were updated
                    ioRealm.where(Contact.class)
                            .findAll()
                            .asObservable()
                            .subscribeOn(Schedulers.immediate())
                            .flatMap(allContacts -> Observable.from(allContacts))
                            .forEach(realmContact -> realmContact.setOld(true));

                    Contact preUpdateContact;
                    for (Contact newContact : contacts) {
                        preUpdateContact = ioRealm.where(Contact.class)
                                .equalTo("mId", newContact.getId())
                                .findFirst();
                        if (preUpdateContact != null)
                            newContact.setRelated(preUpdateContact.getRelated());
                    }

                    ioRealm.insertOrUpdate(contacts);

                    // Remove friends that doesn't exist anymore
                    ioRealm.where(Contact.class)
                            .equalTo("mOld", true)
                            .findAll()
                            .asObservable()
                            .subscribeOn(Schedulers.immediate())
                            .subscribe(oldContacts -> oldContacts.deleteAllFromRealm());

                    ioRealm.commitTransaction();
                    ioRealm.close();
                });
    }

    @NonNull
    public Observable<List<Friend>> getFriendsRx() {
        return Observable.<GraphResponse>create(subscriber -> {
            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            if (accessToken == null) {
                subscriber.onError(new NullPointerException("AccessToken is null"));
                return;
            }
            String nextToken = "";
            Bundle params;
            GraphResponse response;
            while (nextToken != null) {
                params = null;
                if (!nextToken.isEmpty()) {
                    params = new Bundle();
                    params.putString("after", nextToken);
                }
                response = new GraphRequest(accessToken, "/me/taggable_friends",
                        params, HttpMethod.GET).executeAndWait();
                if (response.getError() != null) {
                    subscriber.onError(response.getError().getException());
                    nextToken = null;
                } else {
                    subscriber.onNext(response);
                    if (response.getJSONObject().isNull("paging")) {
                        nextToken = null;
                        subscriber.onCompleted();
                    } else {
                        try {
                            nextToken = response.getJSONObject().getJSONObject("paging").getJSONObject("cursors").getString("after");
                        } catch (JSONException e) {
                            e.printStackTrace();
                            subscriber.onError(e);
                            nextToken = null;
                        }
                    }
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .flatMap(response -> {
                    List<Friend> list = new ArrayList<>();
                    try {
                        JSONArray friendArray = response.getJSONObject().getJSONArray("data");
                        for (int i = 0; i < friendArray.length(); ++i) {
                            list.add(new Friend(friendArray.getJSONObject(i)));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return Observable.from(list);
                }).toList();
    }

    @NonNull
    private Observable<List<Friend>> getAndRealmFriends() {
        return getFriendsRx()
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
                            .flatMap(allContacts -> Observable.from(allContacts))
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
            RealmResults<Friend> sameNameFriends = realm.where(Friend.class)
                    .equalTo("mName", contact.getName())
                    .findAll();
            if (sameNameFriends.size() == 1)
                contact.setRelated(sameNameFriends.first());
            final Contact copiedContact = realm.copyFromRealm(contact);
            if (sameNameFriends.size() == 1) {
                callables.add(() -> {
                    setContactPhoto(copiedContact.getId(), copiedContact.getRelated().getPhoto());
                    return null;
                });
                contact.setSynced(true);
                contact.setManual(false);
                contact.getRelated().setSynced(true);
            } else if (contact.getRelated() != null) {
                Log.d(TAG, "performSync: [" + contact.getName() + "] - syncing using manual settings.");
                callables.add(() -> {
                    setContactPhoto(copiedContact.getId(), copiedContact.getRelated().getPhoto());
                    return null;
                });
                contact.setSynced(true);
                contact.setManual(true);
                contact.getRelated().setSynced(true);
            } else if (sameNameFriends.size() > 1) {
                Log.d(TAG, "performSync: [" + contact.getName() + "] Friend exists multiple times and connot be synced automatically.");
            } else
                contact.setSynced(false);
        }
        realm.commitTransaction();
        realm.close();

        try {
            mThreadPool.invokeAll(callables);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setContactPhoto(@NonNull final String friendId, @NonNull final String photo) {
        Bitmap bitmap = getBitmapFromURL(photo);
        if (bitmap == null)
            return;

        ByteArrayOutputStream streamy = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, streamy);
        byte[] photoData = streamy.toByteArray();
        ContentValues values = new ContentValues();

        values.put(ContactsContract.Data.RAW_CONTACT_ID, friendId);
        values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
        values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData);
        values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);

        Log.d(TAG, "setContactPhoto: id = " + friendId);

        int photoRow = -1;
        String where = ContactsContract.Data.RAW_CONTACT_ID + " = " + friendId + " AND " + ContactsContract.Data.MIMETYPE + "=='" + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";
        Cursor cursor = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Contacts.Data._ID}, where, null, null);
        if (cursor.moveToFirst())
            photoRow = cursor.getInt(0);
        cursor.close();

        if (photoRow >= 0) {
            getContext().getContentResolver().update(
                    ContactsContract.Data.CONTENT_URI,
                    values,
                    ContactsContract.Data._ID + " = " + photoRow, null);
        } else {
            getContext().getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
        }
        try {
            streamy.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Bitmap getBitmapFromURL(@NonNull String src) {
        src = tryToGetBetterQualityPic(src);

        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            connection.disconnect();
            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String tryToGetBetterQualityPic(@NonNull String photo) {
        Pattern r = Pattern.compile("_(\\d+?)_");

        Matcher m = r.matcher(photo);
        if (m.find()) {
            try {
                URL url = new URL("https://www.facebook.com/photo.php?fbid=" + m.group(1));
                HttpURLConnection.setFollowRedirects(true);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:46.0) Gecko/20100101 Firefox/46.0");
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                StringBuilder builder = new StringBuilder();
                String line;
                Pattern p = Pattern.compile("src=\"(.+?" + m.group(1) + ".+?)\"");
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                reader.close();
                connection.disconnect();

                String body = builder.toString();
                m = p.matcher(body);
                while (m.find()) {
                    if (m.group(1).contains("50x50/"))
                        continue;
                    if (!m.group(1).contains("scontent"))
                        continue;
                    reader.close();
                    return m.group(1).replace("&amp;", "&");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return photo;
    }
}
