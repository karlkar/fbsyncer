package com.kksionek.photosyncer.view

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentOnboardingBinding
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.sync.AccountManager
import com.kksionek.photosyncer.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    companion object {
        const val ONBOARDING_SUCCESSFUL: String = "ONBOARDING_SUCCESSFUL"

        private const val REQUEST_PERMISSIONS_CONTACTS = 4444

        private const val TAG = "OnboardingFragment"
    }

    @Inject
    lateinit var secureStorage: SecureStorage

    @Inject
    lateinit var accountManager: AccountManager

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    private lateinit var savedStateHandle: SavedStateHandle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentOnboardingBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedStateHandle = findNavController().previousBackStackEntry!!.savedStateHandle.also {
            it.set(ONBOARDING_SUCCESSFUL, false)
        }

        showPermissionRequestScreen()
    }

    private fun showPermissionRequestScreen() {
        if (!onboardingViewModel.areContactsPermissionsGranted()) {
            binding.questionTextView.setText(R.string.activity_main_grant_contacts_access_message)
            binding.questionButton.apply {
                setText(R.string.activity_main_button_grant_contacts_access)
                setOnClickListener {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                        ),
                        REQUEST_PERMISSIONS_CONTACTS
                    )
                }
            }
        } else {
            showFbLoginScreen()
        }
    }

    private fun showFbLoginScreen() {
        if (!onboardingViewModel.isFbAccountSetUp()) {
            binding.questionTextView.setText(R.string.activity_main_facebook_permission_request_message)
            showProgress(false)
            binding.questionButton.apply {
                setText(R.string.activity_main_button_login_facebook)
                setOnClickListener {
                    showProgress(true)
                    val login = binding.fbLogin.text.toString()
                    val pass = binding.fbPass.text.toString()

                    if (login.isNotEmpty() && pass.isNotEmpty()) {
                        secureStorage.write("PREF_LOGIN", login)
                        secureStorage.write("PREF_PASSWORD", pass)
                        // TODO: Move more logic to ViewModel
                        onboardingViewModel.fbLogin()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                {
                                    binding.loginProgress.visibility = View.GONE
                                    showSyncScreen()
                                },
                                {
                                    showProgress(false)
                                    secureStorage.clear()
                                    Toast.makeText(
                                        requireContext(),
                                        R.string.activity_main_login_failed_toast,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                })
                    } else {
                        showProgress(false)
                        Toast.makeText(
                            requireContext(),
                            R.string.activity_main_login_failed_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            showSyncScreen()
        }
    }

    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        binding.fbLoginForm.visibility = if (show) View.GONE else View.VISIBLE
        binding.fbLoginForm.animate()
            .setDuration(shortAnimTime.toLong())
            .alpha((if (show) 0 else 1).toFloat())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.fbLoginForm.visibility = if (show) View.GONE else View.VISIBLE
                }
            })

        binding.loginProgress.visibility = if (show) View.VISIBLE else View.GONE
        binding.loginProgress.animate()
            .setDuration(shortAnimTime.toLong())
            .alpha((if (show) 1 else 0).toFloat())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.loginProgress.visibility = if (show) View.VISIBLE else View.GONE
                }
            })
    }

    private fun showSyncScreen() {
        binding.questionTextView.setText(R.string.activity_main_prerequisites_fulfilled_message)
        binding.questionButton.apply {
            setText(R.string.activity_main_button_sync)
            setOnClickListener {
                isEnabled = false
                if (!accountManager.isAccountCreated() && accountManager.createAccount() == null) {
                    Log.e(
                        TAG,
                        "showSyncScreen: I couldn't create a new account. That's strange"
                    )
                } else {
                    accountManager.requestSync()
                    savedStateHandle.set(ONBOARDING_SUCCESSFUL, true)
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSIONS_CONTACTS) {
            if (grantResults.size == 2
                && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            ) {
                showFbLoginScreen()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.activity_main_permission_rejected_message,
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}