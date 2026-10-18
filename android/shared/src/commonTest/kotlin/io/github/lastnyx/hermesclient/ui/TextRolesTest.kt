package io.github.lastnyx.hermesclient.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class TextRolesTest {
    @Test
    fun rolesMapToTypographyAndCodeSharesOneMonospace() {
        val t = Typography()
        val roles = TextRoles(t)
        assertEquals(t.bodyMedium, roles.body)
        assertEquals(t.bodySmall, roles.secondary)
        assertEquals(t.labelLarge, roles.label)
        assertEquals(t.labelSmall, roles.caption)
        assertEquals(t.titleSmall, roles.title)
        assertEquals(t.bodySmall.copy(fontFamily = FontFamily.Monospace), roles.code)
        assertEquals(roles.code, roles.monospace(t.bodySmall))
    }
}
