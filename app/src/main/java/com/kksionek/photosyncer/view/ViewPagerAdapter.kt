package com.kksionek.photosyncer.view

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager.widget.PagerAdapter
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.model.ContactsAdapter
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.tab_not_synced.view.*

internal class ViewPagerAdapter(
    private val parentActivity: Activity,
    private val realmUi: Realm
) : PagerAdapter() {

    override fun getCount(): Int = TAB_COUNT

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view === `object`

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val context = container.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.tab_not_synced, container, false)
        container.addView(view)

        val recyclerView = view.recycler_view
        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        val contactsAdapter: ContactsAdapter<Contact>

        when (position) {
            0 -> {
                val notSyncedContacts = realmUi.where(Contact::class.java)
                    .isNull("related")
                    .equalTo("isManual", false)
                    .findAll()
                    .sort("mName", Sort.ASCENDING)

                contactsAdapter = ContactsAdapter(context, notSyncedContacts, true)
                contactsAdapter.onItemClickListener = { contact ->
                    val facebookPicketIntent =
                        Intent(context, FacebookPickerActivity::class.java).apply {
                            putExtra(FacebookPickerActivity.EXTRA_ID, contact.getId())
                        }
                    parentActivity.startActivityForResult(
                        facebookPicketIntent,
                        TabActivity.REQUEST_FACEBOOK_PICKER
                    )
                }
            }
            1 -> {
                val autoSyncedContacts = realmUi.where(Contact::class.java)
                    .isNotNull("related")
                    .equalTo("isManual", false)
                    .findAll()
                    .sort("mName", Sort.ASCENDING)

                contactsAdapter = ContactsAdapter(context, autoSyncedContacts, true)
                contactsAdapter.onItemClickListener = { contact ->
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_cancel_auto_sync_title)
                        .setMessage(R.string.alert_cancel_auto_sync_message)
                        .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                            realmUi.executeTransaction {
                                contact.related = null
                                contact.isManual = true
                                contact.synced = true
                            }
                            dialogInterface.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                        .create()
                        .show()
                }
            }
            2 -> {
                val manualContacts = realmUi.where(Contact::class.java)
                    .equalTo("isManual", true)
                    .findAll()
                    .sort("mName", Sort.ASCENDING)

                contactsAdapter = ContactsAdapter(context, manualContacts, true)
                contactsAdapter.onItemClickListener = { contact ->
                    AlertDialog.Builder(context)
                        .setTitle(R.string.alert_release_bond_title)
                        .setMessage(R.string.alert_release_bond_message)
                        //TODO: make another dialog/preference remembering if app should remove photo automatically
                        .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                            realmUi.executeTransaction {
                                contact.related = null
                                contact.isManual = false
                                contact.synced = false
                            }
                            dialogInterface.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                        .create()
                        .show()
                }
            }
            else -> throw IllegalStateException("Shouldn't contain more than 3 tabs")
        }
        recyclerView.adapter = contactsAdapter
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun getPageTitle(position: Int): CharSequence? = when (position) {
        0 -> parentActivity.getString(R.string.activity_tab_tab_not_synced)
        1 -> parentActivity.getString(R.string.activity_tab_tab_auto)
        2 -> parentActivity.getString(R.string.activity_tab_tab_manual)
        else -> super.getPageTitle(position)
    }

    companion object {

        private const val TAB_COUNT = 3
    }
}
