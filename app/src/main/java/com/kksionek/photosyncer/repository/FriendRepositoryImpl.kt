package com.kksionek.photosyncer.repository

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kksionek.photosyncer.model.FriendEntity
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.exceptions.Exceptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class FriendRepositoryImpl @Inject constructor(
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient,
    private val firebaseCrashlytics: FirebaseCrashlytics,
    private val friendDao: FriendDao
) : FriendRepository {

    companion object {
        private const val TAG = "FriendRepositoryImpl"

        private val URLENCODED = "application/x-www-form-urlencoded".toMediaTypeOrNull()
        private val UID_PATTERN = Pattern.compile("\\?uid=(.*?)&")
    }

    override fun getFriends(): Single<List<FriendEntity>> {
        return Single.zip(
            obtainFriends(),
            friendDao.getAllFriends(),
            { fbFriends, roomFriends ->
                // Remove old contacts from room
                val fbFriendIds = fbFriends.map { it.id }
                val friendsToBeRemoved = roomFriends.filter { oldFriend ->
                    oldFriend.id !in fbFriendIds
                }
                friendDao.removeAllSync(*friendsToBeRemoved.toTypedArray())
                fbFriends
            })
    }

    private fun obtainFriends(): Single<List<FriendEntity>> {
        return fbLogin().map { resp ->
            var textResponse = ""
            var ppk = 0
            val uids = mutableListOf<Long>()
            do {
                val matcher = UID_PATTERN.matcher(resp)
                while (matcher.find()) {
                    matcher.group(1)?.let {
                        uids.add(it.toLong())
                    }
                }
                if (uids.isEmpty()) {
                    firebaseCrashlytics.log("[$ppk] No friend uids were found in `resp` = $resp")
                    firebaseCrashlytics.recordException(Exception("No friend uids were found in `resp`"))
                }

                ++ppk
                val req = Request.Builder()
                    .url("https://m.facebook.com/friends/center/friends/?ppk=$ppk&bph=$ppk#friends_center_main")
                    .build()
                try {
                    val response = okHttpClient.newCall(req).execute()
                    if (response.isSuccessful) {
                        textResponse = response.body!!.string()
                    }
                    response.close()
                } catch (e: IOException) {
                    throw Exceptions.propagate(e)
                }

            } while (textResponse.contains("?uid="))
            Log.d(TAG, "getRxFriends: Found ${uids.size} friends.")
            uids
        }
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMap { getRxFriend(it).toObservable() }
            .toList()
    }

    private fun getRxFriend(uid: Long): Single<FriendEntity> {
        return Single.fromCallable {
            val req = Request.Builder()
                .url("https://m.facebook.com/friends/hovercard/mbasic/?uid=$uid&redirectURI=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F%3Frefid%3D9%26mfl_act%3D1%23last_acted")
                .build()
            okHttpClient.newCall(req).execute()
        }.toObservable()
            .filter { it.isSuccessful }
            .map { response ->
                try {
                    val body = response.body!!.string()
                    response.close()
                    return@map body
                } catch (e: IOException) {
                    Log.e(TAG, "Failed", e)
                    throw Exceptions.propagate(e)
                }
            }.map { responseStr ->
                var profPicIdx = responseStr.indexOf("profpic img")
                if (profPicIdx == -1) {
                    profPicIdx = responseStr.indexOf("class=\"w p\"")
                }
                if (profPicIdx == -1) {
                    profPicIdx = responseStr.indexOf("class=\"x p\"")
                }
                if (profPicIdx == -1) {
                    profPicIdx = responseStr.indexOf("class=\"y q\"")
                }
                if (profPicIdx == -1) {
                    firebaseCrashlytics.log("Uid = '$uid'. Page content = $responseStr")
                    firebaseCrashlytics.recordException(Exception("Cannot find picture for friend"))
                    return@map ""
                }
                responseStr.substring(
                    max(0, profPicIdx - 400),
                    min(profPicIdx + 200, responseStr.length)
                )
            }
            .filter { url -> url.isNotEmpty() }
            .firstOrError()
            .map { responseStr ->
                var photoUrl: String? = null
                val p = Pattern.compile("src=\"(.+?_\\d\\d+?_.+?)\"")
                var m = p.matcher(responseStr)
                if (m.find()) {
                    photoUrl = m.group(1).replace("&amp;", "&")
                }

                var name: String? = null
                val p2 = Pattern.compile("alt=\"(.+?)\"")
                m = p2.matcher(responseStr)
                if (m.find()) {
                    name = StringEscapeUtils.unescapeHtml4(m.group(1))
                }
                FriendEntity(uid, name!!, photoUrl!!)
            }
    }

    override fun fbLogin(): Single<String> {
        return Single.create { singlePublisher ->
            val login = secureStorage.read("PREF_LOGIN")
            val pass = secureStorage.read("PREF_PASSWORD")
            if (login.isNullOrEmpty() && pass.isNullOrEmpty()) {
                singlePublisher.onError(Exception("Login and/or password not set"))
                return@create
            }

            var req = Request.Builder()
                .url("https://m.facebook.com/login.php?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&refsrc=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&_rdr")
                .build()

            var responseStr: String? = null
            try {
                val response = okHttpClient.newCall(req).execute()
                if (response.isSuccessful) {
                    responseStr = response.body!!.string()
                }
                response.close()
            } catch (e: IOException) {
                singlePublisher.onError(e)
                return@create
            }

            if (responseStr == null) {
                singlePublisher.onError(Exception("Response is null"))
                return@create
            }

            val doc = Jsoup.parse(responseStr)
            val lsd = doc.select("input[name=lsd]").first().`val`()
            val mTs = doc.select("input[name=m_ts]").first().`val`()
            val li = doc.select("input[name=li]").first().`val`()
            val jazoest = doc.select("input[name=jazoest]").first().`val`()

            val reqBody: RequestBody
            try {
                val loginEncoded = URLEncoder.encode(login, "utf-8")
                val passEncoded = URLEncoder.encode(pass, "utf-8")
                reqBody =
                    ("email=$loginEncoded&pass=$passEncoded&login=Log%20In&lsd=$lsd&m_ts=$mTs&li=$li&jazoest=$jazoest&try_number=0&unrecognized_tries=0&").toRequestBody(
                        URLENCODED
                    )
            } catch (e: UnsupportedEncodingException) {
                singlePublisher.onError(e)
                return@create
            }

            req = Request.Builder()
                .url("https://m.facebook.com/login.php?next=https://m.facebook.com/friends/center/friends/")
                .post(reqBody)
                .build()

            try {
                val response = okHttpClient.newCall(req).execute()
                if (response.isSuccessful) {
                    responseStr = response.body!!.string()
                }
                response.close()
            } catch (e: IOException) {
                singlePublisher.onError(e)
                return@create
            }

            if (responseStr.contains("login_form")) {
                Log.e(TAG, "fbLogin: Wrong login/password")
                secureStorage.clear()
                singlePublisher.onError(Exception("Wrong login and/or password"))
            }
            if (responseStr.contains("Please try again later")) {
                Log.e(TAG, "fbLogin: Too often")
                singlePublisher.onError(Exception("You are trying too often"))
            }
            singlePublisher.onSuccess(responseStr)
        }
    }

    override fun getFriendsWithName(name: String): Single<List<FriendEntity>> =
        friendDao.getFriendsWithName(name)
}