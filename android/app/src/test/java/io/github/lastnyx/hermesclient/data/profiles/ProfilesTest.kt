package io.github.lastnyx.hermesclient.data.profiles

import io.github.lastnyx.hermesclient.data.auth.AuthedHttp
import io.github.lastnyx.hermesclient.data.auth.SecretCipher
import io.github.lastnyx.hermesclient.data.auth.TokenPair
import io.github.lastnyx.hermesclient.data.auth.TokenStorage
import io.github.lastnyx.hermesclient.data.auth.TokenStore
import io.github.lastnyx.hermesclient.data.sessions.CANT_REACH_GATEWAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private class FakeStorage : ProfileStorage {
        val values = mutableMapOf<String, String>()
        override fun load(gatewayId: String) = values[gatewayId]
        override fun save(gatewayId: String, name: String?) {
            if (name == null) values.remove(gatewayId) else values[gatewayId] = name
        }
    }

    private fun repoOn(gateway: MockWebServer): ProfilesRepository {
        val base = gateway.url("/").toString().trimEnd('/')
        val tokens = TokenStore(MemoryStorage(), XorCipher())
        tokens.save("gw-1", TokenPair("at-1", "rt-1", 1760000000, "basic", "u1"))
        return ProfilesRepository(AuthedHttp(OkHttpClient(), tokens, "gw-1", base, now = { 1000 }), base)
    }

    @Test
    fun parseProfilesReadsOnlyWhatTheListNeeds() {
        val body = JSONObject(
            """{"profiles":[
            {"name":"work","path":"/home/u/.hermes/profiles/work","is_default":false,"model":"m1","provider":"p1","display_name":"Work"},
            {"name":"nameless-skipped-below"},
            {"path":"/x","is_default":false},
            {"name":"default","is_default":true,"display_name":null,"model":null,"provider":null},
            {"name":"blank","display_name":"  "}]}""",
        )
        val items = parseProfiles(body)
        assertEquals(listOf("default", "work", "nameless-skipped-below", "blank"), items.map { it.name })
        assertEquals(ProfileItem("default", "default", true, null, null), items[0])
        assertEquals(ProfileItem("work", "Work", false, "m1", "p1"), items[1])
        assertEquals("blank", items[3].label)
        assertFalse(items.toString().contains("/home/u"))
        assertFalse(items.toString().contains("null\""))
        assertEquals(emptyList<ProfileItem>(), parseProfiles(null))
        assertEquals(emptyList<ProfileItem>(), parseProfiles(JSONObject("{}")))
    }

    @Test
    fun parseActiveAndParamRules() {
        assertEquals("b", parseActiveProfile(JSONObject("""{"active":"a","current":"b"}""")))
        assertNull(parseActiveProfile(JSONObject("{}")))
        assertNull(parseActiveProfile(JSONObject("""{"current":null}""")))
        assertNull(parseActiveProfile(null))
        listOf(null, "", "  ", "default", "Default").forEach { assertNull(profileParam(it)) }
        assertEquals("work", profileParam("work"))
        assertEquals("work", profileParam(" work "))
    }

    @Test
    fun storeKeepsOneChoicePerGateway() {
        val fake = FakeStorage()
        val store = ProfileStore(fake)
        assertNull(store.selected("gw-1"))
        store.select("gw-1", "work")
        assertEquals("work", store.selected("gw-1"))
        assertNull(store.selected("gw-2"))
        store.select("gw-1", null)
        assertNull(store.selected("gw-1"))
        fake.save("gw-1", "default")
        assertNull(store.selected("gw-1"))
    }

    @Test
    fun repositoryAndModelOverTheGateway() {
        val gateway = MockWebServer()
        gateway.start()
        val repo = repoOn(gateway)
        val store = ProfileStore(FakeStorage())
        val model = ProfilesModel(repo, Dispatchers.Unconfined, store, "gw-1")

        gateway.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"secret server detail"}"""))
        runBlocking { model.load() }
        assertEquals("Could not load profiles", model.state.value.error)
        assertEquals("/api/profiles", gateway.takeRequest().path)

        gateway.enqueue(MockResponse().setBody("""{"profiles":[{"name":"default","is_default":true},{"name":"work"}]}"""))
        gateway.enqueue(MockResponse().setBody("""{"active":"default","current":"work"}"""))
        runBlocking { model.load() }
        assertNull(model.state.value.error)
        assertEquals(listOf("default", "work"), model.state.value.profiles.map { it.name })
        assertEquals("work", model.state.value.current)

        model.select(model.state.value.profiles[1])
        assertEquals("work", model.state.value.selected)
        assertEquals("work", store.selected("gw-1"))
        model.select(model.state.value.profiles[0])
        assertNull(model.state.value.selected)
        assertNull(store.selected("gw-1"))

        gateway.takeRequest()
        gateway.takeRequest()
        assertEquals(0, gateway.requestCount - 3)
        gateway.shutdown()
    }

    @Test
    fun droppedConnectionGivesTheFixedGatewayText() {
        val gateway = MockWebServer()
        gateway.start()
        val repo = repoOn(gateway)
        gateway.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        gateway.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val failure = repo.list().exceptionOrNull()
        assertTrue(failure != null)
        assertEquals(CANT_REACH_GATEWAY, failure!!.message)
        gateway.shutdown()
    }
}
