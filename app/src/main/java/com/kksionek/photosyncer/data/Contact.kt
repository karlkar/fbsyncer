package com.kksionek.photosyncer.data

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Contact @JvmOverloads constructor(
    @PrimaryKey var mId: String? = null,
    var mName: String? = null,
    var mPhoto: String? = null,
    var related: Friend? = null,
    var synced: Boolean = false,
    var old: Boolean = false,
    var isManual: Boolean = false
) : RealmObject(), Person, Comparable<Person> {

    override fun compareTo(other: Person): Int {
        return mName!!.compareTo(other.getName())
    }

    override fun equals(other: Any?): Boolean {
        return other is Person && mName == other.getName()
    }

    override fun getId() = mId!!

    override fun getName() = mName!!

    override fun getPhoto() = mPhoto!!
}
