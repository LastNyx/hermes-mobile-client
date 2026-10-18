package io.github.lastnyx.hermesclient.data.gateway

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Gateway(val id: String, val name: String, val url: String)

interface GatewayStorage {
    fun load(): String?
    fun save(text: String)
}

/** The registry holds no secrets; tokens live elsewhere. */
class GatewayRegistry(private val storage: GatewayStorage) {
    private var gateways = mutableListOf<Gateway>()
    private var activeId: String? = null

    init {
        storage.load()?.let { text ->
            runCatching {
                val root = JSONObject(text)
                val items = root.getJSONArray("gateways")
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    gateways += Gateway(o.getString("id"), o.getString("name"), o.getString("url"))
                }
                activeId = root.optString("active").ifEmpty { null }
            }
        }
    }

    fun list(): List<Gateway> = gateways.toList()

    fun active(): Gateway? = gateways.firstOrNull { it.id == activeId }

    fun add(name: String, url: String): Result<Gateway> = validate(null, name, url).mapCatching { (n, u) ->
        Gateway(UUID.randomUUID().toString(), n, u).also { gateways += it; persist() }
    }

    fun update(id: String, name: String, url: String): Result<Gateway> = validate(id, name, url).mapCatching { (n, u) ->
        val index = gateways.indexOfFirst { it.id == id }
        require(index >= 0) { "Gateway no longer exists" }
        gateways[index].copy(name = n, url = u).also { gateways[index] = it; persist() }
    }

    fun remove(id: String) {
        gateways.removeAll { it.id == id }
        if (activeId == id) activeId = null
        persist()
    }

    fun setActive(id: String) {
        if (gateways.any { it.id == id }) { activeId = id; persist() }
    }

    private fun validate(selfId: String?, rawName: String, rawUrl: String): Result<Pair<String, String>> {
        val name = rawName.trim()
        val others = gateways.filter { it.id != selfId }
        val reason = when {
            name.isEmpty() -> "Name is required"
            name.length > 64 -> "Name is longer than 64 characters"
            others.any { it.name.equals(name, ignoreCase = true) } -> "Name is already used"
            else -> null
        }
        if (reason != null) return Result.failure(IllegalArgumentException(reason))
        val url = GatewayUrl.normalise(rawUrl)
            ?: return Result.failure(IllegalArgumentException("Enter a full http:// or https:// URL"))
        if (others.any { it.url == url }) return Result.failure(IllegalArgumentException("URL is already registered"))
        return Result.success(name to url)
    }

    private fun persist() {
        val items = JSONArray()
        gateways.forEach { items.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) }
        storage.save(JSONObject().put("gateways", items).put("active", activeId ?: "").toString())
    }
}
