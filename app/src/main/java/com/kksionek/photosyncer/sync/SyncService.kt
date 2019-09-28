package com.kksionek.photosyncer.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class SyncService : Service() {

    private lateinit var syncAdapter: SyncAdapter

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        syncAdapter = SyncAdapter(applicationContext, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? = syncAdapter.syncAdapterBinder

    companion object {

        private val TAG = "SyncService"
    }

}