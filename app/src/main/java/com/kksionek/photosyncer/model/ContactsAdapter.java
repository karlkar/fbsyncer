package com.kksionek.photosyncer.model;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.kksionek.photosyncer.R;
import com.kksionek.photosyncer.data.Person;
import com.squareup.picasso.Picasso;

import io.realm.OrderedRealmCollection;
import io.realm.RealmObject;
import io.realm.RealmRecyclerViewAdapter;

class ContactViewHolder extends RecyclerView.ViewHolder {
    public final View parentView;
    public final ImageView imageView;
    public final TextView textView;

    public ContactViewHolder(View itemView) {
        super(itemView);
        parentView = itemView;
        imageView = (ImageView) itemView.findViewById(R.id.thumbnail);
        textView = (TextView) itemView.findViewById(R.id.text);
    }
}

public class ContactsAdapter<T extends RealmObject & Person> extends RealmRecyclerViewAdapter<T, ContactViewHolder> {

    public interface OnItemClickListener<T> {
        void onItemClick(View view, T contact);
    }

    public interface OnItemLongClickListener<T> {
        void onItemLongClick(View view, T contact);
    }

    private final Context mContext;
    private OnItemClickListener mListener;
    private OnItemLongClickListener mLongListener;

    public ContactsAdapter(@NonNull Context context, @Nullable OrderedRealmCollection<T> data, boolean autoUpdate) {
        super(context, data, autoUpdate);
        mContext = context;
        mListener = null;
        mLongListener = null;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        mLongListener = listener;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_friends, parent, false);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        T contact = getItem(position);

        holder.textView.setText(contact.getName());
        Picasso.with(mContext)
                .load(contact.getPhoto())
                .placeholder(R.drawable.contact)
                .into(holder.imageView);

        holder.parentView.setOnClickListener(view -> {
            if (mListener != null)
                mListener.onItemClick(view, contact);
        });

        holder.parentView.setOnLongClickListener(v -> {
            if (mLongListener != null)
                mLongListener.onItemLongClick(v, contact);
            return mLongListener != null;
        });
    }
}
