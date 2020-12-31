package com.kksionek.photosyncer.model

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.databinding.RowFriendsBinding
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.squareup.picasso.Picasso

class ContactViewHolder<T : Person>(
    parent: ContactsAdapter<T>,
    private val binding: RowFriendsBinding
) : RecyclerView.ViewHolder(binding.root) {

    private lateinit var innerData: T

    var data: T
        get() = innerData
        internal set(value) {
            innerData = value

            binding.text.text = value.name
            Picasso.get()
                .load(value.photo)
                .placeholder(R.drawable.contact)
                .into(binding.thumbnail)
        }

    init {
        itemView.setOnClickListener {
            parent.onItemClickListener?.invoke(data)
        }
    }
}

class ContactsAdapter<T : Person> : ListAdapter<T, ContactViewHolder<T>>(
    object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            TODO("Not yet implemented")
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            TODO("Not yet implemented")
        }
    }
), FastScrollRecyclerView.SectionedAdapter {

    var onItemClickListener: OnItemClickListener<T>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder<T> {
        val binding = RowFriendsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(this, binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder<T>, position: Int) {
        if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
            Log.e(TAG, "WTF")
            return
        }

        holder.data = getItem(holder.bindingAdapterPosition)!!
    }

    override fun getSectionName(position: Int): String =
        getItem(position)?.name?.firstOrNull()?.toString().orEmpty()

    companion object {
        private const val TAG = "ContactsAdapter"
    }
}

typealias OnItemClickListener<T> = (T) -> Unit
