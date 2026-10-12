package io.github.lastnyx.hermesclient

import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves the JVM unit-test pipeline runs; real tests arrive with the tickets. */
class SkeletonTest {
    @Test fun releasesRepoIsTheOpenOne() {
        assertEquals("LastNyx/hermes-mobile-client-releases", BuildConfig.RELEASES_REPO)
    }
}
