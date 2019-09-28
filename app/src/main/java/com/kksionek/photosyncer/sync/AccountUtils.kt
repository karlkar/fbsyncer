package com.kksionek.photosyncer.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager

object AccountUtils {
    const val ACCOUNT_NAME = "Photo Syncer"
    const val ACCOUNT_TYPE = "com.kksionek.photosyncer"

    const val CONTENT_AUTHORITY = "com.kksionek.photosyncer"

    private const val PREF_ACCOUNT_CREATED = "ACCOUNT_CREATED"

    val account: Account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    fun isAccountCreated(ctx: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sharedPreferences.getBoolean("ACCOUNT_CREATED", false)
    }

    fun createAccount(ctx: Context): Account? {
        val systemService = ctx.getSystemService(Context.ACCOUNT_SERVICE) as AccountManager
        val account = account
        return if (systemService.addAccountExplicitly(account, null, null)) {
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true)
            ContentResolver.addPeriodicSync(
                account, CONTENT_AUTHORITY, Bundle(),
                (7 * 24 * 60 * 60).toLong()
            )
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
            sharedPreferences.edit().putBoolean(PREF_ACCOUNT_CREATED, true).apply()
            account
        } else {
            null
        }
    }
}
