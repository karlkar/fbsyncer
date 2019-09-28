package com.kksionek.photosyncer.model

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kksionek.photosyncer.R
import com.kksionek.photosyncer.data.Person
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.squareup.picasso.Picasso
import io.realm.OrderedRealmCollection
import io.realm.RealmObject
import io.realm.RealmRecyclerViewAdapter

class ContactViewHolder<T>(parent: ContactsAdapter<T>, parentView: View) :
    RecyclerView.ViewHolder(parentView) where T : RealmObject, T : Person {

    private val imageView: ImageView = parentView.findViewById(R.id.thumbnail)
    private val textView: TextView = parentView.findViewById(R.id.text)

    private lateinit var innerData: T

    var data: T
        get() = innerData
        internal set(value) {
            innerData = value

            textView.text = value.getName()
            Picasso.get()
                .load(value.getPhoto())
                .placeholder(R.drawable.contact)
                .into(imageView)
        }

    init {
        itemView.setOnClickListener {
            parent.onItemClickListener?.invoke(data)
        }
    }
}

class ContactsAdapter<T>(
    context: Context,
    data: OrderedRealmCollection<T>?,
    autoUpdate: Boolean
) : RealmRecyclerViewAdapter<T, ContactViewHolder<T>>(context, data, autoUpdate),
    FastScrollRecyclerView.SectionedAdapter where T : RealmObject, T : Person {

    var onItemClickListener: OnItemClickListener<T>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder<T> {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.row_friends, parent, false)
        return ContactViewHolder(this, itemView)
    }

    override fun onBindViewHolder(holder: ContactViewHolder<T>, position: Int) {
        val contact = getItem(holder.adapterPosition)
        if (contact == null) {
            Log.e(TAG, "WTF")
            return
        }

        holder.data = contact
    }

    override fun getSectionName(position: Int): String =
        getItem(position)?.getName()?.firstOrNull()?.toString() ?: ""

    companion object {
        private const val TAG = "ContactsAdapter"
    }
}

typealias OnItemClickListener<T> = (T) -> Unit
