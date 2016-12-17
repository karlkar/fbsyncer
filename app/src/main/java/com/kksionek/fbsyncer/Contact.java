package com.kksionek.fbsyncer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class Contact extends RealmObject implements Person, Comparable<Person> {
    @PrimaryKey
    private String mId;
    private String mName;
    private String mPhoto;

    private Friend mRelated;
    private boolean mSynced;
    private boolean mOld;

    public Contact() {
        mId = null;
        mName = null;
        mPhoto = null;
        mRelated = null;
        mSynced = false;
        mOld = false;
    }

    public Contact(String id, String name) {
        this(id, name, null);
    }

    public Contact(@NonNull String id, @NonNull String name, @Nullable String photo) {
        mId = id;
        mName = name;
        mPhoto = photo;
        mRelated = null;
        mSynced = false;
        mOld = false;
    }

    @Override
    public int compareTo(Person another) {
        return mName.compareTo(another.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Person))
            return false;
        return mName.equals(((Person) o).getName());
    }

    public void setOld(boolean old) {
        mOld = old;
    }

    public Friend getRelated() {
        return mRelated;
    }

    public void setRelated(Friend related) {
        mRelated = related;
    }

    public void setSynced(boolean synced) {
        mSynced = synced;
    }

    public String getName() {
        return mName;
    }

    public String getId() {
        return mId;
    }

    public String getPhoto() {
        return mPhoto;
    }
}
