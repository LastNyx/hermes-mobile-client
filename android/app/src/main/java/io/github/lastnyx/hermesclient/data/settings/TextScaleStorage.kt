package io.github.lastnyx.hermesclient.data.settings

interface TextScaleStorage {
    fun load(): String?
    fun save(key: String)
}
