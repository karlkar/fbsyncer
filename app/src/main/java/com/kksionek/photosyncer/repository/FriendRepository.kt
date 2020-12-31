package com.kksionek.photosyncer.repository

import androidx.annotation.CheckResult
import com.kksionek.photosyncer.model.FriendEntity
import io.reactivex.Single

interface FriendRepository {

    @CheckResult
    fun getFriends(): Single<List<FriendEntity>>

    @CheckResult
    fun fbLogin(): Single<String>

    @CheckResult
    fun getFriendsWithName(name: String): Single<List<FriendEntity>>
}