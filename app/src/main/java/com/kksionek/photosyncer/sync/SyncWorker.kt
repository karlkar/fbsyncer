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
import androidx.core.content.edit
import androidx.core.content.getSystemService
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
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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

    private val threadPool = Executors.newFixedThreadPool(2)

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

    private fun performSync() {
        val callables = mutableListOf<Callable<Unit>>()
        val contacts = contactsRepository.getAllContactsAndFriends().blockingGet()

        // All contacts in database are new => they have synced set to false
        //        for (Contact contact : contacts)
        //            contact.setSynced(false);
        //        for (Friend friend : friends)
        //            friend.setSynced(false);

        for (contact in contacts) {
            if (contact.contactEntity.isManual) {
                if (contact.friendEntity != null) {
                    syncToRelated(callables, contact, true)
                }
            } else {
                val sameNameFriends =
                    friendRepository.getFriendsWithName(contact.contactEntity.name).blockingGet()
                if (sameNameFriends.size == 1) {
                    contactsRepository.bindFriend(
                        contact.contactEntity.id,
                        sameNameFriends.first().id
                    ).blockingAwait()
                    syncToRelated(callables, contact, false)
                } else {
                    if (sameNameFriends.isEmpty()) {
                        Log.d(
                            TAG,
                            "performSync: [${contact.contactEntity.name}] Friend doesn't exist on social network."
                        )
                    } else {
                        Log.d(
                            TAG,
                            "performSync: [${contact.contactEntity.name}] Friend exists multiple times and connot be synced automatically."
                        )
                    }
                    contactsRepository.setSynced(
                        contact.contactEntity.id,
                        synced = false,
                        manual = false
                    ).blockingAwait()
                }
            }
        }

        try {
            threadPool.invokeAll(callables)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun syncToRelated(
        callables: MutableList<Callable<Unit>>,
        contact: ContactAndFriend,
        manual: Boolean
    ) {
        if (manual) {
            Log.d(
                TAG,
                "syncToRelated: [${contact.contactEntity.name}] - syncing using manual settings."
            )
        } else {
            Log.d(
                TAG,
                "syncToRelated: [${contact.contactEntity.name}] - syncing using auto settings."
            )
        }
        callables.add(Callable {
            setContactPhoto(
                contact.contactEntity.id,
                contact.friendEntity!!.photo
            )
        })
        contactsRepository.setSynced(
            contact.contactEntity.id,
            synced = true,
            manual
        ).blockingAwait()
    }

    private fun setContactPhoto(rawContactId: Long, photo: String) {
        val bitmap = getBitmapFromURL(photo) ?: return

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
                applicationContext.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
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

        if (days < 6) {
            return
        }

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

        val notificationManager: NotificationManager? = applicationContext.getSystemService()
        val notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (::notificationChannel.isInitialized) {
                notificationChannel = NotificationChannel(
                    "syncDone",
                    "Photo Syncer",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationChannel.enableLights(true)
                notificationManager?.createNotificationChannel(notificationChannel)
            }
            notificationChannel.id
        } else {
            ""
        }
        val builder = NotificationCompat.Builder(
            applicationContext,
            notificationChannelId
        )
        builder.setSmallIcon(R.mipmap.ic_launcher)
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
        notificationManager?.notify(0, builder.build())
    }
}