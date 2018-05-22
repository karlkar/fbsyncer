package com.kksionek.photosyncer.view;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.data.Contact;
import com.kksionek.photosyncer.data.Friend;
import com.kksionek.photosyncer.model.SecurePreferences;
import com.kksionek.photosyncer.sync.AccountUtils;

import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmResults;

public class TabActivity extends AppCompatActivity {

    public static final int REQUEST_FACEBOOK_PICKER = 4445;

    private static final String TAG = "TABACTIVITY";
    public static final String PREF_LAST_AD = "LAST_AD";

    private Realm mRealmUi;
    private MenuItemSyncCtrl mMenuItemSyncCtrl = null;
    private ViewPager mPager;
    private SyncStatusObserver mSyncStatusObserver = which -> runOnUiThread(() -> {
        Account account = AccountUtils.getAccount();
        boolean syncActive = ContentResolver.isSyncActive(
                account, AccountUtils.CONTENT_AUTHORITY);
        boolean syncPending = ContentResolver.isSyncPending(
                account, AccountUtils.CONTENT_AUTHORITY);

        //Log.d(TAG, "Event received: " + (syncActive || syncPending));
        if (mMenuItemSyncCtrl != null) {
            if (syncActive || syncPending)
                mMenuItemSyncCtrl.startAnimation();
            else
                mMenuItemSyncCtrl.endAnimation();
        }
    });
    private Object mSyncObserverHandle;
    private InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecurePreferences prefs = new SecurePreferences(getBaseContext(), "tmp", "NoTifiCationHandLer", true);
        if (((prefs.getString("PREF_LOGIN") == null || prefs.getString("PREF_LOGIN").isEmpty())
                && (prefs.getString("PREF_PASSWORD") == null || prefs.getString("PREF_PASSWORD").isEmpty())) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                                || checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED))
                || !AccountUtils.isAccountCreated(this)) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }

        setContentView(R.layout.activity_tab);

        mRealmUi = Realm.getDefaultInstance();

        ViewPagerAdapter adapter = new ViewPagerAdapter(this, mRealmUi);
        mPager = findViewById(R.id.pager);
        mPager.setAdapter(adapter);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        for (int i = 0; i < adapter.getCount(); ++i)
            tabLayout.addTab(tabLayout.newTab().setText(adapter.getPageTitle(i)));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        mPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        showAdIfNeeded();
    }

    private void showAdIfNeeded() {
        long lastAd = PreferenceManager.getDefaultSharedPreferences(this).getLong(PREF_LAST_AD, 0);

        if (lastAd == 0) {
            lastAd = System.currentTimeMillis();
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putLong(PREF_LAST_AD, lastAd)
                    .apply();
        }

        long diff = System.currentTimeMillis() - lastAd;
        long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

        if (days >= 7 || getIntent().getBooleanExtra("INTENT_AD", false)) {
            mInterstitialAd = new InterstitialAd(this);
            mInterstitialAd.setAdUnitId(getString(R.string.interstitial_ad_unit_id));
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    PreferenceManager.getDefaultSharedPreferences(TabActivity.this)
                            .edit()
                            .putLong(PREF_LAST_AD, System.currentTimeMillis())
                            .apply();
                    mInterstitialAd.show();
                }

                @Override
                public void onAdFailedToLoad(int errorCode) {
                }

                @Override
                public void onAdClosed() {
                }
            });
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("3795279CABB4FAC75E5F23A086CC9C9F")
                    .build();
            mInterstitialAd.loadAd(adRequest);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_sync);
        if (menuItem != null)
            mMenuItemSyncCtrl = new MenuItemSyncCtrl(this, menuItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_sync) {
            ContentResolver.requestSync(
                    AccountUtils.getAccount(),
                    AccountUtils.CONTENT_AUTHORITY,
                    new Bundle());
            return true;
        } else if (item.getItemId() == R.id.menu_privacy_policy) {
            Intent browserIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://novelstudio.pl/karol/photosyncer/privacy_policy.html"));
            startActivity(browserIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int mask = ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE | ContentResolver.SYNC_OBSERVER_TYPE_PENDING;
        mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask, mSyncStatusObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
    }

    @Override
    protected void onDestroy() {
        mRealmUi.close();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FACEBOOK_PICKER) {
            if (resultCode == RESULT_OK) {
                String contactId = data.getStringExtra(FacebookPickerActivity.EXTRA_ID);
                Contact contact = mRealmUi.where(Contact.class)
                        .equalTo("mId", contactId)
                        .findFirst();
                String friendId = data.getStringExtra(FacebookPickerActivity.EXTRA_RESULT_ID);
                Friend friend = mRealmUi.where(Friend.class)
                        .equalTo("mId", friendId)
                        .findFirst();
                RealmResults<Friend> sameNameFriends = mRealmUi.where(Friend.class)
                        .equalTo("mName", friend.getName())
                        .findAll();

                mRealmUi.executeTransaction(realm -> {
                    contact.setRelated(friend);
                    contact.setSynced(true);
                    contact.setManual(true);
                });
                // TODO: Sync single

                if (sameNameFriends.size() > 1) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.problem);
                    builder.setMessage(R.string.problem_message);
                    builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
                    builder.create().show();
                }

                Toast.makeText(this, R.string.sync_preference_saved, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
