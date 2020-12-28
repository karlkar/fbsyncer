package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FriendEntity(
    @PrimaryKey val friendId: Int,
    val name: String,
    val photo: String,
    val old: Boolean = false
)