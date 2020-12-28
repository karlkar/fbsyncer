package com.kksionek.photosyncer.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity

@Dao
interface ContactDao {

    @Transaction
    @Query("SELECT * FROM ContactEntity")
    fun getAllContacts(): List<ContactAndFriend>

    @Insert
    fun insertAll(vararg contactEntities: ContactEntity)
}