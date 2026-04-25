package de.geek_hub.scroll_tron

actual fun openUrl(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {}
}

actual fun getPlatformScaleFactor(): Float = 1.0f