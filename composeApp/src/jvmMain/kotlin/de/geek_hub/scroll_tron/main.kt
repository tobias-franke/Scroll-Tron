package de.geek_hub.scroll_tron

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Scroll Tron",
    ) {
        App()
    }
}