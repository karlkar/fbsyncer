package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kksionek.photosyncer.data.Person

@Entity
data class FriendEntity(
    @PrimaryKey override val id: Int,
    override val name: String,
    override val photo: String,
    val old: Boolean = false
): Person