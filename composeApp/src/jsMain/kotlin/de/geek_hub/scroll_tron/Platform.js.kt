package de.geek_hub.scroll_tron

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun openUrl(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}