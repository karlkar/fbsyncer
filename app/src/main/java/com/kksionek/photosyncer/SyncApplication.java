package com.kksionek.photosyncer;

import androidx.multidex.MultiDexApplication;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class SyncApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name("photosync.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
    }
}
