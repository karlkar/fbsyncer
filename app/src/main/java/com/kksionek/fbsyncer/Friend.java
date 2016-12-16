package com.kksionek.fbsyncer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class Friend extends RealmObject implements Comparable<Friend> {
    @PrimaryKey
    private String mId;
    private String mName;
    private String mPhoto;
    private boolean mFacebook;

    private Friend mRelated;
    private boolean mSynced;
    private boolean mOld;

    public Friend() {
        mId = null;
        mName = null;
        mPhoto = null;
        mFacebook = false;
        mRelated = null;
        mSynced = false;
        mOld = false;
    }

    public Friend(String id, String name) {
        this(id, name, null);
    }

    public Friend(@NonNull String id, @NonNull String name, @Nullable String photo) {
        mId = id;
        mName = name;
        mPhoto = photo;
        mFacebook = false;
        mRelated = null;
        mSynced = false;
        mOld = false;
    }

    public Friend(JSONObject jsonObject) {
        mFacebook = true;
        mRelated = null;
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
    }

    @Override
    public int compareTo(Friend another) {
        return mName.compareTo(another.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Friend))
            return false;
        return mName.equals(((Friend) o).getName());
    }

    public String getName() {
        return mName;
    }

    public String getPhoto() {
        return mPhoto;
    }

    public String getId() {
        return mId;
    }

    public void setSynced(boolean synced) {
        mSynced = synced;
    }

    public void setOld(boolean old) {
        mOld = old;
    }

    public void setRelated(@Nullable Friend related) {
        mRelated = related;
    }

    @Nullable
    public Friend getRelated() {
        return mRelated;
    }
}
