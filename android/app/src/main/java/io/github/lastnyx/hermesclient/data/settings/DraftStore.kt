package io.github.lastnyx.hermesclient.data.settings

/** A stored chat and the new-chat composer get separate keys, and so do two gateways. */
fun draftKey(gatewayId: String, storedId: String?): String =
    if (storedId == null) "$gatewayId/new" else "$gatewayId/s/$storedId"

class DraftStore(private val storage: DraftStorage) {
    fun get(key: String): String = storage.load(key) ?: ""

    fun set(key: String, text: String) {
        if (text.isBlank()) storage.clear(key) else storage.save(key, text)
    }
}
