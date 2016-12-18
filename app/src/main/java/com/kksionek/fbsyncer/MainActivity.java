package com.kksionek.fbsyncer;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
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
import io.realm.RealmResults;
import io.realm.Sort;

public class MainActivity extends AppCompatActivity implements ISyncListener {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;
    private static final int REQUEST_FACEBOOK_PICKER = 4445;
    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private Button mQuestionBtn;
    private CallbackManager mCallbackManager;

    private Realm mRealm;

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
        mTextView.setText("All prerequirements are fulfilled - I have access to your contacts and you're logged in to Facebook account. We can start the process now!");
        mQuestionBtn.setText("Sync");
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
        Intent intent = new Intent(this, TabActivity.class);
        startActivity(intent);
        finish();
    }
}
