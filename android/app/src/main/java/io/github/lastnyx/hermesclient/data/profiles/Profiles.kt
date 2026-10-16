package io.github.lastnyx.hermesclient.data.profiles

import org.json.JSONObject

/** [label] is what the picker shows; the host path of a profile is deliberately never read. */
data class ProfileItem(
    val name: String,
    val label: String,
    val isDefault: Boolean,
    val model: String?,
    val provider: String?,
)

private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else (opt(key) as? String)?.trim()?.ifEmpty { null }

fun parseProfiles(body: JSONObject?): List<ProfileItem> {
    val rows = body?.optJSONArray("profiles") ?: return emptyList()
    val items = (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val name = row.text("name") ?: return@mapNotNull null
        ProfileItem(
            name = name,
            label = row.text("display_name") ?: name,
            isDefault = row.optBoolean("is_default", false),
            model = row.text("model"),
            provider = row.text("provider"),
        )
    }
    return items.filter { it.isDefault } + items.filterNot { it.isDefault }
}

/** The profile the gateway process itself runs as. */
fun parseActiveProfile(body: JSONObject?): String? = body?.text("current")

/** Null means "send nothing": the gateway then uses its own default profile. */
fun profileParam(selected: String?): String? =
    selected?.trim()?.takeIf { it.isNotEmpty() && !it.equals("default", ignoreCase = true) }

interface ProfileStorage {
    fun load(gatewayId: String): String?
    fun save(gatewayId: String, name: String?)
}

class ProfileStore(private val storage: ProfileStorage) {
    fun selected(gatewayId: String): String? = profileParam(storage.load(gatewayId))
    fun select(gatewayId: String, name: String?) = storage.save(gatewayId, profileParam(name))
}
