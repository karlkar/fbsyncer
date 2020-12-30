package com.kksionek.photosyncer.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.repository.ContactDao
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class TabViewModel @ViewModelInject constructor(
    private val contactDao: ContactDao
) : ViewModel() {

    private val disposables = CompositeDisposable()

    private val _data = MutableLiveData<List<ContactEntity>>()
    val data: LiveData<List<ContactEntity>> = _data

    fun getTabs(): List<Int> {
        return listOf(
            R.string.activity_tab_tab_not_synced,
            R.string.activity_tab_tab_auto,
            R.string.activity_tab_tab_manual
        )
    }

    // TODO: Probably those should not be Single's but Flowable's...
    fun setSelectedTab(position: Int) {
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

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }

    fun cancelAutoSync(contactEntity: ContactEntity) {
        disposables.add(contactDao.cancelAutoSync(contactEntity.id).subscribe())
    }

    fun releaseBond(contactEntity: ContactEntity) {
        disposables.add(contactDao.releaseBond(contactEntity.id).subscribe())
    }
}