package com.kksionek.photosyncer

import androidx.multidex.MultiDexApplication

import io.realm.Realm
import io.realm.RealmConfiguration

class SyncApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Realm.init(this)
        val config = RealmConfiguration.Builder()
            .name("photosync.realm")
            .deleteRealmIfMigrationNeeded()
            .build()
        Realm.setDefaultConfiguration(config)
    }
}
