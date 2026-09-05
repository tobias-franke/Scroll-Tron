@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
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

actual fun isMultiplayerSupported(): Boolean = true

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function(text) {
    if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).catch(function() {
            var ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.left = '-9999px';
            document.body.appendChild(ta);
            ta.focus();
            ta.select();
            try { document.execCommand('copy'); } catch(e) {}
            document.body.removeChild(ta);
        });
    } else if (typeof document !== 'undefined') {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        try { document.execCommand('copy'); } catch(e) {}
        document.body.removeChild(ta);
    }
}
""")
private external fun jsCopyToClipboard(text: String)

actual fun copyToClipboard(text: String) {
    try {
        jsCopyToClipboard(text)
    } catch (_: Throwable) {}
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("function() { return (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.readText) ? navigator.clipboard.readText() : null; }")
private external fun jsReadClipboardPromise(): kotlin.js.Promise<kotlin.js.JsString>?

actual fun getFromClipboard(onResult: (String?) -> Unit) {
    try {
        val promise = jsReadClipboardPromise()
        if (promise != null) {
            promise.then { jsStr ->
                onResult(jsStr.toString())
                null
            }.catch {
                onResult(null)
                null
            }
        } else {
            onResult(null)
        }
    } catch (_: Throwable) {
        onResult(null)
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("function(event) { var cd = event.clipboardData || window.clipboardData; return cd ? (cd.getData('text') || cd.getData('text/plain') || null) : null; }")
private external fun jsGetClipboardData(event: org.w3c.dom.events.Event): String?

actual fun registerClipboardPasteListener(onPaste: (String) -> Unit): () -> Unit {
    val listener: (org.w3c.dom.events.Event) -> Unit = { event ->
        try {
            val text = jsGetClipboardData(event)
            if (!text.isNullOrBlank()) {
                onPaste(text)
            }
        } catch (_: Throwable) {}
    }
    try {
        kotlinx.browser.window.addEventListener("paste", listener)
        return {
            try {
                kotlinx.browser.window.removeEventListener("paste", listener)
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {
        return {}
    }
}