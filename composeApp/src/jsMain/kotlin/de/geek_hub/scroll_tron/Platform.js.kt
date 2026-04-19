package de.geek_hub.scroll_tron

actual fun openUrl(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}