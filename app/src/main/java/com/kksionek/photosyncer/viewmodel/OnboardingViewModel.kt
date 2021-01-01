package com.kksionek.photosyncer.viewmodel

import android.Manifest
import android.content.Context
import androidx.core.content.PermissionChecker
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.repository.FriendRepository
import com.kksionek.photosyncer.repository.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Single

class OnboardingViewModel @ViewModelInject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val friendRepository: FriendRepository
) : ViewModel() {

    fun hasPrerequisites(): Boolean {
        return isFbAccountSetUp()
                || areContactsPermissionsGranted()
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

    fun fbLogin(): Single<String> {
        return friendRepository.fbLogin()
    }
}