package com.kksionek.photosyncer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import io.realm.Realm
import io.realm.RealmConfiguration

@HiltAndroidApp
class SyncApplication : Application() {

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
