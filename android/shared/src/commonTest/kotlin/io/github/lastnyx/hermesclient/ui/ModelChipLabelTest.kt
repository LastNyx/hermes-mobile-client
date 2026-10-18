package io.github.lastnyx.hermesclient.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelChipLabelTest {
    @Test
    fun labelShowsModelThenEffortWhenPresent() {
        assertEquals("sonnet · high", modelChipLabel("sonnet", "high"))
        assertEquals("sonnet", modelChipLabel("sonnet", ""))
        assertEquals("Model", modelChipLabel("", "high"))
    }
}
