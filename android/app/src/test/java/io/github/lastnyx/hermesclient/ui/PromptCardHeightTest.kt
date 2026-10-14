package io.github.lastnyx.hermesclient.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptCardHeightTest {
    @Test
    fun promptBodyIsCappedAtSixtyPercentOfTheScreen() {
        assertEquals(480.dp, promptBodyMaxHeight(800.dp))
    }
}
