package io.github.lastnyx.hermesclient.data.settings

import android.content.Context

class PrefsDraftStorage(context: Context) : DraftStorage {
    private val prefs = context.getSharedPreferences("composer_drafts", Context.MODE_PRIVATE)
    override fun load(key: String): String? = prefs.getString(key, null)
    override fun save(key: String, text: String) { prefs.edit().putString(key, text).apply() }
    override fun clear(key: String) { prefs.edit().remove(key).apply() }
}
