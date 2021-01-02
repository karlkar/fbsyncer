package com.kksionek.photosyncer.repository

interface SecureStorage {

    companion object {
        const val PREF_LOGIN = "PREF_LOGIN"
        const val PREF_PASSWORD = "PREF_PASSWORD"
    }

    fun write(key: String, value: String?)

    fun read(key: String): String?

    fun clear()
}