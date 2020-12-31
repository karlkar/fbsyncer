package com.kksionek.photosyncer.repository

import androidx.annotation.CheckResult
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity
import io.reactivex.Completable
import io.reactivex.Single

interface ContactsRepository {

    @CheckResult
    fun getContacts(): Single<List<ContactEntity>>

    @CheckResult
    fun bindFriend(contactId: Long, friendId: Long): Completable

    @CheckResult
    fun getAllContactsAndFriends(): Single<List<ContactAndFriend>>

    @CheckResult
    fun setSynced(contactId: Long, synced: Boolean, manual: Boolean): Completable
}