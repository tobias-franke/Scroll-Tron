package de.geek_hub.scroll_tron

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("function(url) { window.open(url, '_blank'); }")
private external fun jsOpenUrl(url: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun openUrl(url: String) = jsOpenUrl(url)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("function() { return window.self !== window.top ? 2.0 : 1.0; }")
private external fun jsGetPlatformScaleFactor(): Float

actual fun getPlatformScaleFactor(): Float = jsGetPlatformScaleFactor()

actual fun isMultiplayerSupported(): Boolean = false