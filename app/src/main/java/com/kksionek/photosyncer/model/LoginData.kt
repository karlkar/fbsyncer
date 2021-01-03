package com.kksionek.photosyncer.model

data class LoginData(
    val email: String,
    val password: String,
    val lsd: String,
    val m_ts: String,
    val li: String,
    val jazoest: String,
    val login: String = "Log+In",
    val try_number: Int = 0,
    val unrecognized_tries: Int = 0,
    val prefill_contact_point: String = "",
    val prefill_source: String = "",
    val prefill_type: String = "",
    val first_prefill_source: String = "",
    val first_prefill_type: String = "",
    val had_cp_prefilled: Boolean = false,
    val had_password_prefilled: Boolean = false,
    val is_smart_lock: Boolean = false
)
