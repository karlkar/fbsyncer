package com.kksionek.photosyncer.model

import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import com.kksionek.photosyncer.data.Contact
import io.reactivex.Single

object RxContacts {

    private val PROJECTION = arrayOf(
        ContactsContract.Contacts.NAME_RAW_CONTACT_ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_URI
    )

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

        val contacts = generateSequence { if (cursor.moveToNext()) cursor else null }
            .map {
                val id = cursor.getString(idxId)
                val displayName = cursor.getString(idxDisplayNamePrimary)
                val thumbnailPath = cursor.getString(idxThumbnail)
                Contact(id, displayName, thumbnailPath)
            }
            .toList()
        cursor.close()
        subscriber.onSuccess(contacts)
    }
}