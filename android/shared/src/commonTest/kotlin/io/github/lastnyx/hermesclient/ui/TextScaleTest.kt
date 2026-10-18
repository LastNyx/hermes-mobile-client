package io.github.lastnyx.hermesclient.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.lastnyx.hermesclient.data.settings.TextScaleStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {
    @Test
    fun fromKeyMapsKeysAndFallsBackToDefault() {
        assertEquals(TextScale.Small, TextScale.fromKey("small"))
        assertEquals(TextScale.Default, TextScale.fromKey("default"))
        assertEquals(TextScale.Large, TextScale.fromKey("large"))
        assertEquals(TextScale.ExtraLarge, TextScale.fromKey("xlarge"))
        assertEquals(TextScale.Default, TextScale.fromKey(null))
        assertEquals(TextScale.Default, TextScale.fromKey("huge"))
    }

    @Test
    fun scaledTypographyMultipliesSizesAndKeepsUnspecified() {
        val base = Typography(bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 40.sp))
        val large = scaledTypography(base, TextScale.Large)
        assertEquals(23.sp, large.bodyMedium.fontSize)
        assertEquals(46.sp, large.bodyMedium.lineHeight)
        assertEquals(base.headlineMedium.fontSize.value * 1.15f, large.headlineMedium.fontSize.value, 0.001f)
        assertEquals(base.headlineMedium.lineHeight.value * 1.15f, large.headlineMedium.lineHeight.value, 0.001f)

        val plain = Typography(bodySmall = TextStyle(fontSize = TextUnit.Unspecified))
        assertEquals(TextUnit.Unspecified, scaledTypography(plain, TextScale.ExtraLarge).bodySmall.fontSize)
        assertEquals(base, scaledTypography(base, TextScale.Default))
    }

    @Test
    fun storeDefaultsAndReturnsWhatWasSet() {
        val fake = object : TextScaleStorage {
            var value: String? = null
            override fun load() = value
            override fun save(key: String) { value = key }
        }
        val store = TextScaleStore(fake)
        assertEquals(TextScale.Default, store.current())
        store.set(TextScale.Large)
        assertEquals("large", fake.value)
        assertEquals(TextScale.Large, TextScaleStore(fake).current())
    }
}
