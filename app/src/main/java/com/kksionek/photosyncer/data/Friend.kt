package com.kksionek.photosyncer.data

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Friend @JvmOverloads constructor(
    @PrimaryKey var mId: String? = null,
    var mName: String? = null,
    var mPhoto: String? = null,
    var old: Boolean = false
) : RealmObject(), Person, Comparable<Person> {

    override fun compareTo(other: Person): Int {
        return mName!!.compareTo(other.getName())
    }

    override fun equals(o: Any?): Boolean {
        return o is Person && mName == o.getName()
    }

    override fun getId() = mId!!

    override fun getName() = mName!!

    override fun getPhoto() = mPhoto!!
}
