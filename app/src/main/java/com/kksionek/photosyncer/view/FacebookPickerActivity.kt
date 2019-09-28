package com.kksionek.photosyncer.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.data.Friend
import com.kksionek.photosyncer.model.ContactsAdapter
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_fb_picker.*

class FacebookPickerActivity : AppCompatActivity() {

    private lateinit var contactId: String

    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fb_picker)
        contactId = intent.getStringExtra(EXTRA_ID)

        realm = Realm.getDefaultInstance()
        val contact = realm.where(Contact::class.java)
            .equalTo("id", contactId)
            .findFirst()

        infoTextName.text = contact!!.getName()

        val notSyncedFriends = realm.where(Friend::class.java)
            .findAll()
            .sort("mName", Sort.ASCENDING)
        val contactsAdapter = ContactsAdapter(this, notSyncedFriends, false)
        contactsAdapter.onItemClickListener = { friend ->
            AlertDialog.Builder(this@FacebookPickerActivity)
                .setTitle(R.string.alert_create_bond_title)
                .setMessage(
                    getString(
                        R.string.alert_create_bond_message,
                        contact.getName(),
                        friend.getName()
                    )
                )
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    val intent = Intent().apply {
                        intent.putExtra(EXTRA_ID, contactId)
                        intent.putExtra(EXTRA_RESULT_ID, friend.getId())
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }
                .setNegativeButton(android.R.string.no) { dialogInterface, _ -> dialogInterface.dismiss() }
                .create()
                .show()
        }
        recyclerView.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(Activity.RESULT_CANCELED)
    }

    override fun onDestroy() {
        realm.close()
        super.onDestroy()
    }

    companion object {

        const val EXTRA_ID = "ID"
        const val EXTRA_RESULT_ID = "resultID"
    }
}
