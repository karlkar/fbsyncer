package com.kksionek.photosyncer.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.FragmentFbPickerBinding
import com.kksionek.photosyncer.model.ContactsAdapter
import com.kksionek.photosyncer.model.FriendEntity
import com.kksionek.photosyncer.viewmodel.FbPickerViewModel

class FbPickerFragment : Fragment() {

    private val args: FbPickerFragmentArgs by navArgs()

    private val contactId get() = args.contactId

    private var _binding: FragmentFbPickerBinding? = null
    private val binding get() = _binding!!

    private val fbPickerViewModel: FbPickerViewModel by viewModels()

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
        val contactsAdapter = ContactsAdapter<FriendEntity>()

        fbPickerViewModel.contactEntity.observe(viewLifecycleOwner) {
            it?.let { contact ->
                binding.infoTextName.text = contact.name
            }
        }
        fbPickerViewModel.friendEntities.observe(viewLifecycleOwner) {
            it?.let { list ->
                contactsAdapter.submitList(list)
            }
        }
        fbPickerViewModel.bindingCompletedWithoutError.observe(viewLifecycleOwner) {
            it?.let { success ->
                if (!success) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.problem)
                        .setMessage(R.string.problem_message)
                        .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                        .create()
                        .show()
                }

                Toast.makeText(requireContext(), R.string.sync_preference_saved, Toast.LENGTH_SHORT)
                    .show()
            }
        }

        fbPickerViewModel.init()

        contactsAdapter.onItemClickListener = { friend ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.alert_create_bond_title)
                .setMessage(
                    getString(
                        R.string.alert_create_bond_message,
                        fbPickerViewModel.contactEntity.value?.name.orEmpty(),
                        friend.name
                    )
                )
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    fbPickerViewModel.bindFriend(contactId, friend)
                    findNavController().navigateUp()
                }
                .setNegativeButton(android.R.string.no) { dialog, _ -> dialog.dismiss() }
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
}