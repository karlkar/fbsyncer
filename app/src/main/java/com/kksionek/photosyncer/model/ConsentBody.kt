package com.kksionek.photosyncer.model

data class ConsentBody(
    val fb_dtsg: String,
    val jazoest: String,
    val lsd: String,
//    val __dyn: String,
    val __a: String,
    val accept_consent: Boolean = true,
    val m_sess: String = "",
    val __csr: String = "",
    val __req: Int = 2,
    val __user: Int = 0
)
