package com.kksionek.photosyncer.viewmodel

import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.SingleLiveEvent
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.repository.ContactDao
import com.kksionek.photosyncer.sync.WorkManagerController
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.time.Instant
import java.util.concurrent.TimeUnit

class TabViewModel @ViewModelInject constructor(
    private val contactDao: ContactDao,
    private val workManagerController: WorkManagerController,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    companion object {
        const val PREF_LAST_AD = "LAST_AD"
        const val INTENT_EXTRA_AD = "INTENT_AD"
    }

    private val disposables = CompositeDisposable()

    private var selectedTab: Int = 0

    private val _data = MutableLiveData<List<ContactEntity>>()
    val data: LiveData<List<ContactEntity>> = _data

    val isSyncRunning: LiveData<Boolean> = workManagerController.isSyncRunning

    private val _launchPickAFriend = SingleLiveEvent<Long>()
    val launchPickAFriend: LiveData<Long?> = _launchPickAFriend

    private val _showCancelAutoSyncDialog = SingleLiveEvent<Long>()
    val showCancelAutoSyncDialog: LiveData<Long?> = _showCancelAutoSyncDialog

    private val _showReleaseManualBondConfirmation = SingleLiveEvent<Long>()
    val showReleaseManualBondConfirmation: LiveData<Long?> = _showReleaseManualBondConfirmation

    private val _showAd = SingleLiveEvent<Unit>()
    val showAd: LiveData<Unit?> = _showAd

    fun getTabs(): List<Int> {
        return listOf(
            R.string.activity_tab_tab_not_synced,
            R.string.activity_tab_tab_auto,
            R.string.activity_tab_tab_manual
        )
    }

    // TODO: Probably those should not be Single's but Flowable's...
    fun setSelectedTab(position: Int) {
        selectedTab = position
        val observable = when (position) {
            0 -> contactDao.getNotSyncedContacts()
            1 -> contactDao.getAutoSyncedContacts()
            2 -> contactDao.getManuallySyncedContacts()
            else -> throw IllegalArgumentException("Position should not be greater than 2")
        }

        disposables.add(observable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { contacts: List<ContactEntity> ->
                _data.value = contacts
            }
        )
    }

    fun runSync() {
        workManagerController.runSync()
    }

    fun scheduleSync() {
        workManagerController.scheduleSync()
    }

    fun onItemClicked(contactEntity: ContactEntity) {
        when (selectedTab) {
            0 -> _launchPickAFriend.value = contactEntity.id
            1 -> _showCancelAutoSyncDialog.value = contactEntity.id
            2 -> _showReleaseManualBondConfirmation.value = contactEntity.id
        }
    }

    fun checkAd(intent: Intent) {
        val lastAdTime = sharedPreferences.getLong(PREF_LAST_AD, 0).let {
            if (it == 0L) {
                val lastAdTime = Instant.now().toEpochMilli()
                sharedPreferences.edit {
                    putLong(PREF_LAST_AD, lastAdTime)
                }
                lastAdTime
            } else {
                it
            }
        }

        val diff = Instant.now().toEpochMilli() - lastAdTime
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        if (days >= 6 || intent.getBooleanExtra(INTENT_EXTRA_AD, false)) {
            _showAd.call()
        }
    }

    fun onAdLoaded() {
        sharedPreferences.edit {
            putLong(PREF_LAST_AD, Instant.now().toEpochMilli())
        }
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }

    fun cancelAutoSync(contactEntityId: Long) {
        disposables.add(contactDao.cancelAutoSync(contactEntityId).subscribe())
    }

    fun releaseBond(contactEntityId: Long) {
        disposables.add(contactDao.releaseBond(contactEntityId).subscribe())
    }
}