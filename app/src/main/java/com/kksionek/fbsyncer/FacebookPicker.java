package com.kksionek.fbsyncer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class FacebookPicker extends AppCompatActivity {

    public static final String EXTRA_ID = "ID";
    public static final String EXTRA_RESULT_ID = "resultID";

    private String mContactId;

    private TextView mTextView;
    private RecyclerView mRecyclerView;
    private ContactsAdapter mAdapter;
    private Realm mRealm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fb_picker);
        mContactId = getIntent().getStringExtra(EXTRA_ID);

        mTextView = (TextView) findViewById(R.id.infoText);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        mRealm = Realm.getDefaultInstance();
        Contact contact = mRealm.where(Contact.class)
                .equalTo("mId", mContactId)
                .findFirst();

        mTextView.setText(getString(R.string.activity_facebookpicker_text, contact.getName()));

        RealmResults<Friend> notSyncedFriends = mRealm.where(Friend.class)
                .equalTo("mSynced", false)
                .findAllSorted("mName", Sort.ASCENDING);
        mAdapter = new ContactsAdapter(this, notSyncedFriends, false);
        mAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Friend>() {
            @Override
            public void onClick(View view, Friend friend) {
                AlertDialog.Builder builder = new AlertDialog.Builder(FacebookPicker.this);
                builder.setTitle(R.string.alert_create_bond_title);
                builder.setMessage(getString(R.string.alert_create_bond_message, contact.getName(), friend.getName()));
                builder.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    builder.create().show();
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_ID, mContactId);
                    intent.putExtra(EXTRA_RESULT_ID, friend.getId());
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                });
                builder.setNegativeButton(android.R.string.no, (dialogInterface, i) -> dialogInterface.dismiss());
                builder.create().show();
            }
        });
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }
}
