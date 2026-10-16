package io.github.lastnyx.hermesclient.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64

class TokenStoreTest {
    private class XorCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = encrypt(sealed)
    }

    private class ThrowingCipher : SecretCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(sealed: ByteArray): ByteArray = throw GeneralSecurityException("bad tag")
    }

    private class MemoryStorage : TokenStorage {
        var text: String? = null
        override fun load() = text
        override fun save(text: String?) { this.text = text }
    }

    private val pair = TokenPair("at-1", "rt-1", 1760000000, "basic", "u1")

    @Test
    fun emptyStorageIsNotUnreadable() {
        val store = TokenStore(MemoryStorage(), XorCipher())
        assertNull(store.load("gw-1"))
        assertFalse(store.isUnreadable())
        store.save("gw-1", pair)
        assertEquals(pair, store.load("gw-1"))
        assertFalse(store.isUnreadable())
    }

    @Test
    fun aFailedDecryptIsUnreadableAndTheBlobIsKept() {
        val storage = MemoryStorage()
        TokenStore(storage, XorCipher()).save("gw-1", pair)
        val blob = storage.text
        val store = TokenStore(storage, ThrowingCipher())

        assertNull(store.load("gw-1"))
        assertTrue(store.isUnreadable())
        assertEquals(blob, storage.text)
        store.clear("gw-1")
        assertEquals(blob, storage.text)
    }

    @Test
    fun badBase64AndBadJsonAreUnreadable() {
        val storage = MemoryStorage()
        val store = TokenStore(storage, XorCipher())
        storage.text = "%%%not-base64"
        assertNull(store.load("gw-1"))
        assertTrue(store.isUnreadable())

        storage.text = Base64.getEncoder().encodeToString(XorCipher().encrypt("nope".toByteArray()))
        assertNull(store.load("gw-1"))
        assertTrue(store.isUnreadable())
    }

    @Test
    fun saveOverAnUnreadableBlobReplacesIt() {
        val storage = MemoryStorage()
        TokenStore(storage, XorCipher()).save("gw-1", pair)
        TokenStore(storage, ThrowingCipher()).isUnreadable()
        storage.text = "%%%not-base64"
        val store = TokenStore(storage, XorCipher())
        assertTrue(store.isUnreadable())

        store.save("gw-2", pair)

        assertEquals(pair, store.load("gw-2"))
        assertNull(store.load("gw-1"))
        assertFalse(store.isUnreadable())
    }
}
