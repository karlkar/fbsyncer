package com.kksionek.photosyncer.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.ContactsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.preference.PreferenceManager
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.model.FriendEntity
import com.kksionek.photosyncer.repository.ContactsRepository
import com.kksionek.photosyncer.repository.FriendRepository
import com.kksionek.photosyncer.view.MainActivity
import com.kksionek.photosyncer.view.TabFragment
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker @WorkerInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val contactsRepository: ContactsRepository,
    private val friendRepository: FriendRepository,
    private val okHttpClient: OkHttpClient
) : RxWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    private lateinit var notificationChannel: NotificationChannel

    override fun createWork(): Single<Result> {
        showNotification()
        return Single.zip<List<ContactEntity>, List<FriendEntity>, Unit>(
            contactsRepository.getContacts(),
            friendRepository.getFriends(),
            { _, _ -> }
        )
            .doOnSuccess { performSync() }
            .map { Result.success() }
            .onErrorReturn { Result.failure() }
            .subscribeOn(Schedulers.io())
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

    private fun performSync(): Completable {
        return contactsRepository.getAllContactsAndFriends()
            .flatMapObservable { Observable.fromIterable(it) }
            .map { contact ->
                if (contact.contactEntity.isManual) {
                    if (contact.friendEntity != null) {
                        syncToRelated(contact, true)
                    } else {
                        Completable.complete()
                    }
                } else {
                    friendRepository.getFriendsWithName(contact.contactEntity.name)
                        .flatMapCompletable { sameNameFriends ->
                            if (sameNameFriends.size == 1) {
                                contactsRepository.bindFriend(
                                    contact.contactEntity.id,
                                    sameNameFriends.first().id
                                ).andThen(syncToRelated(contact, false))
                            } else {
                                if (sameNameFriends.isEmpty()) {
                                    Log.d(
                                        TAG,
                                        "performSync: [${contact.contactEntity.name}] doesn't exist on fb."
                                    )
                                } else {
                                    Log.d(
                                        TAG,
                                        "performSync: [${contact.contactEntity.name}] exists multiple times on fb and cannot be synced automatically."
                                    )
                                }
                                contactsRepository.setSynced(
                                    contact.contactEntity.id,
                                    synced = false,
                                    manual = false
                                )
                            }
                        }
                }
            }.ignoreElements()
    }

    private fun syncToRelated(
        contact: ContactAndFriend,
        manual: Boolean
    ): Completable {
        if (manual) {
            Log.d(TAG, "syncToRelated: [${contact.contactEntity.name}] - manual settings.")
        } else {
            Log.d(TAG, "syncToRelated: [${contact.contactEntity.name}] - auto settings.")
        }
        return contactsRepository.setSynced(
            contactId = contact.contactEntity.id,
            synced = true,
            manual = manual
        ).andThen(
            setContactPhoto(
                contact.contactEntity.id,
                contact.friendEntity!!.photo
            )
        )
    }

    private fun setContactPhoto(rawContactId: Long, photo: String): Completable {
        val bitmap = getBitmapFromURL(photo) ?: return Completable.complete()

        return Completable.fromCallable {
            ByteArrayOutputStream().use { stream ->
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
                applicationContext.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.Data._ID),
                    where,
                    null,
                    null
                ).use {
                    it?.let { cursor ->
                        if (cursor.moveToFirst()) {
                            photoRow = cursor.getInt(0)
                        }
                    }
                }

                if (photoRow >= 0) {
                    applicationContext.contentResolver.update(
                        ContactsContract.Data.CONTENT_URI,
                        values,
                        "${ContactsContract.Data._ID} = $photoRow",
                        null
                    )
                } else {
                    Log.d(TAG, "setContactPhoto: INSERT $rawContactId")
                    applicationContext.contentResolver.insert(
                        ContactsContract.Data.CONTENT_URI,
                        values
                    )
                }
            }
        }
    }

    private fun showNotification() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var lastAd = sharedPrefs.getLong(TabFragment.PREF_LAST_AD, 0)

        if (lastAd == 0L) {
            lastAd = System.currentTimeMillis()
            sharedPrefs.edit {
                putLong(TabFragment.PREF_LAST_AD, lastAd)
            }
        }

        val diff = System.currentTimeMillis() - lastAd
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        if (days < 6) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("INTENT_AD", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (::notificationChannel.isInitialized) {
                notificationChannel = NotificationChannel(
                    "syncDone",
                    "Photo Syncer",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationChannel.enableLights(true)
                notificationManager.createNotificationChannel(notificationChannel)
            }
            notificationChannel.id
        } else {
            ""
        }
        NotificationCompat.Builder(applicationContext, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(applicationContext.getString(R.string.notification_synchronisation_done))
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(applicationContext.getString(R.string.notification_synchronisation_done))
            )
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .also { notificationManager.notify(0, it.build()) }
    }
}