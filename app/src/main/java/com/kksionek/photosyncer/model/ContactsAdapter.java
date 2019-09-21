package com.kksionek.photosyncer.model;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.data.Person;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.squareup.picasso.Picasso;

import io.realm.OrderedRealmCollection;
import io.realm.RealmObject;
import io.realm.RealmRecyclerViewAdapter;

class ContactViewHolder extends RecyclerView.ViewHolder {
    final View parentView;
    final ImageView imageView;
    final TextView textView;

    ContactViewHolder(View itemView) {
        super(itemView);
        parentView = itemView;
        imageView = itemView.findViewById(R.id.thumbnail);
        textView = itemView.findViewById(R.id.text);
    }
}

public class ContactsAdapter<T extends RealmObject & Person>
        extends RealmRecyclerViewAdapter<T, ContactViewHolder>
        implements FastScrollRecyclerView.SectionedAdapter {

    public interface OnItemClickListener<T> {
        void onItemClick(View view, T contact);
    }

    private OnItemClickListener<T> mListener;
    private View.OnClickListener mOnClickListener = v -> {
        if (mListener != null) {
            mListener.onItemClick(v, (T) v.getTag());
        }
    };

    public ContactsAdapter(@NonNull Context context, @Nullable OrderedRealmCollection<T> data, boolean autoUpdate) {
        super(context, data, autoUpdate);
        mListener = null;
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_friends, parent, false);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        T contact = getItem(position);
        if (contact == null) {
            holder.textView.setText("");
            holder.imageView.setImageDrawable(null);
            holder.parentView.setOnClickListener(null);
            return;
        }

        holder.textView.setText(contact.getName());
        Picasso.get()
                .load(contact.getPhoto())
                .placeholder(R.drawable.contact)
                .into(holder.imageView);

        holder.parentView.setTag(contact);
        holder.parentView.setOnClickListener(mOnClickListener);
    }

    @NonNull
    @Override
    public String getSectionName(int position) {
        T item = getItem(position);
        if (item != null) {
            return item.getName().substring(0, 1);
        } else {
            return "";
        }
    }
}
