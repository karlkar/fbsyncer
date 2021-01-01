package com.kksionek.photosyncer.sync

import androidx.lifecycle.LiveData

interface WorkManagerController {

    val isSyncRunning: LiveData<Boolean>

    fun runSync()

    fun scheduleSync()
}