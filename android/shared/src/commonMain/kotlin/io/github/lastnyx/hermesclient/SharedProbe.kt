package io.github.lastnyx.hermesclient

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Proves commonMain can use JVM-only libraries on both targets (Android and desktop JVM). */
internal fun sharedProbe(url: String): String = JSONObject().put("host", url.toHttpUrl().host).toString()
