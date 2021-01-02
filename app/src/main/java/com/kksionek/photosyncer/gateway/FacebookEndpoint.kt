package com.kksionek.photosyncer.gateway

import com.kksionek.photosyncer.model.LoginData
import io.reactivex.Single
import retrofit2.http.*

interface FacebookEndpoint {

    @GET("/login.php?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&refsrc=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&_rdr")
    fun loadLoginForm(): Single<String>

    @Headers("Content-Type: application/x-www-form-urlencoded;charset=utf-8")
    @POST("/login/device-based/regular/login/?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F")
    fun login(@Body loginData: LoginData): Single<String>

    @GET("/friends/center/friends/?#friends_center_main")
    fun getFriendList(@Query("ppk") ppk: Int, @Query("bph") bph: Int): Single<String>

    @GET("/friends/hovercard/mbasic/?redirectURI=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F%3Frefid%3D9%26mfl_act%3D1%23last_acted")
    fun getFriendData(@Query("uid") uid: Long): Single<String>
}