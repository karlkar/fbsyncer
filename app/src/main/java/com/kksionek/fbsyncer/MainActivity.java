package com.kksionek.fbsyncer;

import android.Manifest;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;
    private static final int LOADER_CONTACTS = 0;
    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private Button mQuestionBtn;
    private CallbackManager mCallbackManager;
    private AccessToken mAccessToken = null;
    private List<Friend> mContactList = null;
    private AtomicBoolean mContactsLoaded = new AtomicBoolean(false);

    private static final int CONTACT_ID_INDEX = 0;
    private static final int DISPLAY_NAME_PRIMARY_INDEX = 1;

    private static final String[] PROJECTION =
        {
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY
        };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.questionTextView);
        mQuestionBtn = (Button) findViewById(R.id.questionButton);

        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(getApplication());
        mCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                mAccessToken = loginResult.getAccessToken();
                showSyncScreen();
            }

            @Override
            public void onCancel() {
                Toast.makeText(MainActivity.this, "To continue, you have to login to facebook.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "onError: Failed to login to facebook");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED)) {
            showPermissionRequestScreen();
        } else {
            startContactLoader();
            showFbLoginScreen();
        }
    }

    private void showPermissionRequestScreen() {
        mTextView.setText("In order to sync your contact images I need an access to your contacts. I will have to read them and modify if I will find a proper image. Please press the button to grant me access.");
        mQuestionBtn.setText("Grant access");
        mQuestionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS}, REQUEST_PERMISSIONS_CONTACTS);
            }
        });
    }

    private void showFbLoginScreen() {
        mTextView.setText("In order to sync your contact images I need an access to your Facebook account. I will use it only to obtain profile images of your friends. Please press the button to login to Facebook.");
        mQuestionBtn.setText("Login to facebook");
        mQuestionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginManager.getInstance().logInWithReadPermissions(MainActivity.this, Arrays.asList("user_friends"));
            }
        });
    }

    private void showSyncScreen() {
        mTextView.setText("All prerequirements are fulfilled - I have access to your contacts and ou're logged in to Facebook account. We can start the process now!");
        mQuestionBtn.setText("Sync");
        mQuestionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Friend> friends = new ArrayList<>();
                Snackbar.make(v, "Sync started", Snackbar.LENGTH_LONG).show();
                requestFriends(friends, null);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_CONTACTS) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startContactLoader();
                showFbLoginScreen();
            } else {
                Toast.makeText(this, "You didn't grant me full access to your contacts. I cannot sync them.", Toast.LENGTH_LONG).show();
            }
            return;
        } else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startContactLoader() {
        getLoaderManager().initLoader(LOADER_CONTACTS, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                return new CursorLoader(MainActivity.this, ContactsContract.RawContacts.CONTENT_URI, PROJECTION, null, null, null);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                if (mContactList == null)
                    mContactList = new ArrayList<>();
                else
                    mContactList.clear();
                String contactId;
                String displayName;
                while (data.moveToNext()) {
                    contactId = data.getString(CONTACT_ID_INDEX);
                    displayName = data.getString(DISPLAY_NAME_PRIMARY_INDEX);
                    mContactList.add(new Friend(contactId, displayName));
                }
                mContactsLoaded.set(true);
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                mContactList.clear();
            }
        });
    }

    private void requestFriends(@NonNull final List<Friend> friendList, String nextToken) {
        GraphRequest req = new GraphRequest(mAccessToken, "/me/taggable_friends", null, HttpMethod.GET, new GraphRequest.Callback() {
            @Override
            public void onCompleted(GraphResponse response) {
                Log.d(TAG, "onCompleted: response = " + response.toString());
                if (response.getError() != null)
                    Log.e(TAG, "onCompleted: Couldn't obtain friend data.");
                else {
                    try {
                        JSONArray friendArray = response.getJSONObject().getJSONArray("data");
                        for (int i = 0; i < friendArray.length(); ++i) {
                            Log.d(TAG, "onCompleted: FRIEND = " + friendArray.get(i).toString());
                            friendList.add(new Friend(friendArray.getJSONObject(i)));
                        }
                        if (!response.getJSONObject().isNull("paging")) {
                            String token = response.getJSONObject().getJSONObject("paging").getJSONObject("cursors").getString("after");
                            requestFriends(friendList, token);
                        } else {
                            // All FB friends are downloaded
                            onFbFriendsObtained(friendList);
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
        req.executeAsync();
    }

    private void onFbFriendsObtained(@NonNull List<Friend> friendList) {
        if (mContactsLoaded.get()) {
            List<Friend> notSyncedContacts = new ArrayList<>();
            List<Friend> notSyncedFriends = new ArrayList<>();

            for (Friend friend : mContactList)
                notSyncedContacts.add(friend);

            for (Friend friend : friendList) {
                int idx;
                if ((idx = mContactList.indexOf(friend)) != -1) {
                    Friend contact = mContactList.get(idx);
                    setContactPhoto(contact, friend.getPhoto());
                    notSyncedContacts.remove(contact);
                    notSyncedFriends.add(friend);
                } else
                    notSyncedFriends.add(friend);
            }
        }
    }

    private void setContactPhoto(@NonNull final Friend friend, @NonNull final String photo) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {

                Bitmap bitmap = getBitmapFromURL(photo);
                if (bitmap == null)
                    return null;
                ByteArrayOutputStream streamy = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, streamy);
                byte[] photoData = streamy.toByteArray();
                ContentValues values = new ContentValues();

                values.put(ContactsContract.Data.RAW_CONTACT_ID, friend.getId());
                values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
                values.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData);
                values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);

                int photoRow = -1;
                String where = ContactsContract.Data.RAW_CONTACT_ID + " = " + friend.getId() + " AND " + ContactsContract.Data.MIMETYPE + "=='" + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";
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
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
}
