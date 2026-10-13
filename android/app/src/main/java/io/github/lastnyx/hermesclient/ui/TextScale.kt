package io.github.lastnyx.hermesclient.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

enum class TextScale(val key: String, val factor: Float, val label: String) {
    Small("small", 0.9f, "Small"),
    Default("default", 1.0f, "Default"),
    Large("large", 1.15f, "Large"),
    ExtraLarge("xlarge", 1.3f, "Extra large");

    companion object {
        fun fromKey(key: String?): TextScale = entries.firstOrNull { it.key == key } ?: Default
    }
}

interface TextScaleStorage {
    fun load(): String?
    fun save(key: String)
}

class TextScaleStore(private val storage: TextScaleStorage) {
    fun current(): TextScale = TextScale.fromKey(storage.load())
    fun set(scale: TextScale) = storage.save(scale.key)
}

fun scaledTypography(base: Typography, scale: TextScale): Typography = base.copy(
    displayLarge = base.displayLarge.scaled(scale),
    displayMedium = base.displayMedium.scaled(scale),
    displaySmall = base.displaySmall.scaled(scale),
    headlineLarge = base.headlineLarge.scaled(scale),
    headlineMedium = base.headlineMedium.scaled(scale),
    headlineSmall = base.headlineSmall.scaled(scale),
    titleLarge = base.titleLarge.scaled(scale),
    titleMedium = base.titleMedium.scaled(scale),
    titleSmall = base.titleSmall.scaled(scale),
    bodyLarge = base.bodyLarge.scaled(scale),
    bodyMedium = base.bodyMedium.scaled(scale),
    bodySmall = base.bodySmall.scaled(scale),
    labelLarge = base.labelLarge.scaled(scale),
    labelMedium = base.labelMedium.scaled(scale),
    labelSmall = base.labelSmall.scaled(scale),
)

private fun TextStyle.scaled(scale: TextScale): TextStyle =
    copy(fontSize = fontSize.scaled(scale), lineHeight = lineHeight.scaled(scale))

private fun TextUnit.scaled(scale: TextScale): TextUnit = if (isSpecified) this * scale.factor else this
