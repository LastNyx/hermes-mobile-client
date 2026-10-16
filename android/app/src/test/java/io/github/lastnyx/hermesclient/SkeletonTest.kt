package io.github.lastnyx.hermesclient

import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves the JVM unit-test pipeline runs; real tests arrive with the tickets. */
class SkeletonTest {
    @Test fun releasesRepoIsThisRepo() {
        assertEquals("LastNyx/hermes-mobile-client", BuildConfig.RELEASES_REPO)
    }
}
