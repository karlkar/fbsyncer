package com.kksionek.photosyncer.repository

import androidx.room.Dao
import androidx.room.Insert
import com.kksionek.photosyncer.model.FriendEntity

@Dao
interface FriendDao {

    @Insert
    fun insertAll(vararg friendEntities: FriendEntity)
}