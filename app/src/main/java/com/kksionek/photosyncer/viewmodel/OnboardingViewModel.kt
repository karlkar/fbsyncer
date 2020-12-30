package com.kksionek.photosyncer.viewmodel

import android.Manifest
import android.content.Context
import androidx.core.content.PermissionChecker
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.sync.AccountManager
import dagger.hilt.android.qualifiers.ApplicationContext

class OnboardingViewModel @ViewModelInject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val accountManager: AccountManager
) : ViewModel() {

    fun hasPrerequisites(): Boolean {
        return isFbAccountSetUp()
                || areContactsPermissionsGranted()
                || isAccountCreated()
    }

    fun isFbAccountSetUp(): Boolean {
        return listOf("PREF_LOGIN", "PREF_PASSWORD").none { secureStorage.read(it).isNullOrEmpty() }
    }

    fun areContactsPermissionsGranted(): Boolean {
        return listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        ).all {
            PermissionChecker.checkSelfPermission(context, it) ==
                    PermissionChecker.PERMISSION_GRANTED
        }
    }

    fun isAccountCreated(): Boolean =
        accountManager.isAccountCreated()
}