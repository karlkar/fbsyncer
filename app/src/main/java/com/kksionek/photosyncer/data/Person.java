package com.kksionek.photosyncer.data;

import androidx.annotation.NonNull;

public interface Person {
    String getId();
    @NonNull String getName();
    String getPhoto();
}
