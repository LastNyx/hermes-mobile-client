package io.github.lastnyx.hermesclient.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

const val WINDOW_TITLE = "Hermes"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = WINDOW_TITLE) {
        MaterialTheme { Surface {} }
    }
}
