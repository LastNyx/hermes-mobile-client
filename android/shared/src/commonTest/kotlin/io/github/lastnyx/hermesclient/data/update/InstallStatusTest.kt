package io.github.lastnyx.hermesclient.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallStatusTest {
    @Test fun failuresBecomePlainSentencesAndOthersAreIgnored() {
        assertNull(installFailureMessage(0))
        assertNull(installFailureMessage(-1))
        assertEquals("Install failed; the update must be signed with the same key", installFailureMessage(1))
        assertEquals("Install was blocked on this device", installFailureMessage(2))
        assertEquals("Install was cancelled", installFailureMessage(3))
        assertEquals("Install failed; the update must be signed with the same key", installFailureMessage(5))
        assertEquals("Not enough storage to install the update", installFailureMessage(6))
        assertEquals("Install failed", installFailureMessage(99))
    }
}
