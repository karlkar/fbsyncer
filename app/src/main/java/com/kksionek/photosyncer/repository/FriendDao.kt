package com.kksionek.photosyncer.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kksionek.photosyncer.model.FriendEntity
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface FriendDao {

    @Insert
    fun insertAll(vararg friendEntities: FriendEntity): Completable

    @Query("SELECT * FROM FriendEntity ORDER BY name")
    fun getAllFriends(): Single<List<FriendEntity>>

    @Query("SELECT * FROM FriendEntity WHERE name = :name ORDER BY name")
    fun getFriendsWithName(name: String): Single<List<FriendEntity>>

    @Delete
    fun removeAllSync(vararg friendEntity: FriendEntity)
}