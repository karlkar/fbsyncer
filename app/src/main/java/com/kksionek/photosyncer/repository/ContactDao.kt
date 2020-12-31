package com.kksionek.photosyncer.repository

import androidx.room.*
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSync(vararg contactEntities: ContactEntity)

    @Delete
    fun removeAllSync(vararg contactEntities: ContactEntity)

    @Query("SELECT * FROM ContactEntity WHERE id = :contactId")
    fun getContact(contactId: Int): Single<ContactEntity>

    @Query("SELECT * FROM ContactEntity")
    fun getAllContacts(): Single<List<ContactEntity>>

    @Transaction
    @Query("SELECT * FROM ContactEntity")
    fun getAllContactsAndFriends(): Single<List<ContactAndFriend>>

    // TODO: isManual should not matter - is it needed?
    @Query("SELECT * FROM ContactEntity WHERE relatedFriend IS NULL AND isManual = 'false' ORDER BY name ASC")
    fun getNotSyncedContacts(): Single<List<ContactEntity>>

    @Query("SELECT * FROM ContactEntity WHERE relatedFriend IS NOT NULL AND isManual = 'false' ORDER BY name ASC")
    fun getAutoSyncedContacts(): Single<List<ContactEntity>>

    @Query("SELECT * FROM ContactEntity WHERE isManual = 'true' ORDER BY name ASC")
    fun getManuallySyncedContacts(): Single<List<ContactEntity>>

    @Query("UPDATE ContactEntity SET relatedFriend = NULL, isManual = 'true', synced = 'true' WHERE id = :id")
    fun cancelAutoSync(id: Long): Completable

    @Query("UPDATE ContactEntity SET relatedFriend = NULL, isManual = 'false', synced = 'false' WHERE id = :id")
    fun releaseBond(id: Long): Completable

    @Query("UPDATE ContactEntity SET relatedFriend = :id, synced = 'true', isManual = 'true' WHERE id = :contactId")
    fun bindFriend(contactId: Long, id: Long): Completable

    @Query("UPDATE ContactEntity SET synced = :synced, isManual = :manual WHERE id = :contactId")
    fun setSynced(contactId: Long, synced: Boolean, manual: Boolean): Completable
}