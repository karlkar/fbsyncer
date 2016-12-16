package com.kksionek.fbsyncer;

import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.RealmResults;

public class FBSyncService extends Service {

    private static final String TAG = "FBSyncService";
    private static final int CONTACT_ID_INDEX = 0;
    private static final int DISPLAY_NAME_PRIMARY_INDEX = 1;

    private static final String[] PROJECTION =
            {
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
            };

    private final IBinder mBinder = new MyLocalBinder();
    private ISyncListener mSyncListener = null;

    private AccessToken mAccessToken = null;
    private CallbackManager mCallbackManager;
    private final AtomicBoolean mContactsLoaded = new AtomicBoolean(false);
    private final AtomicBoolean mFbLoaded = new AtomicBoolean(false);
    private final ExecutorService mThreadPool = Executors.newFixedThreadPool(2);

    @Override
    public void onCreate() {
        super.onCreate();
        AppEventsLogger.activateApp(getApplication());
        mCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                mAccessToken = loginResult.getAccessToken();
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "onError: Failed to login to facebook");
            }
        });
    }

    public void setListener(ISyncListener listener) {
        mSyncListener = listener;
    }

    private void getContacts() {
        mContactsLoaded.set(false);
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Friend> contacts = realm.where(Friend.class)
                .equalTo("mFacebook", false)
                .findAll();
        Cursor cursor = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, PROJECTION, null, null, null);
        String rawContactId;
        String displayName;
        String thumbnailPath;

        realm.beginTransaction();
        for (Friend contact : contacts)
            contact.setOld(true);
        while (cursor.moveToNext()) {
            rawContactId = cursor.getString(CONTACT_ID_INDEX);
            displayName = cursor.getString(DISPLAY_NAME_PRIMARY_INDEX);
            thumbnailPath = getThumbnailOf(rawContactId);
            Friend newContact = new Friend(rawContactId, displayName, thumbnailPath);
            RealmResults<Friend> preUpdateContact = realm.where(Friend.class)
                    .equalTo("mId", rawContactId)
                    .findAll();
            if (preUpdateContact.size() > 0) {
                if (preUpdateContact.first().getRelated() != null)
                    newContact.setRelated(preUpdateContact.first().getRelated());
            }
            realm.insertOrUpdate(newContact);
        }
        RealmResults<Friend> oldContacts = contacts.where()
                .equalTo("mOld", true)
                .findAll();
        Log.d(TAG, "getContacts: " + oldContacts.size() + " contacts were deleted from phone.");
        oldContacts.deleteAllFromRealm();
        realm.commitTransaction();
        realm.close();
        mContactsLoaded.set(true);
    }

    private String getThumbnailOf(String rawContactId) {
        String contactId;
        Cursor cur = getContentResolver()
                .query(ContactsContract.Data.CONTENT_URI,
                        new String[] {ContactsContract.Data.CONTACT_ID},
                        ContactsContract.Data.RAW_CONTACT_ID
                                + "="
                                + rawContactId
                                + " AND "
                                + ContactsContract.Data.MIMETYPE
                                + "='"
                                + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                                + "'", null, null);
        if (cur != null && cur.moveToFirst()) {
            contactId = cur.getString(0);
            cur.close();
            return Uri.withAppendedPath(
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                            Long.parseLong(contactId)),
                    ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
                    .toString();
        } else
            return null;
    }

    private void getFbContacts() {
        mFbLoaded.set(false);
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Friend> friends = realm.where(Friend.class)
                .equalTo("mFacebook", true)
                .findAll();
        realm.beginTransaction();
        for (Friend friend : friends)
            friend.setOld(true);
        realm.commitTransaction();
        mAccessToken = AccessToken.getCurrentAccessToken();
        if (mAccessToken == null)
            return;
        requestFriends(null);
        RealmResults<Friend> oldFriends = friends.where()
                .equalTo("mOld", true)
                .findAll();
        Log.d(TAG, "getFbContacts: " + oldFriends.size() + " friends were deleted from Facebook");
        realm.beginTransaction();
        oldFriends.deleteAllFromRealm();
        realm.commitTransaction();
        realm.close();
        mFbLoaded.set(true);
    }

    public void startSync() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (mSyncListener != null)
                    mSyncListener.onSyncStarted();
            }

            @Override
            protected Void doInBackground(Void... voids) {
                getContacts();
                getFbContacts();

                performSync();

                Log.d(TAG, "doInBackground: DONE");
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (mSyncListener != null)
                    mSyncListener.onSyncEnded();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void syncSingle(final String contactId, final String photo) {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (mSyncListener != null)
                    mSyncListener.onSyncStarted();
            }

            @Override
            protected Void doInBackground(Void... voids) {
                setContactPhoto(contactId, photo);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (mSyncListener != null)
                    mSyncListener.onSyncEnded();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void requestFriends(@Nullable String nextToken) {
        GraphRequest req = new GraphRequest(mAccessToken, "/me/taggable_friends", null, HttpMethod.GET, new GraphRequest.Callback() {
            @Override
            public void onCompleted(GraphResponse response) {
//                Log.d(TAG, "onCompleted: response = " + response.toString());
                if (response.getError() != null)
                    Log.e(TAG, "onCompleted: Couldn't obtain friend data.");
                else {
                    try {
                        JSONArray friendArray = response.getJSONObject().getJSONArray("data");
                        Realm realm = Realm.getDefaultInstance();
                        realm.beginTransaction();
                        for (int i = 0; i < friendArray.length(); ++i) {
//                            Log.d(TAG, "onCompleted: FRIEND = " + friendArray.get(i).toString());
                            realm.insertOrUpdate(new Friend(friendArray.getJSONObject(i)));
                        }
                        realm.commitTransaction();
                        realm.close();
                        if (!response.getJSONObject().isNull("paging")) {
                            String token = response.getJSONObject().getJSONObject("paging").getJSONObject("cursors").getString("after");
                            requestFriends(token);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        if (nextToken != null) {
            Bundle parameters = new Bundle();
            parameters.putString("after", nextToken);
            req.setParameters(parameters);
        }
        req.executeAndWait();
    }

    private void performSync() {
        List<Callable<Void>> callables = new ArrayList<>();
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Friend> all = realm.where(Friend.class).findAll();
        RealmResults<Friend> contacts = realm.where(Friend.class)
                .equalTo("mFacebook", false).findAll();
        RealmResults<Friend> friends = realm.where(Friend.class)
                .equalTo("mFacebook", true).findAll();

        realm.beginTransaction();

        for (Friend friend : all)
            friend.setSynced(false);

        for (final Friend contact : contacts) {
            int idx;
            if ((idx = friends.indexOf(contact)) != -1) {
                final Friend friend = friends.get(idx);
                callables.add(() -> {
                    setContactPhoto(contact.getId(), friend.getPhoto());
                    return null;
                });
                contact.setSynced(true);
                contact.setRelated(friend);
            } else if (contact.getRelated() != null) {
                Log.d(TAG, "performSync: [" + contact.getName() + "] - syncing using previous settings");
                callables.add(() -> {
                    setContactPhoto(contact.getId(), contact.getRelated().getPhoto());
                    return null;
                });
                contact.setSynced(true);
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
        Cursor cursor = getContentResolver().query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Contacts.Data._ID}, where, null, null);
        if (cursor.moveToFirst())
            photoRow = cursor.getInt(0);
        cursor.close();

        if (photoRow >= 0) {
            getContentResolver().update(
                    ContactsContract.Data.CONTENT_URI,
                    values,
                    ContactsContract.Data._ID + " = " + photoRow, null);
        } else {
            getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
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

                String line;
                int counter = 0;
                Pattern p = Pattern.compile("src=\"(.+?" + m.group(1) + ".+?)\"");
                while ((line = reader.readLine()) != null) {
                    m = p.matcher(line);
                    while (m.find()) {
                        if (++counter >= 2) {
                            reader.close();
                            return m.group(1).replace("&amp;", "&");
                        }
                    }
                }
                reader.close();
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return photo;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mSyncListener = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThreadPool.shutdownNow();
    }

    public class MyLocalBinder extends Binder {
        FBSyncService getService() {
            return FBSyncService.this;
        }
    }
}
