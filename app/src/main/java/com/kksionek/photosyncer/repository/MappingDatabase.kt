package com.kksionek.photosyncer.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.model.FriendEntity

@Database(
    entities = [
        ContactEntity::class,
        FriendEntity::class
    ],
    version = 1
)
abstract class MappingDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    abstract fun friendDao(): FriendDao
}