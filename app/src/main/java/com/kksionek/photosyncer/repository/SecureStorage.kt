package com.kksionek.photosyncer.repository

interface SecureStorage {

    fun write(key: String, value: String?)

    fun read(key: String): String?

    fun clear()
}