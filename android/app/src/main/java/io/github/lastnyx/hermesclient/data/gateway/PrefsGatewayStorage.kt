package io.github.lastnyx.hermesclient.data.gateway

import android.content.Context

class PrefsGatewayStorage(context: Context) : GatewayStorage {
    private val prefs = context.getSharedPreferences("gateway_registry", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("registry", null)
    override fun save(text: String) { prefs.edit().putString("registry", text).apply() }
}
