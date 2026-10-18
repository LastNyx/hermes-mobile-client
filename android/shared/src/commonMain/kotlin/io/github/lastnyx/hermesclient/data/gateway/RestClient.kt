package io.github.lastnyx.hermesclient.data.gateway

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shares [base]'s pool and dispatcher; the call limit must not reach the WebSocket or APK download. */
fun restClient(base: OkHttpClient, callTimeoutMs: Long = 30_000): OkHttpClient =
    base.newBuilder().callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS).build()
