package io.github.lastnyx.hermesclient.ui

import androidx.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTopBarTest {
    @Test
    fun titleUsesTitleSmall() {
        val typography = Typography()
        assertEquals(typography.titleSmall, screenTitleStyle(typography))
    }
}
