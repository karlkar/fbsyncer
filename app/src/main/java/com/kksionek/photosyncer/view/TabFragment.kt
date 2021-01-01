package com.kksionek.photosyncer.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.material.tabs.TabLayout
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentTabBinding
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.model.ContactsAdapter
import com.kksionek.photosyncer.viewmodel.OnboardingViewModel
import com.kksionek.photosyncer.viewmodel.TabViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class TabFragment : Fragment() {

    companion object {
        const val PREF_LAST_AD = "LAST_AD"
    }

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuItemSyncCtrl: MenuItemSyncCtrl
    private val contactsAdapter = ContactsAdapter<ContactEntity>()

    private val tabViewModel: TabViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        val navController = findNavController()
        val currentBackStackEntry = navController.currentBackStackEntry
        val savedStateHandle = currentBackStackEntry!!.savedStateHandle
        savedStateHandle.getLiveData<Boolean>(OnboardingFragment.ONBOARDING_SUCCESSFUL)
            .observe(currentBackStackEntry) { success ->
                if (!success) {
                    val startDestination = navController.graph.startDestination
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(startDestination, true)
                        .build()
                    navController.navigate(startDestination, null, navOptions)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentTabBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = contactsAdapter
        with(binding.tabLayout) {
            tabViewModel.getTabs().forEach { tabTitle ->
                addTab(newTab().setText(tabTitle))
            }
            tabGravity = TabLayout.GRAVITY_FILL
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    tabViewModel.setSelectedTab(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }

        tabViewModel.isSyncRunning.observe(viewLifecycleOwner) {
            if (it) {
                menuItemSyncCtrl.startAnimation()
            } else {
                menuItemSyncCtrl.endAnimation()
            }
        }

        tabViewModel.data.observe(viewLifecycleOwner) {
            contactsAdapter.submitList(it)
            when (binding.tabLayout.selectedTabPosition) {
                0 -> {
                    contactsAdapter.onItemClickListener = { contactEntity ->
                        findNavController().navigate(
                            TabFragmentDirections.actionTabFragmentToFbPickerFragment(
                                contactEntity.id
                            )
                        )
                    }
                }
                1 -> {
                    contactsAdapter.onItemClickListener = { contactEntity ->
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.alert_cancel_auto_sync_title)
                            .setMessage(R.string.alert_cancel_auto_sync_message)
                            .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                                tabViewModel.cancelAutoSync(contactEntity)
                                dialogInterface.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                            .create()
                            .show()
                    }
                }
                2 -> {
                    contactsAdapter.onItemClickListener = { contactEntity ->
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.alert_release_bond_title)
                            .setMessage(R.string.alert_release_bond_message)
                            //TODO: make another dialog/preference remembering if app should remove photo automatically
                            .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                                tabViewModel.releaseBond(contactEntity)
                                dialogInterface.dismiss()
                            }
                            .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                            .create()
                            .show()
                    }
                }
            }
        }

        showAdIfNeeded()
    }

    private fun showAdIfNeeded() {
        val lastAd = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getLong(PREF_LAST_AD, 0)
        if (lastAd == 0L) {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                putLong(PREF_LAST_AD, System.currentTimeMillis())
            }
        }

        val diff = System.currentTimeMillis() - lastAd
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        if (days >= 6 || requireActivity().intent.getBooleanExtra("INTENT_AD", false)) {
            val interstitialAd = InterstitialAd(requireContext()).apply {
                adUnitId = getString(R.string.interstitial_ad_unit_id)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                            putLong(PREF_LAST_AD, System.currentTimeMillis())
                        }
                        show()
                    }

                    override fun onAdFailedToLoad(errorCode: Int) {}

                    override fun onAdClosed() {}
                }
            }
            val adRequest = AdRequest.Builder().build()
            interstitialAd.loadAd(adRequest)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.menu_sync)?.let {
            menuItemSyncCtrl = MenuItemSyncCtrl(requireContext(), it)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_sync -> {
                tabViewModel.runSync()
                return true
            }
            R.id.menu_privacy_policy -> {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://novelstudio.pl/karol/photosyncer/privacy_policy.html")
                )
                startActivity(browserIntent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        tabViewModel.scheduleSync()
    }

    override fun onResume() {
        super.onResume()

        if (!onboardingViewModel.hasPrerequisites()) {
            findNavController().navigate(R.id.onboardingFragment)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}