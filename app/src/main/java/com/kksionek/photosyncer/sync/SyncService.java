package com.kksionek.photosyncer.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class SyncService extends Service {

    private static final String TAG = "SyncService";

    private SyncAdapter mSyncAdapter = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");
        mSyncAdapter = new SyncAdapter(getApplicationContext(), true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mSyncAdapter.getSyncAdapterBinder();
    }

}