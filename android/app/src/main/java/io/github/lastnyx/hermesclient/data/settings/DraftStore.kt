package io.github.lastnyx.hermesclient.data.settings

/** A stored chat and the new-chat composer get separate keys, and so do two gateways. */
fun draftKey(gatewayId: String, storedId: String?): String =
    if (storedId == null) "$gatewayId/new" else "$gatewayId/s/$storedId"

/** The latest composer text together with the key it was typed under, so a key switch cannot mix them. */
class DraftTracker(var key: String, var text: String) {
    fun track(key: String, text: String) {
        this.key = key
        this.text = text
    }
}

fun DraftStore.flush(tracked: DraftTracker) = set(tracked.key, tracked.text)

class DraftStore(private val storage: DraftStorage) {
    fun get(key: String): String = storage.load(key) ?: ""

    fun set(key: String, text: String) {
        if (text.isBlank()) storage.clear(key) else storage.save(key, text)
    }
}
