package com.kksionek.photosyncer.data

open class Contact constructor(
    override var id: Int = -1,
    override var name: String = "",
    override var photo: String = "",
    var related: Friend? = null,
    var synced: Boolean = false,
    var old: Boolean = false,
    var isManual: Boolean = false
) : Person, Comparable<Person> {

    override fun compareTo(other: Person): Int {
        return name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        return other is Person && name == other.name
    }
}
