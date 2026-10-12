package io.github.lastnyx.hermesclient.data.gateway

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRegistryTest {
    private class MemoryStorage : GatewayStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String) { this.text = text }
    }

    @Test fun registryPersistsAndRejectsAndStatusReportsVersion() {
        val storage = MemoryStorage()
        val registry = GatewayRegistry(storage)

        val home = registry.add("Home", "HTTPS://Hermes.Example.com/prefix//").getOrThrow()
        assertEquals("https://hermes.example.com/prefix", home.url)
        assertTrue(registry.add("Dup", " https://hermes.example.com/prefix/ ").isFailure)
        assertTrue(registry.add("home", "https://other.example.com").isFailure)
        assertTrue(registry.add("", "https://other.example.com").isFailure)
        assertTrue(registry.add("x".repeat(65), "https://other.example.com").isFailure)

        val lab = registry.add("Lab", "http://100.65.118.116:9119").getOrThrow()
        registry.setActive(lab.id)
        registry.update(home.id, "Home2", "https://hermes.example.com/prefix").getOrThrow()

        val fresh = GatewayRegistry(storage)
        assertEquals(listOf("Home2", "Lab"), fresh.list().map { it.name })
        assertEquals(lab.id, fresh.active()?.id)
        fresh.remove(lab.id)
        assertEquals(null, GatewayRegistry(storage).active())

        assertTrue(GatewayUrl.warnsCleartext("http://example.com"))
        assertTrue(GatewayUrl.warnsCleartext("http://192.168.1.5"))
        assertFalse(GatewayUrl.warnsCleartext("https://example.com"))
        assertFalse(GatewayUrl.warnsCleartext("http://127.0.0.1"))
        assertFalse(GatewayUrl.warnsCleartext("http://100.65.118.116:9119"))

        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"version":"0.13.2","auth_required":true}"""))
            server.enqueue(MockResponse().setBody("not json"))
            server.start()
            val client = GatewayClient(OkHttpClient())
            val base = server.url("/").toString()
            assertEquals("0.13.2", client.status(base).getOrThrow().version)
            assertEquals("/api/status", server.takeRequest().path)
            assertTrue(client.status(base).isFailure)
        }
        assertTrue(GatewayClient(OkHttpClient()).status("http://127.0.0.1:1").isFailure)
    }
}
