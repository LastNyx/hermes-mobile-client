package io.github.lastnyx.hermesclient.ui

fun modelChipLabel(model: String, effort: String): String = when {
    model.isBlank() -> "Model"
    effort.isBlank() -> model
    else -> "$model · $effort"
}
