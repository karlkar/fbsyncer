package com.kksionek.photosyncer.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class JetpackSecureStorage(appContext: Context) : SecureStorage {

    private val mainKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        "preferences",
        mainKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun write(key: String, value: String?) {
        sharedPreferences.edit {
            putString(key, value)
        }
    }

    override fun read(key: String): String? =
        sharedPreferences.getString(key, null)

    override fun clear() {
        val allPrefKeys = sharedPreferences.all.keys
        sharedPreferences.edit{
            allPrefKeys.forEach {
                remove(it)
            }
        }
    }
}