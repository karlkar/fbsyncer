package com.kksionek.photosyncer.view

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentOnboardingBinding
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.sync.AccountUtils
import com.kksionek.photosyncer.sync.SyncAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    companion object {
        const val LOGIN_SUCCESSFUL: String = "LOGIN_SUCCESSFUL"

        private const val REQUEST_PERMISSIONS_CONTACTS = 4444
        private const val REQUEST_WHITELIST_PERMISSION = 1616

        private const val TAG = "OnboardingFragment"
    }

    private lateinit var realmUi: Realm

    @Inject
    lateinit var secureStorage: SecureStorage

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private lateinit var savedStateHandle: SavedStateHandle

    private val isSmartManagerInstalled: Boolean
        get() = try {
            requireActivity().packageManager.getPackageInfo(
                "com.samsung.android.sm",
                PackageManager.GET_ACTIVITIES
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

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
            it.set(LOGIN_SUCCESSFUL, false)
        }

        realmUi = Realm.getDefaultInstance()

        showPermissionRequestScreen()
    }

    private fun showPermissionRequestScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ).any { checkSelfPermission(requireContext(), it) != PERMISSION_GRANTED }
        ) {
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
        if (secureStorage.read("PREF_LOGIN").isNullOrEmpty()
            && secureStorage.read("PREF_PASSWORD").isNullOrEmpty()
        ) {
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
                        SyncAdapter.fbLogin(
                            okHttpClient,
                            secureStorage
                        )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                {
                                    binding.loginProgress.visibility = View.GONE
                                    showWhitelistScreen()
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
            showWhitelistScreen()
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

    private fun showWhitelistScreen() {
        if (isSmartManagerInstalled) {
            binding.questionTextView.setText(R.string.activity_main_whitelist_message)
            binding.questionButton.apply {
                setText(R.string.activity_main_whitelist_btn)
                setOnClickListener {
                    val intent = Intent()
                    intent.component = ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                    try {
                        startActivity(intent)
                    } catch (ex: ActivityNotFoundException) {
                        Log.d(TAG, "showWhitelistScreen: Strange... Application wasn't detected...")
                    }

                    showSyncScreen()
                }
            }
        } else {
            showSyncScreen()
        }
    }

    private fun showSyncScreen() {
        binding.questionTextView.setText(R.string.activity_main_prerequisites_fulfilled_message)
        binding.questionButton.apply {
            setText(R.string.activity_main_button_sync)
            setOnClickListener {
                isEnabled = false
                if (!AccountUtils.isAccountCreated(requireContext())) {
                    val account = AccountUtils.createAccount(context)
                    if (account != null) {
                        ContentResolver.requestSync(
                            account,
                            AccountUtils.CONTENT_AUTHORITY,
                            Bundle()
                        )
                        savedStateHandle.set(LOGIN_SUCCESSFUL, true)
                        findNavController().popBackStack()
                    } else {
                        Log.e(
                            TAG,
                            "showSyncScreen: I couldn't create a new account. That's strange"
                        )
                    }
                } else {
                    ContentResolver.requestSync(
                        AccountUtils.account,
                        AccountUtils.CONTENT_AUTHORITY,
                        Bundle()
                    )
                    savedStateHandle.set(LOGIN_SUCCESSFUL, true)
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_WHITELIST_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                showSyncScreen()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.activity_main_whitelist_not_granted_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
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

    override fun onDestroy() {
        realmUi.close()
        super.onDestroy()
    }
}