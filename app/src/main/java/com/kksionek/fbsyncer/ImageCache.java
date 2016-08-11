package com.kksionek.fbsyncer;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;

public class ImageCache {
    private final HashMap<String, Drawable> mMap = new HashMap<>();

    public ImageCache() {
    }

    public Drawable takeDrawable(@NonNull String id) {
        return mMap.get(id);
    }

    public void addToMap(@NonNull String id, @Nullable Drawable drawable) {
        mMap.put(id, drawable);
    }
}
