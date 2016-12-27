package com.kksionek.photosyncer.model;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;

import com.kksionek.photosyncer.data.Contact;

import rx.Observable;
import rx.Subscriber;

public class RxContacts {

    private static final String[] PROJECTION = {
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI
    };

    public static Observable<Contact> fetch(@NonNull final Context context) {
        return Observable.create(new Observable.OnSubscribe<Contact>() {
            @Override
            public void call(Subscriber<? super Contact> subscriber) {
                Cursor cursor = context.getContentResolver().query(
                        ContactsContract.Data.CONTENT_URI,
                        PROJECTION,
                        null,
                        null,
                        ContactsContract.Data.CONTACT_ID);
                if (cursor == null)
                    return;

                int idxId = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
                int idxDisplayNamePrimary = cursor.getColumnIndex(
                        ContactsContract.Data.DISPLAY_NAME_PRIMARY);
                int idxThumbnail = cursor.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI);

                String id, displayName, thumbnailPath;

                while (cursor.moveToNext()) {
                    id = cursor.getString(idxId);
                    displayName = cursor.getString(idxDisplayNamePrimary);
                    thumbnailPath = cursor.getString(idxThumbnail);

                    subscriber.onNext(new Contact(id, displayName, thumbnailPath));
                }
                cursor.close();
                subscriber.onCompleted();
            }
        }).onBackpressureBuffer();
    }

    private RxContacts() {
    }
}