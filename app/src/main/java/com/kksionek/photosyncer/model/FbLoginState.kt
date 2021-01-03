package com.kksionek.photosyncer.model

sealed class FbLoginState {

    object InProgress: FbLoginState()

    object Success: FbLoginState()

    object Error: FbLoginState()
}
