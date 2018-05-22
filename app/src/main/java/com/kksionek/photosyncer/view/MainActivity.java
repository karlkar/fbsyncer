package com.kksionek.photosyncer.view;

import android.Manifest;
import android.accounts.Account;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.model.SecurePreferences;
import com.kksionek.photosyncer.sync.AccountUtils;
import com.kksionek.photosyncer.sync.SyncAdapter;

import io.realm.Realm;
import okhttp3.OkHttpClient;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS_CONTACTS = 4444;
    private static final int REQUEST_WHITELIST_PERMISSION = 1616;

    private static final String TAG = "MainActivity";
    private TextView mTextView;
    private ProgressBar mLoginProgress;
    private LinearLayout mFbLoginForm;
    private EditText mFbLogin;
    private EditText mFbPass;
    private Button mQuestionBtn;

    private Realm mRealmUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = findViewById(R.id.questionTextView);
        mLoginProgress = findViewById(R.id.loginProgress);
        mFbLoginForm = findViewById(R.id.fbLoginForm);
        mFbLogin = findViewById(R.id.fbLogin);
        mFbPass = findViewById(R.id.fbPass);
        mQuestionBtn = findViewById(R.id.questionButton);

        mRealmUi = Realm.getDefaultInstance();

        showPermissionRequestScreen();
    }

    @Override
    protected void onDestroy() {
        mRealmUi.close();
        super.onDestroy();
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
        SecurePreferences prefs = new SecurePreferences(getBaseContext(), "tmp", "NoTifiCationHandLer", true);
        if ((prefs.getString("PREF_LOGIN") == null || prefs.getString("PREF_LOGIN").isEmpty())
                && (prefs.getString("PREF_PASSWORD") == null || prefs.getString("PREF_PASSWORD").isEmpty())) {
            mTextView.setText(R.string.activity_main_facebook_permission_request_message);
            showProgress(false);
            mQuestionBtn.setText(R.string.activity_main_button_login_facebook);
            mQuestionBtn.setOnClickListener(v -> {
                showProgress(true);
                String login = mFbLogin.getText().toString();
                String pass = mFbPass.getText().toString();

                if (!login.isEmpty() && !pass.isEmpty()) {
                    prefs.put("PREF_LOGIN", login);
                    prefs.put("PREF_PASSWORD", pass);
                    SyncAdapter.fbLogin(new OkHttpClient(), getBaseContext())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    s -> {
                                        mLoginProgress.setVisibility(View.GONE);
                                        showWhitelistScreen();
                                    },
                                    throwable -> {
                                        showProgress(false);
                                        prefs.clear();
                                        Toast.makeText(getBaseContext(), R.string.activity_main_login_failed_toast, Toast.LENGTH_SHORT).show();
                                    });
                } else {
                    showProgress(false);
                    Toast.makeText(getBaseContext(), R.string.activity_main_login_failed_toast, Toast.LENGTH_SHORT).show();
                }
            });
        } else
            showWhitelistScreen();
    }

    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mFbLoginForm.setVisibility(show ? View.GONE : View.VISIBLE);
        mFbLoginForm.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFbLoginForm.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mLoginProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        mLoginProgress.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginProgress.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void showWhitelistScreen() {
        if (isSmartManagerInstalled()) {
            mTextView.setText(R.string.activity_main_whitelist_message);
            mQuestionBtn.setText(R.string.activity_main_whitelist_btn);
            mQuestionBtn.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Log.d(TAG, "showWhitelistScreen: Strange... Application wasn't detected...");
                }
                showSyncScreen();
            });
        } else
            showSyncScreen();
    }

    private boolean isSmartManagerInstalled() {
        PackageManager pm = getPackageManager();
        try {
            pm.getPackageInfo("com.samsung.android.sm", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            // skip it
        }
        return false;
    }

    private void showSyncScreen() {
        mTextView.setText(R.string.activity_main_prerequisites_fulfilled_message);
        mQuestionBtn.setText(R.string.activity_main_button_sync);
        mQuestionBtn.setOnClickListener(v -> {
            mQuestionBtn.setEnabled(false);
            if (!AccountUtils.isAccountCreated(getBaseContext())) {
                Account account = AccountUtils.createAccount(this);
                if (account != null) {
                    ContentResolver.requestSync(account, AccountUtils.CONTENT_AUTHORITY, new Bundle());
                    Intent tabIntent = new Intent(this, TabActivity.class);
                    startActivity(tabIntent);
                    finish();
                } else {
                    Log.e(TAG, "showSyncScreen: I couldn't create a new account. That's strange");
                    finish();
                }
            } else {
                ContentResolver.requestSync(AccountUtils.getAccount(), AccountUtils.CONTENT_AUTHORITY, new Bundle());
                Intent tabIntent = new Intent(this, TabActivity.class);
                startActivity(tabIntent);
                finish();
            }
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
