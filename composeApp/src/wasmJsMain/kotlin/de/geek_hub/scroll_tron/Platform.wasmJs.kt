package de.geek_hub.scroll_tron

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("function(url) { window.open(url, '_blank'); }")
private external fun jsOpenUrl(url: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun openUrl(url: String) = jsOpenUrl(url)