package com.kksionek.photosyncer.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import com.kksionek.photosyncer.data.Contact;
import com.kksionek.photosyncer.model.ContactsAdapter;
import com.kksionek.photosyncer.data.Friend;
import com.kksionek.photosyncer.R;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class FacebookPickerActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "ID";
    public static final String EXTRA_RESULT_ID = "resultID";

    private String mContactId;

    private Realm mRealm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fb_picker);
        mContactId = getIntent().getStringExtra(EXTRA_ID);

        TextView textView = (TextView) findViewById(R.id.infoTextName);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        mRealm = Realm.getDefaultInstance();
        Contact contact = mRealm.where(Contact.class)
                .equalTo("mId", mContactId)
                .findFirst();

        textView.setText(contact.getName());

        RealmResults<Friend> notSyncedFriends = mRealm.where(Friend.class)
                .findAllSorted("mName", Sort.ASCENDING);
        ContactsAdapter<Friend> adapter = new ContactsAdapter<>(this, notSyncedFriends, false);
        adapter.setOnItemClickListener((view, friend) -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(FacebookPickerActivity.this);
            builder.setTitle(R.string.alert_create_bond_title);
            builder.setMessage(getString(R.string.alert_create_bond_message, contact.getName(), friend.getName()));
            builder.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_ID, mContactId);
                intent.putExtra(EXTRA_RESULT_ID, friend.getId());
                setResult(Activity.RESULT_OK, intent);
                finish();
            });
            builder.setNegativeButton(android.R.string.no, (dialogInterface, i) -> dialogInterface.dismiss());
            builder.create().show();
        });
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onDestroy() {
        mRealm.close();
        super.onDestroy();
    }
}
