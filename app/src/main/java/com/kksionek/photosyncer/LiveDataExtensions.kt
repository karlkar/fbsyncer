package com.kksionek.photosyncer

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations

fun <T, U> LiveData<T>.map(mapFunction: Function1<T, U>) =
    Transformations.map(this, mapFunction)