package com.kksionek.photosyncer;

import android.app.Application;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class SyncApplication extends Application {

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
