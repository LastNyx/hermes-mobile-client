package io.github.lastnyx.hermesclient.data.capabilities

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.JSON_MEDIA
import io.github.lastnyx.hermesclient.data.gatewayCall
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Skills, toolsets and MCP servers of one gateway over REST. Blocking; call off the main thread. */
class CapabilitiesRepository(private val http: AuthedHttp, baseUrl: String) {
    private val base = baseUrl.trimEnd('/').toHttpUrl()

    fun skills(): Result<List<SkillItem>> = call(get("api", "skills")) { parseSkills(JSONArray(it)) }

    fun toolsets(): Result<List<ToolsetItem>> = call(get("api", "tools", "toolsets")) { parseToolsets(JSONArray(it)) }

    fun mcpServers(): Result<List<McpServerItem>> = call(get("api", "mcp", "servers")) { parseMcpServers(JSONObject(it)) }

    fun setSkill(name: String, enabled: Boolean): Result<Unit> =
        put(url("api", "skills", "toggle"), JSONObject().put("name", name).put("enabled", enabled))

    fun setToolset(name: String, enabled: Boolean): Result<Unit> =
        put(url("api", "tools", "toolsets", name), JSONObject().put("enabled", enabled))

    fun setMcp(name: String, enabled: Boolean): Result<Unit> =
        put(url("api", "mcp", "servers", name, "enabled"), JSONObject().put("enabled", enabled))

    fun testMcp(name: String): Result<McpTestResult> = call(
        Request.Builder().url(url("api", "mcp", "servers", name, "test")).post("".toRequestBody()).build(),
    ) { parseMcpTest(try { JSONObject(it) } catch (e: Exception) { null }) }

    // addPathSegment encodes each name, so a slash or space cannot split the path.
    private fun url(vararg segments: String): HttpUrl =
        base.newBuilder().also { b -> segments.forEach { b.addPathSegment(it) } }.build()

    private fun get(vararg segments: String) = Request.Builder().url(url(*segments)).build()

    private fun put(url: HttpUrl, body: JSONObject): Result<Unit> =
        call(Request.Builder().url(url).put(body.toString().toRequestBody(JSON_MEDIA)).build()) { }

    private fun <T> call(request: Request, map: (String) -> T): Result<T> = gatewayCall(http, request, map)
}
