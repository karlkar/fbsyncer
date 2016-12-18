package com.kksionek.fbsyncer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.util.Arrays;

import io.realm.Realm;

public class MainActivity extends AppCompatActivity implements ISyncListener {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;

    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private Button mQuestionBtn;
    private CallbackManager mCallbackManager;

    private Realm mRealm;

    private FBSyncService mService;

    private final ServiceConnection mConnection = new ServiceConnection() {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.questionTextView);
        mQuestionBtn = (Button) findViewById(R.id.questionButton);

        mCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                showSyncScreen();
            }

            @Override
            public void onCancel() {
                Toast.makeText(MainActivity.this, R.string.activity_main_login_cancelled_message, Toast.LENGTH_LONG).show();
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

        mRealm = Realm.getDefaultInstance();
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
        mRealm.close();
    }

    private void showPermissionRequestScreen() {
        mTextView.setText(R.string.activity_main_grant_contacts_access_message);
        mQuestionBtn.setText(R.string.activity_main_button_grant_contacts_access);
        mQuestionBtn.setOnClickListener(v ->
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.WRITE_CONTACTS},
                        REQUEST_PERMISSIONS_CONTACTS)
        );
    }

    private void showFbLoginScreen() {
        mTextView.setText(R.string.activity_main_facebook_permission_request_message);
        mQuestionBtn.setText(R.string.activity_main_button_login_facebook);
        mQuestionBtn.setOnClickListener(v ->
                LoginManager.getInstance().logInWithReadPermissions(
                        MainActivity.this,
                        Arrays.asList("user_friends")));
    }

    private void showSyncScreen() {
        mTextView.setText(R.string.activity_main_prerequisites_fulfilled_message);
        mQuestionBtn.setText(R.string.activity_main_button_sync);
        mQuestionBtn.setOnClickListener(v -> {
            if (mService != null)
                mService.startSync();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_CONTACTS) {
            if (grantResults.length == 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED)
                showFbLoginScreen();
            else
                Toast.makeText(this,
                        R.string.activity_main_permission_rejected_message,
                        Toast.LENGTH_LONG).show();
        } else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onSyncStarted() {
        mQuestionBtn.setEnabled(false);
        Snackbar.make(mTextView, R.string.activity_main_sync_started, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSyncEnded() {
        mQuestionBtn.setEnabled(true);
        Snackbar.make(mTextView, R.string.activity_main_sync_ended, Snackbar.LENGTH_LONG).show();
        showNotSyncedScreen();
    }

    private void showNotSyncedScreen() {
        Intent intent = new Intent(this, TabActivity.class);
        startActivity(intent);
        finish();
    }
}
