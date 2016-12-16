package com.kksionek.fbsyncer;

import android.content.Context;
import android.content.Intent;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;

class ContactViewHolder extends RecyclerView.ViewHolder {
    public View parentView;
    public ImageView imageView;
    public TextView textView;

    public ContactViewHolder(View itemView) {
        super(itemView);
        parentView = itemView;
        imageView = (ImageView) itemView.findViewById(R.id.thumbnail);
        textView = (TextView) itemView.findViewById(R.id.text);
    }
}

public class ContactsAdapter extends RealmRecyclerViewAdapter<Friend, ContactViewHolder> {

    public interface OnItemClickListener {
        void onClick(View view, Friend friend);
    }

    private Context mContext;
    private OnItemClickListener mListener;

    public ContactsAdapter(@NonNull Context context, @Nullable OrderedRealmCollection data, boolean autoUpdate) {
        super(context, data, autoUpdate);
        mContext = context;
        mListener = null;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    @Override
    public ContactViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_friends, parent, false);
        return new ContactViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ContactViewHolder holder, int position) {
        Friend friend = getItem(position);

        holder.textView.setText(friend.getName());
        Picasso.with(mContext)
                .load(friend.getPhoto())
                .placeholder(R.drawable.contact)
                .into(holder.imageView);

        holder.parentView.setOnClickListener(view -> {
            if (mListener != null)
                mListener.onClick(view, friend);
        });
    }
}
