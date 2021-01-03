package com.kksionek.photosyncer.gateway

import com.kksionek.photosyncer.model.ConsentBody
import com.kksionek.photosyncer.model.LoginData
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FacebookEndpoint {

    @GET("/login.php?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&refsrc=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&_rdr")
    fun loadLoginForm(): Single<String>

    @POST("/cookie/consent/")
    fun consent(@Body consentBody: ConsentBody): Completable

    @POST("/login/device-based/regular/login/?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F")
    fun login(@Body loginData: LoginData): Single<String>

    @GET("/friends/center/friends/?#friends_center_main")
    fun getFriendList(@Query("ppk") ppk: Int, @Query("bph") bph: Int): Single<String>

    @GET("/friends/hovercard/mbasic/?redirectURI=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F%3Frefid%3D9%26mfl_act%3D1%23last_acted")
    fun getFriendData(@Query("uid") uid: Long): Single<String>
}
