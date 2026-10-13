package io.github.lastnyx.hermesclient.data.settings

import android.content.Context
import io.github.lastnyx.hermesclient.ui.TextScaleStorage

class PrefsTextScaleStorage(context: Context) : TextScaleStorage {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    override fun load(): String? = prefs.getString("text_scale", null)
    override fun save(key: String) { prefs.edit().putString("text_scale", key).apply() }
}
