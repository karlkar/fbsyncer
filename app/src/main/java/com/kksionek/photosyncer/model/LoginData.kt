package com.kksionek.photosyncer.model

data class LoginData(
    val email: String,
    val password: String,
    val lsd: String,
    val m_ts: String,
    val li: String,
    val jazoest: String,
    val login: String = "Log%20In",
    val try_number: Int = 0,
    val unrecognized_tries: Int = 0
)