package com.kksionek.photosyncer.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.view.PagerAdapter;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.data.Contact;
import com.kksionek.photosyncer.model.ContactsAdapter;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

class ViewPagerAdapter extends PagerAdapter {

    private static final int TAB_COUNT = 3;

    private Activity mParentActivity;
    private Realm mRealmUi;

    public ViewPagerAdapter(Activity parentActivity, Realm realmUi) {
        mParentActivity = parentActivity;
        mRealmUi = realmUi;
    }

    @Override
    public int getCount() {
        return TAB_COUNT;
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
                        mParentActivity,
                        DividerItemDecoration.VERTICAL));

        ContactsAdapter<Contact> contactsAdapter = null;

        switch (position) {
            case 0: {
                RealmResults<Contact> notSyncedContacts = mRealmUi.where(Contact.class)
                        .isNull("mRelated")
                        .equalTo("mManual", false)
                        .findAllSorted("mName", Sort.ASCENDING);

                contactsAdapter = new ContactsAdapter<>(mParentActivity, notSyncedContacts, true);
                contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                    @Override
                    public void onClick(View view, Contact contact) {
                        Intent facebookPicketIntent = new Intent(mParentActivity, FacebookPickerActivity.class);
                        facebookPicketIntent.putExtra(FacebookPickerActivity.EXTRA_ID, contact.getId());
                        mParentActivity.startActivityForResult(facebookPicketIntent, TabActivity.REQUEST_FACEBOOK_PICKER);
                    }
                });
                break;
            }
            case 1: {
                RealmResults<Contact> autoSyncedContacts = mRealmUi.where(Contact.class)
                        .isNotNull("mRelated")
                        .equalTo("mManual", false)
                        .findAllSorted("mName", Sort.ASCENDING);

                contactsAdapter = new ContactsAdapter<>(mParentActivity, autoSyncedContacts, true);
                    contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                        @Override
                        public void onClick(View view, Contact contact) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
                            builder.setTitle(R.string.alert_cancel_auto_sync_title);
                            builder.setMessage(R.string.alert_cancel_auto_sync_message);
                            builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                                mRealmUi.executeTransaction(realm -> {
                                    contact.setRelated(null);
                                    contact.setManual(true);
                                    contact.setSynced(true);
                                });
                                dialogInterface.dismiss();
                            });
                            builder.setNegativeButton(android.R.string.cancel, ((dialogInterface, i) -> dialogInterface.dismiss()));
                            builder.create().show();
                        }
                    });
                break;
            }
            case 2: {
                RealmResults<Contact> manualContacts = mRealmUi.where(Contact.class)
                        .equalTo("mManual", true)
                        .findAllSorted("mName", Sort.ASCENDING);

                contactsAdapter = new ContactsAdapter<>(mParentActivity, manualContacts, true);
                contactsAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Contact>() {
                    @Override
                    public void onClick(View view, Contact contact) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity);
                        builder.setTitle(R.string.alert_release_bond_title);
                        builder.setMessage(R.string.alert_release_bond_message);
                        //TODO: make another dialog/preference remembering if app should remove photo automatically
                        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                            mRealmUi.executeTransaction(realm -> {
                                if (contact.getRelated() != null)
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
                break;
            }
        }
        if (contactsAdapter != null)
            recyclerView.setAdapter(contactsAdapter);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return mParentActivity.getString(R.string.activity_tab_tab_not_synced);
            case 1:
                return mParentActivity.getString(R.string.activity_tab_tab_auto);
            case 2:
                return mParentActivity.getString(R.string.activity_tab_tab_manual);
            default:
                return super.getPageTitle(position);
        }
    }
}
