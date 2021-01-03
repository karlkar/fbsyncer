package com.kksionek.photosyncer.view

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.fragment.findNavController
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentOnboardingBinding
import com.kksionek.photosyncer.model.FbLoginState
import com.kksionek.photosyncer.model.OnboardingStep
import com.kksionek.photosyncer.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    companion object {
        const val ONBOARDING_SUCCESSFUL = "ONBOARDING_SUCCESSFUL"
    }

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    private lateinit var savedStateHandle: SavedStateHandle

    private val requestContactsPermissions =
        prepareCall(ActivityResultContracts.RequestPermissions()) { grantResults ->
            if (grantResults.size == 2 && grantResults.all { it.value }) {
                onboardingViewModel.nextStep()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.activity_main_permission_rejected_message,
                    Toast.LENGTH_LONG
                ).show()
            }
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
            it.set(ONBOARDING_SUCCESSFUL, false)
        }

        onboardingViewModel.onboardingStep.observe(viewLifecycleOwner) {
            when (it) {
                OnboardingStep.StepPermissions -> showPermissionRequestScreen()
                OnboardingStep.FbLogin -> showFbLoginScreen()
                OnboardingStep.Completed -> markOnboardingSuccessful()
            }
        }

        onboardingViewModel.fbLoginState.observe(viewLifecycleOwner) {
            when (it) {
                FbLoginState.InProgress -> {
                    showLoginProgress(true)
                }
                FbLoginState.Success -> {
                    showLoginProgress(false)
                    onboardingViewModel.nextStep()
                }
                FbLoginState.Error -> {
                    showLoginProgress(false)
                    binding.fbPassLayout.error = getString(R.string.activity_main_login_failed_toast)
                }
            }
        }

        onboardingViewModel.nextStep()
    }

    private fun showPermissionRequestScreen() {
        with(binding) {
            questionTextView.setText(R.string.activity_main_grant_contacts_access_message)
            questionButton.apply {
                setText(R.string.activity_main_button_grant_contacts_access)
                setOnClickListener {
                    requestContactsPermissions.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                        )
                    )
                }
            }
        }
    }

    private fun showFbLoginScreen() {
        binding.questionTextView.setText(R.string.activity_main_facebook_permission_request_message)
        showLoginProgress(false)
        binding.questionButton.apply {
            setText(R.string.activity_main_button_login_facebook)
            setOnClickListener {
                val login = binding.fbLogin.text.toString()
                val pass = binding.fbPass.text.toString()

                if (login.isNotEmpty() && pass.isNotEmpty()) {
                    onboardingViewModel.fbLogin(login, pass)
                } else {
                    showLoginProgress(false)
                    Toast.makeText(
                        requireContext(),
                        R.string.activity_main_login_failed_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showLoginProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        binding.questionButton.isEnabled = !show

        with(binding.fbLoginForm) {
            visibility = if (show) View.GONE else View.VISIBLE
            animate()
                .setDuration(shortAnimTime.toLong())
                .alpha((if (show) 0 else 1).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = if (show) View.GONE else View.VISIBLE
                    }
                })
        }

        with(binding.loginProgress) {
            visibility = if (show) View.VISIBLE else View.GONE
            animate()
                .setDuration(shortAnimTime.toLong())
                .alpha((if (show) 1 else 0).toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
        }
    }

    private fun markOnboardingSuccessful() {
        savedStateHandle.set(ONBOARDING_SUCCESSFUL, true)
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}