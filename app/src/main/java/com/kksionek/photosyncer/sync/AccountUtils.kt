package com.kksionek.photosyncer.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.core.content.getSystemService

object AccountUtils {
    const val ACCOUNT_NAME = "Photo Syncer"
    const val ACCOUNT_TYPE = "com.kksionek.photosyncer"

    const val CONTENT_AUTHORITY = "com.kksionek.photosyncer"

    private const val PREF_ACCOUNT_CREATED = "ACCOUNT_CREATED"

    val account: Account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    fun isAccountCreated(ctx: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("ACCOUNT_CREATED", false)

    fun createAccount(ctx: Context): Account? {
        val systemService: AccountManager? = ctx.getSystemService()
        val account = account
        return if (systemService?.addAccountExplicitly(account, null, null) == true) {
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true)
            ContentResolver.addPeriodicSync(
                account,
                CONTENT_AUTHORITY,
                Bundle(),
                (7 * 24 * 60 * 60).toLong()
            )
            PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                putBoolean(PREF_ACCOUNT_CREATED, true)
            }
            account
        } else {
            null
        }
    }
}
