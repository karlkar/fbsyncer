package com.kksionek.photosyncer.model

import androidx.room.Embedded
import androidx.room.Relation

data class ContactAndFriend(
    @Embedded val contactEntity: ContactEntity,
    @Relation(
        parentColumn = "relatedFriend",
        entityColumn = "friendId"
    )
    val friendEntity: FriendEntity
)
