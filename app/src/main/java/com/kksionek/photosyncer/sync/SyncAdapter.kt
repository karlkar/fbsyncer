package com.kksionek.photosyncer.sync

import android.accounts.Account
import android.app.NotificationChannel
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.data.Friend
import com.kksionek.photosyncer.model.RxContacts
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.view.TabFragment.Companion.PREF_LAST_AD
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.exceptions.Exceptions
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SyncAdapter @Inject constructor(
    @ApplicationContext appContext: Context,
    private val secureStorage: SecureStorage,
    private val firebaseCrashlytics: FirebaseCrashlytics,
    private val okHttpClient: OkHttpClient
) : AbstractThreadedSyncAdapter(appContext, true, false) {

    private val threadPool = Executors.newFixedThreadPool(2)

    private lateinit var notificationChannel: NotificationChannel

    private val contactsRx: Single<List<Contact>>
        get() = RxContacts.fetch(context)
            .subscribeOn(Schedulers.io())

    // Prepare Realm database so it knows which contacts were updated
    // Remove friends that doesn't exist anymore
    private val andRealmContacts: Single<List<Contact>>
        get() = contactsRx
            .doOnSuccess { contacts ->
                if (Looper.myLooper() == null) {
                    Looper.prepare()
                }
                Log.d(TAG, "getAndRealmContacts: Found " + contacts.size + " contacts")
                val ioRealm = Realm.getDefaultInstance()
                ioRealm.beginTransaction()
                ioRealm.where(Contact::class.java)
                    .findAll()
                    .forEach { it.old = true }

                var preUpdateContact: Contact?
                for (newContact in contacts) {
                    preUpdateContact = ioRealm.where(Contact::class.java)
                        .equalTo("id", newContact.getId())
                        .findFirst()
                    if (preUpdateContact != null) {
                        newContact.related = preUpdateContact.related
                        newContact.isManual = preUpdateContact.isManual
                    }
                }

                ioRealm.insertOrUpdate(contacts)
                ioRealm.where(Contact::class.java)
                    .equalTo("old", true)
                    .findAll()
                    .forEach { it.deleteFromRealm() }

                ioRealm.commitTransaction()
                ioRealm.close()
            }

    private val rxFriends: Single<List<Friend>>
        get() {
            return fbLogin(okHttpClient, secureStorage)
                .subscribeOn(Schedulers.io())
                .map { resp ->
                    var textResponse = ""
                    var ppk = 0
                    val uids = ArrayList<String>()
                    do {
                        val matcher = UID_PATTERN.matcher(resp)
                        while (matcher.find()) {
                            val group = matcher.group(1)
                            uids.add(group)
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
                    Log.d(TAG, "getRxFriends: Found " + uids.size + " friends.")
                    uids
                }
                .flatMapObservable { Observable.fromIterable(it) }
                .flatMap { getRxFriend(it).toObservable() }
                .toList()
        }

    // Prepare Realm database so it knows which friends were updated
    private val andRealmFriends: Single<List<Friend>>
        get() = rxFriends
            .doOnSuccess { friends ->
                if (Looper.myLooper() == null) {
                    Looper.prepare()
                }
                val ioRealm = Realm.getDefaultInstance()
                ioRealm.beginTransaction()
                ioRealm.where(Friend::class.java)
                    .findAll()
                    .asFlowable()
                    .subscribeOn(Schedulers.trampoline())
                    .flatMap { Flowable.fromIterable(it) }
                    .forEach { realmContact -> realmContact.old = true }

                friends.forEach {
                    ioRealm.insertOrUpdate(it)
                }

                ioRealm.where(Friend::class.java)
                    .equalTo("old", true)
                    .findAll().deleteAllFromRealm()
                ioRealm.commitTransaction()
                ioRealm.close()
            }

    override fun onPerformSync(
        account: Account,
        bundle: Bundle,
        s: String,
        contentProviderClient: ContentProviderClient,
        syncResult: SyncResult
    ) {
        Log.d(TAG, "onPerformSync: START")
        Single.zip<List<Contact>, List<Friend>, Int>(
            andRealmContacts,
            andRealmFriends,
            { _, _ -> 1 }
        )
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.trampoline())
            .subscribe(
                { performSync() },
                { throwable -> Log.e(TAG, "onPerformSync: Error", throwable) })
        showNotification()
        Log.d(TAG, "onPerformSync: END")
    }

    private fun showNotification() {
        var lastAd = PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_LAST_AD, 0)

        if (lastAd == 0L) {
            lastAd = System.currentTimeMillis()
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(PREF_LAST_AD, lastAd)
                .apply()
        }

        val diff = System.currentTimeMillis() - lastAd
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        if (days < 6) {
            return
        }

//        val intent = Intent(context, TabActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
//            putExtra("INTENT_AD", true)
//        }
//        val pendingIntent = PendingIntent.getActivity(
//            context,
//            1,
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT
//        )
//
//        val notificationManager: NotificationManager? = context.getSystemService()
//        val notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            if (::notificationChannel.isInitialized) {
//                notificationChannel = NotificationChannel(
//                    "syncDone",
//                    "Photo Syncer",
//                    NotificationManager.IMPORTANCE_HIGH
//                )
//                notificationChannel.enableLights(true)
//                notificationManager?.createNotificationChannel(notificationChannel)
//            }
//            notificationChannel.id
//        } else {
//            ""
//        }
//        val builder = NotificationCompat.Builder(
//            context,
//            notificationChannelId
//        )
//        builder.setSmallIcon(R.mipmap.ic_launcher)
//            .setContentTitle(context.getString(R.string.app_name))
//            .setContentText(context.getString(R.string.notification_synchronisation_done))
//            .setAutoCancel(true)
//            .setDefaults(Notification.DEFAULT_SOUND)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setStyle(
//                NotificationCompat.BigTextStyle()
//                    .bigText(context.getString(R.string.notification_synchronisation_done))
//            )
//            .setContentIntent(pendingIntent)
//            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//        notificationManager?.notify(0, builder.build())
    }

    private fun getRxFriend(uid: String): Single<Friend> {
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
                Friend(uid, name, photoUrl)
            }
    }

    private fun getBitmapFromURL(src: String): Bitmap? {
        try {
            val req = Request.Builder()
                .url(src)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val inputStream = resp.body!!.byteStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                resp.close()
                return bitmap
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    private fun performSync() {
        val callables = ArrayList<Callable<Unit>>()
        val realm = Realm.getDefaultInstance()
        val contacts = realm.where(Contact::class.java).findAll()
        //        RealmResults<Friend> friends = realm.where(Friend.class).findAll();

        realm.beginTransaction()

        // All contacts in database are new => they have synced set to false
        //        for (Contact contact : contacts)
        //            contact.setSynced(false);
        //        for (Friend friend : friends)
        //            friend.setSynced(false);

        for (contact in contacts) {
            if (contact.isManual) {
                if (contact.related != null) {
                    syncToRelated(callables, realm, contact, true)
                }
            } else {
                val sameNameFriends = realm.where(Friend::class.java)
                    .equalTo("mName", contact.getName())
                    .findAll()
                if (sameNameFriends.size == 1) {
                    contact.related = sameNameFriends.first()
                    syncToRelated(callables, realm, contact, false)
                } else {
                    if (sameNameFriends.isEmpty())
                        Log.d(
                            TAG,
                            "performSync: [" + contact.getName() + "] Friend doesn't exist on social network."
                        )
                    else
                        Log.d(
                            TAG,
                            "performSync: [" + contact.getName() + "] Friend exists multiple times and connot be synced automatically."
                        )
                    contact.synced = false
                }
            }
        }
        realm.commitTransaction()
        realm.close()

        try {
            threadPool.invokeAll(callables)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun syncToRelated(
        callables: MutableList<Callable<Unit>>,
        realm: Realm,
        contact: Contact,
        manual: Boolean
    ) {
        if (manual) {
            Log.d(
                TAG,
                "syncToRelated: [" + contact.getName() + "] - syncing using manual settings."
            )
        } else {
            Log.d(TAG, "syncToRelated: [" + contact.getName() + "] - syncing using auto settings.")
        }
        val copiedContact = realm.copyFromRealm(contact)
        callables.add(Callable {
            setContactPhoto(
                copiedContact.getId(),
                copiedContact.related!!.getPhoto()
            )
        })
        contact.synced = true
        contact.isManual = manual
    }

    private fun setContactPhoto(rawContactId: String, photo: String) {
        val bitmap = getBitmapFromURL(photo) ?: return

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val photoData = stream.toByteArray()
        val values = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
            put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData)
            put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            )
        }
        Log.d(TAG, "setContactPhoto: id = $rawContactId")

        var photoRow = -1
        val where =
            "${ContactsContract.Data.RAW_CONTACT_ID} = $rawContactId AND ${ContactsContract.Data.MIMETYPE} =='${ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE}'"
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.Data._ID),
            where, null, null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                photoRow = cursor.getInt(0)
            }
            cursor.close()
        }

        if (photoRow >= 0) {
            context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data._ID} = $photoRow",
                null
            )
        } else {
            Log.d(TAG, "setContactPhoto: INSERT $rawContactId")
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
        }
        try {
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {

        private const val TAG = "SYNCADAPTER"

        private val URLENCODED = "application/x-www-form-urlencoded".toMediaTypeOrNull()
        private val UID_PATTERN = Pattern.compile("\\?uid=(.*?)&")

        fun fbLogin(httpClient: OkHttpClient, secureStorage: SecureStorage): Single<String> {
            return Single.fromPublisher { singlePublisher ->
                val login = secureStorage.read("PREF_LOGIN")
                val pass = secureStorage.read("PREF_PASSWORD")
                if (login.isNullOrEmpty() && pass.isNullOrEmpty()) {
                    singlePublisher.onError(Exception("Login and/or password not set"))
                    return@fromPublisher
                }

                var req = Request.Builder()
                    .url("https://m.facebook.com/login.php?next=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&refsrc=https%3A%2F%2Fm.facebook.com%2Ffriends%2Fcenter%2Ffriends%2F&_rdr")
                    .build()

                var responseStr: String? = null
                try {
                    val response = httpClient.newCall(req).execute()
                    if (response.isSuccessful) {
                        responseStr = response.body!!.string()
                    }
                    response.close()
                } catch (e: IOException) {
                    singlePublisher.onError(e)
                    return@fromPublisher
                }

                if (responseStr == null) {
                    singlePublisher.onError(Exception("Response is null"))
                    return@fromPublisher
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
                    return@fromPublisher
                }

                req = Request.Builder()
                    .url("https://m.facebook.com/login.php?next=https://m.facebook.com/friends/center/friends/")
                    .post(reqBody)
                    .build()

                try {
                    val response = httpClient.newCall(req).execute()
                    if (response.isSuccessful) {
                        responseStr = response.body!!.string()
                    }
                    response.close()
                } catch (e: IOException) {
                    singlePublisher.onError(e)
                    return@fromPublisher
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
                singlePublisher.onNext(responseStr)
                singlePublisher.onComplete()
            }
        }
    }
}
