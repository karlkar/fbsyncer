package com.kksionek.photosyncer.repository

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kksionek.photosyncer.gateway.FacebookEndpoint
import com.kksionek.photosyncer.model.ConsentBody
import com.kksionek.photosyncer.model.FriendEntity
import com.kksionek.photosyncer.model.LoginData
import com.kksionek.photosyncer.repository.SecureStorage.Companion.PREF_LOGIN
import com.kksionek.photosyncer.repository.SecureStorage.Companion.PREF_PASSWORD
import io.reactivex.Observable
import io.reactivex.Single
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class FriendRepositoryImpl @Inject constructor(
    private val secureStorage: SecureStorage,
    private val firebaseCrashlytics: FirebaseCrashlytics,
    private val friendDao: FriendDao,
    private val facebookEndpoint: FacebookEndpoint
) : FriendRepository {

    companion object {
        private const val TAG = "FriendRepositoryImpl"

        private val UID_PATTERN = Pattern.compile("\\?uid=(.*?)&")
        private val DTSG_PATTERN = "\\{\"dtsg\":\\{\"token\":\"(.*?)\",".toRegex()
        private val A_PATTERN = "\",\"encrypted\":\"(.*?)\"\\}\\}".toRegex()
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
            var textResponse: String = resp
            var ppk = 0
            val uids = mutableListOf<Long>()
            do {
                val matcher = UID_PATTERN.matcher(textResponse)
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
                textResponse = facebookEndpoint.getFriendList(ppk, ppk).blockingGet()
            } while (textResponse.contains("?uid="))
            Log.d(TAG, "obtainFriends: Found ${uids.size} friends.")
            uids
        }
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMap { getRxFriend(it).toObservable() }
            .toList()
    }

    private fun getRxFriend(uid: Long): Single<FriendEntity> {
        return facebookEndpoint.getFriendData(uid)
            .map { responseStr ->
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
            }.toSingle()
    }

    override fun fbLogin(): Single<String> {
        val login = secureStorage.read(PREF_LOGIN)
        val pass = secureStorage.read(PREF_PASSWORD)
        if (login.isNullOrEmpty() && pass.isNullOrEmpty()) {
            return Single.error(Exception("Login and/or password not set"))
        }
        return facebookEndpoint.loadLoginForm()
            .map { loginFormPage ->
                val doc = Jsoup.parse(loginFormPage)
                val lsd = doc.select("input[name=lsd]").first().`val`()
                val mTs = doc.select("input[name=m_ts]").first().`val`()
                val li = doc.select("input[name=li]").first().`val`()
                val jazoest = doc.select("input[name=jazoest]").first().`val`()
                val fbDtsg = DTSG_PATTERN.find(loginFormPage)?.groupValues?.get(1).orEmpty()
                val a = A_PATTERN.find(loginFormPage)?.groupValues?.get(1).orEmpty()

                val loginEncoded = URLEncoder.encode(login, "utf-8")
                val passEncoded = URLEncoder.encode(pass, "utf-8")
                LoginFormData(
                    loginEncoded,
                    passEncoded,
                    lsd,
                    mTs,
                    li,
                    jazoest,
                    fbDtsg,
                    a
                )
            }
            .flatMap {
                facebookEndpoint.consent(ConsentBody(it.dtsg, it.jazoest, it.lsd, it.a))
                    .andThen(Single.just(it))
            }
            .flatMap { facebookEndpoint.login(it.toLoginData()) }
            .flatMap { loginResponse ->
                if (loginResponse.contains("a problem with this request.")) {
                    Log.e(TAG, "fbLogin: Problem with this request")
                    secureStorage.clear()
                    return@flatMap Single.error(Exception("Wrong login and/or password"))
                }
                if (loginResponse.contains("login_form")) {
                    Log.e(TAG, "fbLogin: Wrong login/password")
                    secureStorage.clear()
                    return@flatMap Single.error(Exception("Wrong login and/or password"))
                }
                if (loginResponse.contains("Please try again later")) {
                    Log.e(TAG, "fbLogin: Too often")
                    return@flatMap Single.error(Exception("You are trying too often"))
                }
                Single.just(loginResponse)
            }
    }

    override fun getFriendsWithName(name: String): Single<List<FriendEntity>> =
        friendDao.getFriendsWithName(name)
}

data class LoginFormData(
    val login: String,
    val pass: String,
    val lsd: String,
    val mTs: String,
    val li: String,
    val jazoest: String,
    val dtsg: String,
    val a: String
)

fun LoginFormData.toLoginData() = LoginData(login, pass, lsd, mTs, li, jazoest)