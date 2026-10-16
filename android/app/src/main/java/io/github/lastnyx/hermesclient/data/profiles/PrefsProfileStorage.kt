package io.github.lastnyx.hermesclient.data.profiles

import android.content.Context

class PrefsProfileStorage(context: Context) : ProfileStorage {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    override fun load(gatewayId: String): String? = prefs.getString("profile_$gatewayId", null)
    override fun save(gatewayId: String, name: String?) {
        prefs.edit().apply { if (name == null) remove("profile_$gatewayId") else putString("profile_$gatewayId", name) }.apply()
    }
}
