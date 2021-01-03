package com.kksionek.photosyncer.viewmodel

import android.Manifest
import android.content.Context
import androidx.core.content.PermissionChecker
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kksionek.photosyncer.model.FbLoginState
import com.kksionek.photosyncer.model.OnboardingStep
import com.kksionek.photosyncer.repository.FriendRepository
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.repository.SecureStorage.Companion.PREF_LOGIN
import com.kksionek.photosyncer.repository.SecureStorage.Companion.PREF_PASSWORD
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class OnboardingViewModel @ViewModelInject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val disposables = CompositeDisposable()

    private val _onboardingStep = MutableLiveData<OnboardingStep>()
    val onboardingStep: LiveData<OnboardingStep> = _onboardingStep

    private val _fbLoginState = MutableLiveData<FbLoginState>()
    val fbLoginState: LiveData<FbLoginState> = _fbLoginState

    fun hasPrerequisites(): Boolean =
        isFbAccountSetUp() && areContactsPermissionsGranted()

    private fun isFbAccountSetUp(): Boolean =
        listOf(PREF_LOGIN, PREF_PASSWORD).none { secureStorage.read(it).isNullOrEmpty() }

    private fun areContactsPermissionsGranted(): Boolean {
        return listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        ).all {
            PermissionChecker.checkSelfPermission(context, it) ==
                    PermissionChecker.PERMISSION_GRANTED
        }
    }

    fun fbLogin(login: String, pass: String) {
        disposables.add(friendRepository.fbLogin()
            .doOnSubscribe {
                _fbLoginState.postValue(FbLoginState.InProgress)
                with(secureStorage) {
                    write(PREF_LOGIN, login)
                    write(PREF_PASSWORD, pass)
                }
            }
            .doOnError { secureStorage.clear() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _fbLoginState.value = FbLoginState.Success
            }, {
                _fbLoginState.value = FbLoginState.Error
            })
        )
    }

    fun nextStep() {
        if (!areContactsPermissionsGranted()) {
            _onboardingStep.value = OnboardingStep.StepPermissions
        } else if (!isFbAccountSetUp()) {
            _onboardingStep.value = OnboardingStep.FbLogin
        } else {
            _onboardingStep.value = OnboardingStep.Completed
        }
    }
}