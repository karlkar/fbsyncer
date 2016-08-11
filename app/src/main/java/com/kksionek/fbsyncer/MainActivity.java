package com.kksionek.fbsyncer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements ISyncListener {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;
    private static final int REQUEST_CONTACT_PICKER = 4445;
    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private Button mQuestionBtn;
    private ListView mListView;
    private CallbackManager mCallbackManager;
    private Friend mClickedFriend = null;
    private final ImageCache mImageCache = new ImageCache();

    private FBSyncService mService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            FBSyncService.MyLocalBinder binder = (FBSyncService.MyLocalBinder) iBinder;
            mService = binder.getService();
            mService.setListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };
    private MyAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.questionTextView);
        mQuestionBtn = (Button) findViewById(R.id.questionButton);
        mListView = (ListView) findViewById(R.id.listView);

        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(getApplication());
        mCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
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
            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            if (accessToken == null)
                showFbLoginScreen();
            else
                showSyncScreen();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mService == null) {
            Intent intent = new Intent(this, FBSyncService.class);
            bindService(intent, mConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mConnection);
            mService = null;
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
        mListView.setVisibility(View.GONE);
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
        mListView.setVisibility(View.GONE);
    }

    private void showSyncScreen() {
        mTextView.setText("All prerequirements are fulfilled - I have access to your contacts and you're logged in to Facebook account. We can start the process now!");
        mQuestionBtn.setText("Sync");
        mQuestionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mService != null)
                    mService.startSync();
            }
        });
        mListView.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == RESULT_OK) {
                Cursor c =  managedQuery(ContactsContract.RawContacts.CONTENT_URI,  new String[] {ContactsContract.RawContacts._ID}, ContactsContract.RawContacts.CONTACT_ID + " = " +  data.getData().getLastPathSegment(), null, null);
                if (c.moveToFirst()) {
                    String id = c.getString(0);
                    if (mService != null) {
                        mService.syncSingle(id, mClickedFriend.getPhoto());
                    }
                }
            }
            mClickedFriend = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_CONTACTS) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)
                showFbLoginScreen();
            else
                Toast.makeText(this, "You didn't grant me full access to your contacts. I cannot sync them.", Toast.LENGTH_LONG).show();
            return;
        } else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onSyncStarted() {
        mQuestionBtn.setEnabled(false);
        Snackbar.make(mTextView, "Sync started", Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSyncEnded() {
        mQuestionBtn.setEnabled(true);
        Snackbar.make(mTextView, "Sync ended", Snackbar.LENGTH_LONG).show();
        showNotSyncedScreen();
    }

    private void showNotSyncedScreen() {
        mTextView.setText("Not all contacts were synced. Here's the list of those");
        mQuestionBtn.setVisibility(View.GONE);
        mQuestionBtn.setOnClickListener(null);
        mListView.setVisibility(View.VISIBLE);

        List<Friend> friends = new ArrayList<>();
        List<Friend> notSyncedContacts;
        int offset = 0;
        int limit = 50;
        do {
            if (mService != null) {
                notSyncedContacts = mService.getNotSyncedFriends(offset, limit);
                if (notSyncedContacts.size() == limit)
                    offset += limit;
                else
                    offset = -1;
                friends.addAll(notSyncedContacts);
            }
        } while (offset != -1);

        Collections.sort(friends);

        mAdapter = new MyAdapter(this, friends);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mClickedFriend == null) {
                    mClickedFriend = (Friend) parent.getItemAtPosition(position);
                    Intent contactPicketIntent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                    startActivityForResult(contactPicketIntent, REQUEST_CONTACT_PICKER);
                }
            }
        });
    }

    private class MyAdapter extends ArrayAdapter<Friend> {

        class ViewHolder {
            ImageView imageView;
            TextView textView;
            int position;
            AtomicBoolean mLoading = new AtomicBoolean(false);
        }

        public MyAdapter(Context context, List<Friend> objects) {
            super(context, R.layout.row_friends, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.row_friends, parent, false);
                holder = new ViewHolder();
                holder.imageView = (ImageView) convertView.findViewById(R.id.thumbnail);
                holder.textView = (TextView) convertView.findViewById(R.id.text);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.position = position;
            Friend friend = getItem(position);
            holder.textView.setText(friend.getName());
            Drawable drawable = mImageCache.takeDrawable(friend.getName());
            if (drawable != null)
                holder.imageView.setImageDrawable(drawable);
            else if (holder.mLoading.compareAndSet(false, true)) {
                ThumbnailLoader loader = new ThumbnailLoader(holder, position, friend);
                loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            return convertView;
        }

        private class ThumbnailLoader extends AsyncTask<Void, Void, Drawable> {

            private final ViewHolder mViewHolder;
            private final int mPosition;
            private final Friend mFriend;

            public ThumbnailLoader(ViewHolder viewHolder, int position, Friend friend) {
                mViewHolder = viewHolder;
                mPosition = position;
                mFriend = friend;
            }

            @Override
            protected Drawable doInBackground(Void... params) {
                try {
                    InputStream is = (InputStream) new URL(mFriend.getPhoto()).getContent();
                    Drawable d = Drawable.createFromStream(is, null);
                    is.close();
                    return d;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                super.onPostExecute(drawable);
                mImageCache.addToMap(mFriend.getName(), drawable);
                if (mPosition == mViewHolder.position)
                    mViewHolder.imageView.setImageDrawable(drawable);
                mViewHolder.mLoading.set(false);
            }
        }
    }
}
