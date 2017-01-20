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

    private static final String[] PROJECTION_PRE_LOLLIPOP = {
            ContactsContract.Data.RAW_CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI
    };

    private static final String[] PROJECTION = {
            ContactsContract.Contacts.NAME_RAW_CONTACT_ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI
    };

    public static Observable<Contact> fetch(@NonNull final Context context) {
        return Observable.create(new Observable.OnSubscribe<Contact>() {
            @Override
            public void call(Subscriber<? super Contact> subscriber) {
                Cursor cursor;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cursor = context.getContentResolver().query(
                            ContactsContract.Contacts.CONTENT_URI,
                            PROJECTION,
                            null,
                            null,
                            ContactsContract.Contacts._ID);
                } else {
                    cursor = context.getContentResolver().query(
                            ContactsContract.Data.CONTENT_URI,
                            PROJECTION_PRE_LOLLIPOP,
                            null,
                            null,
                            ContactsContract.Data._ID);
                }
                if (cursor == null)
                    return;

                int idxId, idxDisplayNamePrimary, idxThumbnail;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    idxId = cursor.getColumnIndex(
                            ContactsContract.Contacts.NAME_RAW_CONTACT_ID);
                    idxDisplayNamePrimary = cursor.getColumnIndex(
                            ContactsContract.Contacts.DISPLAY_NAME);
                    idxThumbnail = cursor.getColumnIndex(
                            ContactsContract.Contacts.PHOTO_URI);
                } else {
                    idxId = cursor.getColumnIndex(
                            ContactsContract.Data.RAW_CONTACT_ID);
                    idxDisplayNamePrimary = cursor.getColumnIndex(
                            ContactsContract.Data.DISPLAY_NAME_PRIMARY);
                    idxThumbnail = cursor.getColumnIndex(
                            ContactsContract.Data.PHOTO_THUMBNAIL_URI);
                }

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