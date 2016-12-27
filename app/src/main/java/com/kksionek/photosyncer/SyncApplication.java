package com.kksionek.photosyncer;

import android.app.Application;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class SyncApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FacebookSdk.sdkInitialize(this);
        AppEventsLogger.activateApp(this);
        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name("photosync.realm")
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
    }
}
