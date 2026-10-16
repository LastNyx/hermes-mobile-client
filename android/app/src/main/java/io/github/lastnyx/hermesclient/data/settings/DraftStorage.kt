package io.github.lastnyx.hermesclient.data.settings

interface DraftStorage {
    fun load(key: String): String?
    fun save(key: String, text: String)
    fun clear(key: String)
}
