package com.kksionek.photosyncer.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface ContactDao {

    @Insert
    fun insertAll(vararg contactEntities: ContactEntity): Completable

    @Query("SELECT * FROM ContactEntity WHERE id = :contactId")
    fun getContact(contactId: Int): Single<ContactEntity>

    @Transaction
    @Query("SELECT * FROM ContactEntity")
    fun getAllContacts(): Single<List<ContactAndFriend>>

    // TODO: isManual should not matter - is it needed?
    @Query("SELECT * FROM ContactEntity WHERE relatedFriend IS NULL AND isManual = 'false' ORDER BY name ASC")
    fun getNotSyncedContacts(): Single<List<ContactEntity>>

    @Query("SELECT * FROM ContactEntity WHERE relatedFriend IS NOT NULL AND isManual = 'false' ORDER BY name ASC")
    fun getAutoSyncedContacts(): Single<List<ContactEntity>>

    @Query("SELECT * FROM ContactEntity WHERE isManual = 'true' ORDER BY name ASC")
    fun getManuallySyncedContacts(): Single<List<ContactEntity>>

    @Query("UPDATE ContactEntity SET relatedFriend = NULL, isManual = 'true', synced = 'true' WHERE id = :id")
    fun cancelAutoSync(id: Int): Completable

    @Query("UPDATE ContactEntity SET relatedFriend = NULL, isManual = 'false', synced = 'false' WHERE id = :id")
    fun releaseBond(id: Int): Completable

    @Query("UPDATE ContactEntity SET relatedFriend = :id, synced = 'true', isManual = 'true' WHERE id = :contactId")
    fun bindFriend(contactId: Int, id: Int): Completable
}