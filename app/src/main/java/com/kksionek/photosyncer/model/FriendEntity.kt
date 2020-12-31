package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FriendEntity(
    @PrimaryKey override val id: Long,
    override val name: String,
    override val photo: String
): Person