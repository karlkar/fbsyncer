package com.kksionek.photosyncer.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.material.tabs.TabLayout
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentTabBinding
import com.kksionek.photosyncer.model.ContactEntity
import com.kksionek.photosyncer.model.ContactsAdapter
import com.kksionek.photosyncer.model.OnItemClickListener
import com.kksionek.photosyncer.viewmodel.OnboardingViewModel
import com.kksionek.photosyncer.viewmodel.TabViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TabFragment : Fragment() {

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuItemSyncCtrl: MenuItemSyncCtrl
    private val contactsAdapter = ContactsAdapter<ContactEntity>()

    private val tabViewModel: TabViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()

    private val onAdapterItemClickListener: OnItemClickListener<ContactEntity> = { contactEntity ->
        tabViewModel.onItemClicked(contactEntity)
    }

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

        binding.recyclerView.adapter = contactsAdapter.also {
            it.onItemClickListener = onAdapterItemClickListener
        }

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

        initObservers()

        tabViewModel.checkAd(requireActivity().intent)
    }

    private fun initObservers() {
        with(tabViewModel) {
            isSyncRunning.observe(viewLifecycleOwner) { running ->
                if (running) {
                    menuItemSyncCtrl.startAnimation()
                } else {
                    menuItemSyncCtrl.endAnimation()
                }
            }

            data.observe(viewLifecycleOwner) {
                contactsAdapter.submitList(it)
            }

            launchPickAFriend.observe(viewLifecycleOwner) { id ->
                id?.let { pickAFriend(it) }
            }

            showCancelAutoSyncDialog.observe(viewLifecycleOwner) { id ->
                id?.let { showCancelAutoSyncConfirmation(it) }
            }

            showReleaseManualBondConfirmation.observe(viewLifecycleOwner) { id ->
                id?.let { showReleaseManualBondConfirmation(it) }
            }

            showAd.observe(viewLifecycleOwner) {
                val interstitialAd = InterstitialAd(requireContext()).apply {
                    adUnitId = getString(R.string.interstitial_ad_unit_id)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            tabViewModel.onAdLoaded()
                            show()
                        }

                        override fun onAdFailedToLoad(errorCode: Int) {}

                        override fun onAdClosed() {}
                    }
                }
                val adRequest = AdRequest.Builder().build()
                interstitialAd.loadAd(adRequest)
            }

            setSelectedTab(0)
        }
    }

    private fun pickAFriend(contactEntityId: Long) {
        findNavController().navigate(
            TabFragmentDirections.actionTabFragmentToFbPickerFragment(contactEntityId)
        )
    }

    private fun showCancelAutoSyncConfirmation(contactEntityId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_cancel_auto_sync_title)
            .setMessage(R.string.alert_cancel_auto_sync_message)
            .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                tabViewModel.cancelAutoSync(contactEntityId)
                dialogInterface.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()
            .show()
    }

    private fun showReleaseManualBondConfirmation(contactEntityId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_release_bond_title)
            .setMessage(R.string.alert_release_bond_message)
            //TODO: make another dialog/preference remembering if app should remove photo automatically
            .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                tabViewModel.releaseBond(contactEntityId)
                dialogInterface.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()
            .show()
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