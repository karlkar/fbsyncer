package com.kksionek.fbsyncer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
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

    private String mContactId;

    private TextView mTextView;
    private RecyclerView mRecyclerView;
    private ContactsAdapter mAdapter;
    private Realm mRealm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fb_picker);
        mContactId = getIntent().getStringExtra("ID");

        mTextView = (TextView) findViewById(R.id.infoText);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        mRealm = Realm.getDefaultInstance();
        Contact contact = mRealm.where(Contact.class)
                .equalTo("mId", mContactId)
                .findFirst();

        mTextView.setText("Pick facebook friend who will be connected to " + contact.getName() + " from your contacts");

        RealmResults<Friend> notSyncedFriends = mRealm.where(Friend.class)
                .equalTo("mSynced", false)
                .findAllSorted("mName", Sort.ASCENDING);
        mAdapter = new ContactsAdapter(this, notSyncedFriends, false);
        mAdapter.setOnItemClickListener(new ContactsAdapter.OnItemClickListener<Friend>() {
            @Override
            public void onClick(View view, Friend friend) {
                Intent intent = new Intent();
                intent.putExtra("ID", mContactId);
                intent.putExtra("resultID", friend.getId());
                setResult(Activity.RESULT_OK, intent);
                finish();
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
