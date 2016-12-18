package com.kksionek.fbsyncer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class TabActivity extends AppCompatActivity implements ISyncListener {

    private static final int REQUEST_FACEBOOK_PICKER = 4445;

    private FBSyncService mService;
    private Realm mRealm;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            FBSyncService.MyLocalBinder binder = (FBSyncService.MyLocalBinder) iBinder;
            mService = binder.getService();
            mService.setListener(TabActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };
    private ViewPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab);

        mRealm = Realm.getDefaultInstance();

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FACEBOOK_PICKER) {
            if (resultCode == RESULT_OK) {
                String contactId = data.getStringExtra(FacebookPicker.EXTRA_ID);
                Contact contact = mRealm.where(Contact.class)
                        .equalTo("mId", contactId)
                        .findFirst();
                String friendId = data.getStringExtra(FacebookPicker.EXTRA_RESULT_ID);
                Friend friend = mRealm.where(Friend.class)
                        .equalTo("mGeneratedId", friendId)
                        .findFirst();
                RealmResults<Friend> sameNameFriends = mRealm.where(Friend.class)
                        .equalTo("mName", friend.getName())
                        .findAll();

                mRealm.executeTransaction(realm -> {
                    contact.setRelated(friend);
                    contact.setSynced(true);
                    contact.getRelated().setSynced(true);
                    contact.setManual(true);
                });
                if (mService != null)
                    mService.syncSingle(contact.getId(), contact.getRelated().getPhoto());

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
        Snackbar.make(mPager, R.string.activity_tab_sync_started, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSyncEnded() {
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
                    RealmResults<Contact> notSyncedContacts = mRealm.where(Contact.class)
                            .equalTo("mSynced", false)
                            .findAllSorted("mName", Sort.ASCENDING);

                    ContactsAdapter<Contact> contactsAdapter = new ContactsAdapter<>(TabActivity.this, notSyncedContacts, true);
                    contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                        @Override
                        public void onClick(View view, Contact contact) {
                            Intent facebookPicketIntent = new Intent(TabActivity.this, FacebookPicker.class);
                            facebookPicketIntent.putExtra(FacebookPicker.EXTRA_ID, contact.getId());
                            startActivityForResult(facebookPicketIntent, REQUEST_FACEBOOK_PICKER);
                        }
                    });
                    recyclerView.setAdapter(contactsAdapter);
                    break;
                }
                case 1: {
                    RealmResults<Contact> manualContacts = mRealm.where(Contact.class)
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
                                mRealm.executeTransaction(realm -> {
                                    contact.setRelated(null);
                                    contact.setManual(false);
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
