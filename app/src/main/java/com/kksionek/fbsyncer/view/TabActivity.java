package com.kksionek.fbsyncer.view;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.kksionek.fbsyncer.data.Contact;
import com.kksionek.fbsyncer.model.ContactsAdapter;
import com.kksionek.fbsyncer.data.Friend;
import com.kksionek.fbsyncer.R;
import com.kksionek.fbsyncer.sync.AccountUtils;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class TabActivity extends AppCompatActivity implements ISyncListener {

    private static final int REQUEST_FACEBOOK_PICKER = 4445;

    private Realm mRealmUi;
    private MenuItemSyncCtrl mMenuItemSyncCtrl = null;
    private ViewPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AccessToken.getCurrentAccessToken() == null ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED))) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        setContentView(R.layout.activity_tab);

        mRealmUi = Realm.getDefaultInstance();

        ViewPagerAdapter adapter = new ViewPagerAdapter();
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_sync);
        if (menuItem != null)
            mMenuItemSyncCtrl = new MenuItemSyncCtrl(this, menuItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_sync) {
            Account account = new Account(AccountUtils.ACCOUNT_NAME, AccountUtils.ACCOUNT_TYPE);
            ContentResolver.requestSync(account, AccountUtils.ACCOUNT_AUTHORITY, new Bundle());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRealmUi.close();
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
                        .equalTo("mGeneratedId", friendId)
                        .findFirst();
                RealmResults<Friend> sameNameFriends = mRealmUi.where(Friend.class)
                        .equalTo("mName", friend.getName())
                        .findAll();

                mRealmUi.executeTransaction(realm -> {
                    contact.setRelated(friend);
                    contact.setSynced(true);
                    contact.getRelated().setSynced(true);
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

    @Override
    public void onSyncStarted() {
        if (mMenuItemSyncCtrl != null)
            mMenuItemSyncCtrl.startAnimation();
        Snackbar.make(mPager, R.string.activity_tab_sync_started, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSyncEnded() {
        if (mMenuItemSyncCtrl != null)
            mMenuItemSyncCtrl.endAnimation();
        Snackbar.make(mPager, R.string.activity_tab_sync_ended, Snackbar.LENGTH_LONG).show();
    }

    private class ViewPagerAdapter extends PagerAdapter {

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Context ctx = container.getContext();
            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.tab_not_synced, container, false);
            container.addView(view);

            RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(ctx);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.addItemDecoration(
                    new DividerItemDecoration(
                            TabActivity.this,
                            DividerItemDecoration.VERTICAL));
//            mTextView.setText("Not all contacts were synced. Here's the list of those");

            switch (position) {
                case 0: {
                    RealmResults<Contact> notSyncedContacts = mRealmUi.where(Contact.class)
                            .equalTo("mSynced", false)
                            .findAllSorted("mName", Sort.ASCENDING);

                    ContactsAdapter<Contact> contactsAdapter = new ContactsAdapter<>(TabActivity.this, notSyncedContacts, true);
                    contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                        @Override
                        public void onClick(View view, Contact contact) {
                            Intent facebookPicketIntent = new Intent(TabActivity.this, FacebookPickerActivity.class);
                            facebookPicketIntent.putExtra(FacebookPickerActivity.EXTRA_ID, contact.getId());
                            startActivityForResult(facebookPicketIntent, REQUEST_FACEBOOK_PICKER);
                        }
                    });
                    recyclerView.setAdapter(contactsAdapter);
                    break;
                }
                case 1: {
                    RealmResults<Contact> manualContacts = mRealmUi.where(Contact.class)
                            .equalTo("mManual", true)
                            .findAllSorted("mName", Sort.ASCENDING);

                    ContactsAdapter<Contact> contactsAdapter = new ContactsAdapter<>(TabActivity.this, manualContacts, true);
                    contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                        @Override
                        public void onClick(View view, Contact contact) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(TabActivity.this);
                            builder.setTitle(R.string.alert_release_bond_title);
                            builder.setMessage(R.string.alert_release_bond_message);
                            //TODO: make another dialog/preference remembering if app should remove photo automatically
                            builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                mRealmUi.executeTransaction(realm -> {
                                    contact.getRelated().setSynced(false);
                                    contact.setRelated(null);
                                    contact.setManual(false);
                                    contact.setSynced(false);
                                });
                                dialogInterface.dismiss();
                            });
                            builder.setNegativeButton(android.R.string.cancel, ((dialogInterface, i) -> dialogInterface.dismiss()));
                            builder.create().show();
                        }
                    });
                    recyclerView.setAdapter(contactsAdapter);
                    break;
                }
            }
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View)object);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.activity_tab_tab_not_synced);
                case 1:
                    return getString(R.string.activity_tab_tab_manual);
                default:
                    return super.getPageTitle(position);
            }
        }
    }
}
