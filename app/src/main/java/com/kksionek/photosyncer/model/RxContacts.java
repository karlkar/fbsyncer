package com.kksionek.photosyncer.model;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;

import com.kksionek.photosyncer.data.Contact;

import java.util.ArrayList;

import rx.Observable;
import rx.Subscriber;

public class RxContacts {

    private static final String[] PROJECTION = new String[3];

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PROJECTION[0] = ContactsContract.Contacts.NAME_RAW_CONTACT_ID;
            PROJECTION[1] = ContactsContract.Contacts.DISPLAY_NAME;
            PROJECTION[2] = ContactsContract.Contacts.PHOTO_URI;
        } else {
            PROJECTION[0] = ContactsContract.Data.RAW_CONTACT_ID;
            PROJECTION[1] = ContactsContract.Data.DISPLAY_NAME_PRIMARY;
            PROJECTION[2] = ContactsContract.Data.PHOTO_THUMBNAIL_URI;
        }
    }

    public static Observable<Contact> fetch(@NonNull final Context context) {
        return Observable.create(new Observable.OnSubscribe<Contact>() {
            @Override
            public void call(Subscriber<? super Contact> subscriber) {
                Cursor cursor = context.getContentResolver().query(
                        ContactsContract.Contacts.CONTENT_URI,
                        PROJECTION,
                        null,
                        null,
                        ContactsContract.Contacts._ID);
                if (cursor == null)
                    return;

                int idxId = cursor.getColumnIndex(PROJECTION[0]);
                int idxDisplayNamePrimary = cursor.getColumnIndex(PROJECTION[1]);
                int idxThumbnail = cursor.getColumnIndex(PROJECTION[2]);

                String id, displayName, thumbnailPath;
                ArrayList<String> ids = new ArrayList<>();

                while (cursor.moveToNext()) {
                    id = cursor.getString(idxId);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                            && ids.contains(id))
                        continue;

                    displayName = cursor.getString(idxDisplayNamePrimary);
                    thumbnailPath = cursor.getString(idxThumbnail);

                    subscriber.onNext(new Contact(id, displayName, thumbnailPath));
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                        ids.add(id);
                }
                cursor.close();
                subscriber.onCompleted();
            }
        }).onBackpressureBuffer();
    }

    private RxContacts() {
    }
}