package com.kksionek.photosyncer.view

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.material.tabs.TabLayout
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.data.Friend
import com.kksionek.photosyncer.model.SecurePreferences
import com.kksionek.photosyncer.sync.AccountUtils
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_tab.*
import java.util.concurrent.TimeUnit

class TabActivity : AppCompatActivity() {

    private lateinit var realmUi: Realm
    private lateinit var menuItemSyncCtrl: MenuItemSyncCtrl
    private val syncStatusObserver = { _: Int ->
        runOnUiThread {
            val account = AccountUtils.account
            val syncActive = ContentResolver.isSyncActive(
                account, AccountUtils.CONTENT_AUTHORITY
            )
            val syncPending = ContentResolver.isSyncPending(
                account, AccountUtils.CONTENT_AUTHORITY
            )

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
        val prefs = SecurePreferences(baseContext, "tmp", "NotificationHandler", true)
        if ((prefs.getString("PREF_LOGIN").isNullOrEmpty()
                    && prefs.getString("PREF_PASSWORD").isNullOrEmpty())
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && listOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ).any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED })
            || !AccountUtils.isAccountCreated(this)
        ) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_tab)

        realmUi = Realm.getDefaultInstance()

        val adapter = ViewPagerAdapter(this, realmUi)
        pager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        for (i in 0 until adapter.count) {
            tabLayout.addTab(tabLayout.newTab().setText(adapter.getPageTitle(i)))
        }
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        pager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                pager.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        showAdIfNeeded()
    }

    private fun showAdIfNeeded() {
        val lastAd = PreferenceManager.getDefaultSharedPreferences(this).getLong(PREF_LAST_AD, 0)
        if (lastAd == 0L) {
            PreferenceManager.getDefaultSharedPreferences(this).edit {
                putLong(PREF_LAST_AD, System.currentTimeMillis())
            }
        }

        val diff = System.currentTimeMillis() - lastAd
        val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        if (days >= 6 || intent.getBooleanExtra("INTENT_AD", false)) {
            val interstitialAd = InterstitialAd(this).apply {
                adUnitId = getString(R.string.interstitial_ad_unit_id)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        PreferenceManager.getDefaultSharedPreferences(this@TabActivity).edit {
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val menuItem = menu.findItem(R.id.menu_sync)
        if (menuItem != null) {
            menuItemSyncCtrl = MenuItemSyncCtrl(this, menuItem)
        }
        return true
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
        super.onPause()
        ContentResolver.removeStatusChangeListener(syncObserverHandle)
        syncObserverHandle = null
    }

    override fun onDestroy() {
        realmUi.close()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FACEBOOK_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val contactId = data.getStringExtra(FacebookPickerActivity.EXTRA_ID)
                val contact = realmUi.where(Contact::class.java)
                    .equalTo("id", contactId)
                    .findFirst()
                val friendId = data.getStringExtra(FacebookPickerActivity.EXTRA_RESULT_ID)
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
                    AlertDialog.Builder(this)
                        .setTitle(R.string.problem)
                        .setMessage(R.string.problem_message)
                        .setPositiveButton(android.R.string.ok) { dialogInterface, _ -> dialogInterface.dismiss() }
                        .create()
                        .show()
                }

                Toast.makeText(this, R.string.sync_preference_saved, Toast.LENGTH_SHORT).show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {

        const val REQUEST_FACEBOOK_PICKER = 4445

        const val PREF_LAST_AD = "LAST_AD"
    }
}
