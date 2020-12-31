package com.kksionek.photosyncer.repository

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.kksionek.photosyncer.model.ContactAndFriend
import com.kksionek.photosyncer.model.ContactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject

class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val contactDao: ContactDao
) : ContactsRepository {

    companion object {
        private const val TAG = "ContactsRepositoryImpl"

        private val PROJECTION = arrayOf(
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI
        )
    }

    override fun getContacts(): Single<List<ContactEntity>> {
        return Single.zip(
            obtainContacts(),
            contactDao.getAllContacts(),
            { androidContacts, roomContacts ->
                Log.d(TAG, "getAndStoreContacts: Found ${androidContacts.size} contacts")
                val newContacts = androidContacts.map { contact ->
                    roomContacts.firstOrNull { it.id == contact.id }
                        ?.let { oldContact ->
                            contact.copy(
                                relatedFriend = oldContact.relatedFriend,
                                isManual = oldContact.isManual
                            )
                        } ?: contact
                }
                // Store updated contacts in room
                contactDao.insertAllSync(*newContacts.toTypedArray())

                val androidContactIds = androidContacts.map { it.id }
                val contactsToBeRemoved = roomContacts.filter { oldContact ->
                    oldContact.id !in androidContactIds
                }
                contactDao.removeAllSync(*contactsToBeRemoved.toTypedArray())
                newContacts
            })
    }

    private fun obtainContacts(): Single<List<ContactEntity>> {
        return Single.create { emitter ->
            appContext.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                PROJECTION,
                null,
                null,
                ContactsContract.Contacts._ID
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(PROJECTION[0])
                val idxDisplayNamePrimary = cursor.getColumnIndex(PROJECTION[1])
                val idxThumbnail = cursor.getColumnIndex(PROJECTION[2])

                val contacts = generateSequence { if (cursor.moveToNext()) cursor else null }
                    .map {
                        val id = it.getLong(idxId)
                        val displayName = it.getString(idxDisplayNamePrimary)
                        val thumbnailPath = it.getString(idxThumbnail)
                        ContactEntity(id, displayName, thumbnailPath)
                    }
                    .toList()
                emitter.onSuccess(contacts)
            } ?: emitter.onError(IllegalStateException("Cursor couldn't be created"))
        }
    }

    override fun bindFriend(contactId: Long, friendId: Long): Completable =
        contactDao.bindFriend(contactId, friendId)

    override fun getAllContactsAndFriends(): Single<List<ContactAndFriend>> =
        contactDao.getAllContactsAndFriends()

    override fun setSynced(contactId: Long, synced: Boolean, manual: Boolean): Completable =
        contactDao.setSynced(contactId, synced, manual)
}