package com.kksionek.photosyncer.sync

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.*
import com.kksionek.photosyncer.map
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerControllerImpl @Inject constructor(
    @ApplicationContext appContext: Context
) : WorkManagerController {

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "SyncWorkUniquePeriodicIdentifier"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "SyncWorkUniqueOneTimeIdentifier"
        private const val SYNC_WORK_TAG = "SyncWorkTag"
    }

    private val workManager: WorkManager = WorkManager.getInstance(appContext)

    override val isSyncRunning: LiveData<Boolean> =
        workManager.getWorkInfosByTagLiveData(SYNC_WORK_TAG)
            .map { workInfoList -> workInfoList.any { it.state == WorkInfo.State.RUNNING } }

    override fun runSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(SYNC_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(0, TimeUnit.SECONDS)
            .addTag(SYNC_WORK_TAG)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}