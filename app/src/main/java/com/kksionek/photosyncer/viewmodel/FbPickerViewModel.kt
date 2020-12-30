package com.kksionek.photosyncer.viewmodel

import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.SingleLiveEvent
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.model.FriendEntity
import com.kksionek.photosyncer.repository.ContactDao
import com.kksionek.photosyncer.repository.FriendDao
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class FbPickerViewModel @ViewModelInject constructor(
    private val contactDao: ContactDao,
    private val friendDao: FriendDao,
    @Assisted private val state: SavedStateHandle
) : ViewModel() {

    private val _contactEntity = MutableLiveData<ContactEntity>()
    val contactEntity: LiveData<ContactEntity> = _contactEntity

    private val _friendEntities = MutableLiveData<List<FriendEntity>>()
    val friendEntities: LiveData<List<FriendEntity>> = _friendEntities

    private val _bindingCompletedWithoutError = SingleLiveEvent<Boolean>()
    val bindingCompletedWithoutError: LiveData<Boolean?> = _bindingCompletedWithoutError

    private val disposables = CompositeDisposable()

    fun init() {
        disposables.add(contactDao.getContact(state.get<Int>("contactId")!!)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it: ContactEntity ->
                _contactEntity.value = it
            })

        disposables.add(friendDao.getAllFriends()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { it: List<FriendEntity> ->
                _friendEntities.value = it
            })
    }

    fun bindFriend(contactId: Int, friendEntity: FriendEntity) {
        disposables.add(
            contactDao.bindFriend(contactId, friendEntity.id)
                .andThen(friendDao.getFriendsWithName(friendEntity.name))
                .subscribe { it: List<FriendEntity> ->
                    _bindingCompletedWithoutError.value = it.size == 1
                }
        )
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }
}