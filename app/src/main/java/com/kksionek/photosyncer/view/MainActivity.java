package com.kksionek.photosyncer.view;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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
import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.sync.AccountUtils;

import java.util.Arrays;

import io.realm.Realm;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;
    private static final int REQUEST_WHITELIST_PERMISSION = 1616;

    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private Button mQuestionBtn;
    private CallbackManager mCallbackManager;

    private Realm mRealmUi;

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
                showWhitelistScreen();
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

        mRealmUi = Realm.getDefaultInstance();

        showPermissionRequestScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRealmUi.close();
    }

    private void showPermissionRequestScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED)) {
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
        } else
            showFbLoginScreen();
    }

    private void showFbLoginScreen() {
        if (AccessToken.getCurrentAccessToken() == null) {
            mTextView.setText(R.string.activity_main_facebook_permission_request_message);
            mQuestionBtn.setText(R.string.activity_main_button_login_facebook);
            mQuestionBtn.setOnClickListener(v ->
                    LoginManager.getInstance().logInWithReadPermissions(
                            MainActivity.this,
                            Arrays.asList("user_friends")));
        } else
            showWhitelistScreen();
    }

    private void showWhitelistScreen() {
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            mTextView.setText(R.string.activity_main_whitelist_message);
            mQuestionBtn.setText(R.string.activity_main_whitelist_btn);
            mQuestionBtn.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivityForResult(intent, REQUEST_WHITELIST_PERMISSION);
            });
        } else
            showSyncScreen();
    }

    private void showSyncScreen() {
        mTextView.setText(R.string.activity_main_prerequisites_fulfilled_message);
        mQuestionBtn.setText(R.string.activity_main_button_sync);
        mQuestionBtn.setOnClickListener(v -> {
            mQuestionBtn.setEnabled(false);
            AccountManager systemService = (AccountManager) getSystemService(ACCOUNT_SERVICE);
            Account account = AccountUtils.getAccount();
            if (systemService.addAccountExplicitly(account, null, null)) {
                ContentResolver.setIsSyncable(account, AccountUtils.CONTENT_AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(account, AccountUtils.CONTENT_AUTHORITY, true);
                ContentResolver.addPeriodicSync(account, AccountUtils.CONTENT_AUTHORITY, new Bundle(),
                        24 * 60 * 60);
            }
            ContentResolver.requestSync(account, AccountUtils.CONTENT_AUTHORITY, new Bundle());
            Intent tabIntent = new Intent(this, TabActivity.class);
            startActivity(tabIntent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_WHITELIST_PERMISSION) {
            if (resultCode == RESULT_OK)
                showSyncScreen();
            else
                Toast.makeText(this, R.string.activity_main_whitelist_not_granted_toast, Toast.LENGTH_LONG).show();
            return;
        }

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
}
