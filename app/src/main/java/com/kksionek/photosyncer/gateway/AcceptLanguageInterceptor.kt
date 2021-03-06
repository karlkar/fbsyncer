package com.kksionek.photosyncer.gateway

import okhttp3.Interceptor
import okhttp3.Response

class AcceptLanguageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("Accept-Language", "en-GB,en;q=0.9")
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}