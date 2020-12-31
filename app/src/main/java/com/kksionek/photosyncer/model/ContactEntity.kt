package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ContactEntity(
    @PrimaryKey override val id: Long,
    override val name: String,
    override val photo: String,
    val relatedFriend: Long? = null,
    val synced: Boolean = false,
    val isManual: Boolean = false
) : Person