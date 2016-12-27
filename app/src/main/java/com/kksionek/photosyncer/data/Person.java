package com.kksionek.photosyncer.data;

import android.support.annotation.NonNull;

public interface Person {
    String getId();
    @NonNull String getName();
    String getPhoto();
}
