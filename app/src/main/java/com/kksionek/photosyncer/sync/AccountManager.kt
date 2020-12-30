package com.kksionek.photosyncer.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AccountManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        const val ACCOUNT_NAME = "Photo Syncer"
        const val ACCOUNT_TYPE = "com.kksionek.photosyncer"

        private const val CONTENT_AUTHORITY = "com.kksionek.photosyncer"
    }

    private val systemAccountManager: AccountManager = context.getSystemService()!!

    private val account: Account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    fun isAccountCreated(): Boolean =
        systemAccountManager.getAccountsByType(ACCOUNT_TYPE).isNotEmpty()

    fun createAccount(): Account? {
        return if (systemAccountManager.addAccountExplicitly(account, null, null)) {
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true)
            ContentResolver.addPeriodicSync(
                account,
                CONTENT_AUTHORITY,
                Bundle(),
                (7 * 24 * 60 * 60).toLong()
            )
            account
        } else {
            null
        }
    }

    fun requestSync() {
        ContentResolver.requestSync(
            account,
            CONTENT_AUTHORITY,
            Bundle()
        )
    }

    fun isSyncActive(): Boolean =
        ContentResolver.isSyncActive(account, CONTENT_AUTHORITY)

    fun isSyncPending(): Boolean =
        ContentResolver.isSyncPending(account, CONTENT_AUTHORITY)
}
