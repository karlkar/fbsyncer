package com.kksionek.photosyncer.model

import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import com.kksionek.photosyncer.data.Contact
import io.reactivex.Single

object RxContacts {

    private val PROJECTION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        arrayOf(
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI
        )
    } else {
        arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI
        )
    }

    fun fetch(context: Context) = Single.create<List<Contact>> { subscriber ->
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            PROJECTION,
            null,
            null,
            ContactsContract.Contacts._ID
        )
        if (cursor == null) {
            subscriber.onError(IllegalStateException("Couldn't obtain results for contacts query"))
            return@create
        }

        val idxId = cursor.getColumnIndex(PROJECTION[0])
        val idxDisplayNamePrimary = cursor.getColumnIndex(PROJECTION[1])
        val idxThumbnail = cursor.getColumnIndex(PROJECTION[2])

        var id: String
        var displayName: String
        var thumbnailPath: String
        val ids = mutableListOf<String>()
        val contacts = mutableListOf<Contact>()

        while (cursor.moveToNext()) {
            id = cursor.getString(idxId)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && id in ids) {
                continue
            }

            displayName = cursor.getString(idxDisplayNamePrimary)
            thumbnailPath = cursor.getString(idxThumbnail)

            contacts.add(Contact(id, displayName, thumbnailPath))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ids.add(id)
            }
        }
        cursor.close()
        subscriber.onSuccess(contacts)
    }
}