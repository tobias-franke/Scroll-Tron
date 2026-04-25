package de.geek_hub.scroll_tron

actual fun openUrl(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}

actual fun getPlatformScaleFactor(): Float {
    return if (kotlinx.browser.window.self !== kotlinx.browser.window.top) 2.0f else 1.0f
}