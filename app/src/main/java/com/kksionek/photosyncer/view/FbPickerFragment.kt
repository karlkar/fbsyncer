package com.kksionek.photosyncer.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Contact
import com.kksionek.photosyncer.data.Friend
import com.kksionek.photosyncer.databinding.FragmentFbPickerBinding
import com.kksionek.photosyncer.model.ContactsAdapter
import io.realm.Realm
import io.realm.Sort

class FbPickerFragment : Fragment() {

    companion object {
        const val EXTRA_ID = "ID"
        const val EXTRA_RESULT_ID = "Result_ID"
    }

    private val args: FbPickerFragmentArgs by navArgs()

    private val contactId get() = args.contactId

    private var _binding: FragmentFbPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var realm: Realm

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentFbPickerBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        realm = Realm.getDefaultInstance()
        val contact = realm.where(Contact::class.java)
            .equalTo("id", contactId)
            .findFirst()

        binding.infoTextName.text = contact!!.getName()

        val notSyncedFriends = realm.where(Friend::class.java)
            .findAll()
            .sort("mName", Sort.ASCENDING)
        val contactsAdapter = ContactsAdapter(requireContext(), notSyncedFriends, false)
        contactsAdapter.onItemClickListener = { friend ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.alert_create_bond_title)
                .setMessage(
                    getString(
                        R.string.alert_create_bond_message,
                        contact.getName(),
                        friend.getName()
                    )
                )
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // TODO Store result in view model
//                    val intent = Intent().apply {
//                        putExtra(FacebookPickerActivity.EXTRA_ID, contactId)
//                        putExtra(FacebookPickerActivity.EXTRA_RESULT_ID, friend.getId())
//                    }
//                    setResult(Activity.RESULT_OK, intent)
//                    finish()
                }
                .setNegativeButton(android.R.string.no) { dialogInterface, _ -> dialogInterface.dismiss() }
                .create()
                .show()
        }
        binding.recyclerView.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        realm.close()
        super.onDestroy()
    }
}