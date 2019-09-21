package com.kksionek.photosyncer.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private boolean mManual;

    public Contact() {
        mId = null;
        mName = null;
        mPhoto = null;
        mRelated = null;
        mSynced = false;
        mOld = false;
        mManual = false;
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
        mManual = false;
    }

    @Override
    public int compareTo(@NonNull Person another) {
        return mName.compareTo(another.getName());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Person && mName.equals(((Person) o).getName());
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

    @NonNull
    public String getName() {
        return mName;
    }

    public String getId() {
        return mId;
    }

    public String getPhoto() {
        return mPhoto;
    }

    public void setManual(boolean manual) {
        mManual = manual;
    }

    public void setName(String name) {
        mName = name;
    }

    public boolean isManual() {
        return mManual;
    }
}
