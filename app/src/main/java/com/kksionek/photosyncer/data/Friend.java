package com.kksionek.photosyncer.data;

import androidx.annotation.NonNull;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class Friend extends RealmObject implements Person, Comparable<Person> {
    @PrimaryKey
    private String mId;
    private String mName;
    private String mPhoto;

    private boolean mOld;

    public Friend() {
        mId = null;
        mName = null;
        mPhoto = null;
        mOld = false;
    }

    public Friend(String uid, String name, String photo) {
        mId = uid;
        mName = name;
        mPhoto = photo;
        mOld = false;
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
        return mId;
    }

    public String getFacebookId() {
        return mId;
    }

    public void setOld(boolean old) {
        mOld = old;
    }
}
