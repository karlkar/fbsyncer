package com.kksionek.photosyncer.data

open class Friend constructor(
    override var id: Int = -1,
    override var name: String = "",
    override var photo: String = "",
    var old: Boolean = false
) : Person, Comparable<Person> {

    override fun compareTo(other: Person): Int {
        return name.compareTo(other.name)
    }

    override fun equals(other: Any?): Boolean {
        return other is Person && name == other.name
    }
}
