package com.kksionek.photosyncer.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log

class AccountAuthenticatorService : Service() {

    private var accountAuthenticator: AccountAuthenticator? = null

    override fun onCreate() {
        Log.i(TAG, "Service created")
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? = accountAuthenticator!!.iBinder

    inner class AccountAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

        override fun editProperties(
            response: AccountAuthenticatorResponse,
            accountType: String
        ): Bundle = throw UnsupportedOperationException()

        @Throws(NetworkErrorException::class)
        override fun addAccount(
            response: AccountAuthenticatorResponse,
            accountType: String, authTokenType: String, requiredFeatures: Array<String>,
            options: Bundle
        ): Bundle {
            Log.i(TAG, "addAccount")
            val result = Bundle()

            result.putString(AccountManager.KEY_ACCOUNT_NAME, AccountUtils.ACCOUNT_NAME)
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, AccountUtils.ACCOUNT_TYPE)
            return result
        }

        @Throws(NetworkErrorException::class)
        override fun confirmCredentials(
            response: AccountAuthenticatorResponse,
            account: Account, options: Bundle
        ): Bundle? = null

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(
            response: AccountAuthenticatorResponse,
            account: Account, authTokenType: String, options: Bundle
        ): Bundle = throw UnsupportedOperationException()

        override fun getAuthTokenLabel(authTokenType: String): String =
            throw UnsupportedOperationException()

        @Throws(NetworkErrorException::class)
        override fun hasFeatures(
            response: AccountAuthenticatorResponse,
            account: Account, features: Array<String>
        ): Bundle = throw UnsupportedOperationException()

        @Throws(NetworkErrorException::class)
        override fun updateCredentials(
            response: AccountAuthenticatorResponse,
            account: Account, authTokenType: String, options: Bundle
        ): Bundle = throw UnsupportedOperationException()

    }

    companion object {

        private val TAG = AccountAuthenticatorService::class.java.simpleName
    }
}
