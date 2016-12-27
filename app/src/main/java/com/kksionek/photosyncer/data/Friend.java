package com.kksionek.photosyncer.data;

import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;

public class Friend extends RealmObject implements Person, Comparable<Person> {
    @PrimaryKey
    private String mGeneratedId;
    private String mId;
    private String mName;
    private String mPhoto;

    private boolean mSynced;
    private boolean mOld;

    public Friend() {
        mId = null;
        mName = null;
        mPhoto = null;
        mSynced = false;
        mOld = false;
    }

    public Friend(JSONObject jsonObject) {
        mSynced = false;
        mOld = false;
        try {
            mId = jsonObject.getString("id");
            mName = jsonObject.getString("name");
            mPhoto = jsonObject.getJSONObject("picture").getJSONObject("data").getString("url");
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
            mId = "";
            mPhoto = "";
            mName = "";
        }
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Friend> friendsWithTheSameName = realm.where(Friend.class)
                .equalTo("mName", mName)
                .equalTo("mOld", false)
                .findAll();
        mGeneratedId = Integer.toString((mName + friendsWithTheSameName.size()).hashCode());
        realm.close();
    }

    @Override
    public int compareTo(@NonNull Person another) {
        return mName.compareTo(another.getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Person && mName.equals(((Person) o).getName());
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public String getPhoto() {
        return mPhoto;
    }

    public String getId() {
        return mGeneratedId;
    }

    public void setSynced(boolean synced) {
        mSynced = synced;
    }

    public void setOld(boolean old) {
        mOld = old;
    }
}
