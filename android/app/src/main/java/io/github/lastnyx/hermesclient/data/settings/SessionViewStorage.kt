package io.github.lastnyx.hermesclient.data.settings

interface SessionViewStorage {
    fun load(name: String): String?
    fun save(name: String, key: String)
}
