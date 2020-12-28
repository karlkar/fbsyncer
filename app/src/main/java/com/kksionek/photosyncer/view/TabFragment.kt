package com.kksionek.photosyncer.view

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.material.tabs.TabLayout
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.data.Friend
import com.kksionek.photosyncer.databinding.FragmentTabBinding
import com.kksionek.photosyncer.repository.SecureStorage
import com.kksionek.photosyncer.sync.AccountUtils
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TabFragment : Fragment() {

    companion object {

        const val REQUEST_FACEBOOK_PICKER = 4445
        const val PREF_LAST_AD = "LAST_AD"
    }

    private var _binding: FragmentTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var realmUi: Realm
    private lateinit var menuItemSyncCtrl: MenuItemSyncCtrl

    @Inject
    lateinit var secureStorage: SecureStorage

    private val syncStatusObserver = { _: Int ->
        requireActivity().runOnUiThread {
            val account = AccountUtils.account
            val syncActive = ContentResolver.isSyncActive(account, AccountUtils.CONTENT_AUTHORITY)
            val syncPending = ContentResolver.isSyncPending(account, AccountUtils.CONTENT_AUTHORITY)

            //Log.d(TAG, "Event received: " + (syncActive || syncPending));
            if (syncActive || syncPending) {
                menuItemSyncCtrl.startAnimation()
            } else {
                menuItemSyncCtrl.endAnimation()
            }
        }
    }
    private var syncObserverHandle: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        val navController = findNavController()
        val currentBackStackEntry = navController.currentBackStackEntry
        val savedStateHandle = currentBackStackEntry!!.savedStateHandle
        savedStateHandle.getLiveData<Boolean>(OnboardingFragment.LOGIN_SUCCESSFUL)
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
        if ((secureStorage.read("PREF_LOGIN").isNullOrEmpty()
                    && secureStorage.read("PREF_PASSWORD").isNullOrEmpty())
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ).any { checkSelfPermission(requireContext(), it) != PERMISSION_GRANTED })
            || !AccountUtils.isAccountCreated(requireContext())
        ) {
            findNavController().navigate(R.id.onboardingFragment)
            return
        }

        realmUi = Realm.getDefaultInstance()

        val adapter = ViewPagerAdapter(requireContext(), realmUi)
        binding.pager.adapter = adapter

        for (i in 0 until adapter.count) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(adapter.getPageTitle(i)))
        }
        binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        binding.pager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(binding.tabLayout))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.pager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

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
                ContentResolver.requestSync(
                    AccountUtils.account,
                    AccountUtils.CONTENT_AUTHORITY,
                    Bundle()
                )
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

    override fun onResume() {
        super.onResume()
        val mask =
            ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING
        syncObserverHandle = ContentResolver.addStatusChangeListener(mask, syncStatusObserver)
    }

    override fun onPause() {
        ContentResolver.removeStatusChangeListener(syncObserverHandle)
        syncObserverHandle = null
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        realmUi.close()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FACEBOOK_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val contactId = data.getStringExtra(FbPickerFragment.EXTRA_ID)
                val contact = realmUi.where(Contact::class.java)
                    .equalTo("id", contactId)
                    .findFirst()
                val friendId = data.getStringExtra(FbPickerFragment.EXTRA_RESULT_ID)
                val friend = realmUi.where(Friend::class.java)
                    .equalTo("id", friendId)
                    .findFirst()
                val sameNameFriends = realmUi.where(Friend::class.java)
                    .equalTo("mName", friend!!.getName())
                    .findAll()

                realmUi.executeTransaction {
                    contact!!.related = friend
                    contact.synced = true
                    contact.isManual = true
                }
                // TODO: Sync single

                if (sameNameFriends.size > 1) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.problem)
                        .setMessage(R.string.problem_message)
                        .setPositiveButton(android.R.string.ok) { dialogInterface, _ -> dialogInterface.dismiss() }
                        .create()
                        .show()
                }

                Toast.makeText(requireContext(), R.string.sync_preference_saved, Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}