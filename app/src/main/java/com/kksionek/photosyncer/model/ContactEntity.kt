package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ContactEntity(
    @PrimaryKey val contactId: Int,
    val name: String,
    val photo: String,
    val relatedFriend: Int? = null,
    var synced: Boolean = false,
    var old: Boolean = false,
    val isManual: Boolean
)