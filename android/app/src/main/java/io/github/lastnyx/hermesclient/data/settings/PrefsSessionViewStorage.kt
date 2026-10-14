package io.github.lastnyx.hermesclient.data.settings

import android.content.Context

class PrefsSessionViewStorage(context: Context) : SessionViewStorage {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    override fun load(name: String): String? = prefs.getString(name, null)
    override fun save(name: String, key: String) { prefs.edit().putString(name, key).apply() }
}
