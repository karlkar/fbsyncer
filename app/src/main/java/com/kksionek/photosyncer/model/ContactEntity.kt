package com.kksionek.photosyncer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kksionek.photosyncer.data.Person

@Entity
data class ContactEntity(
    @PrimaryKey override val id: Int,
    override val name: String,
    override val photo: String,
    val relatedFriend: Int? = null,
    val synced: Boolean = false,
    val old: Boolean = false,
    val isManual: Boolean
) : Person