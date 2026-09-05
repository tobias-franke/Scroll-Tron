package de.geek_hub.scroll_tron

actual fun openUrl(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}

actual fun getPlatformScaleFactor(): Float {
    return if (kotlinx.browser.window.self !== kotlinx.browser.window.top) 2.0f else 1.0f
}

actual fun isMultiplayerSupported(): Boolean = true

actual fun copyToClipboard(text: String) {
    try {
        val nav = kotlinx.browser.window.navigator.asDynamic()
        if (nav != null && nav.clipboard != null && nav.clipboard.writeText != null) {
            nav.clipboard.writeText(text).catch {
                fallbackJsCopy(text)
            }
        } else {
            fallbackJsCopy(text)
        }
    } catch (_: Throwable) {
        fallbackJsCopy(text)
    }
}

private fun fallbackJsCopy(text: String) {
    try {
        val doc = kotlinx.browser.document
        val textArea = doc.createElement("textarea") as org.w3c.dom.HTMLTextAreaElement
        textArea.value = text
        textArea.style.position = "fixed"
        textArea.style.left = "-9999px"
        doc.body?.appendChild(textArea)
        textArea.focus()
        textArea.select()
        doc.execCommand("copy")
        doc.body?.removeChild(textArea)
    } catch (_: Throwable) {}
}

actual fun getFromClipboard(onResult: (String?) -> Unit) {
    try {
        val nav = kotlinx.browser.window.navigator.asDynamic()
        if (nav != null && nav.clipboard != null && nav.clipboard.readText != null) {
            nav.clipboard.readText()
                .then { text: String -> onResult(text) }
                .catch { onResult(null) }
        } else {
            onResult(null)
        }
    } catch (_: Throwable) {
        onResult(null)
    }
}

actual fun registerClipboardPasteListener(onPaste: (String) -> Unit): () -> Unit {
    val listener: (org.w3c.dom.events.Event) -> Unit = { event ->
        try {
            val clipEvent = event.asDynamic()
            val text = clipEvent.clipboardData?.getData("text") as? String
                ?: clipEvent.clipboardData?.getData("text/plain") as? String
            if (!text.isNullOrBlank()) {
                onPaste(text)
            }
        } catch (_: Throwable) {}
    }
    kotlinx.browser.window.addEventListener("paste", listener)
    return {
        kotlinx.browser.window.removeEventListener("paste", listener)
    }
}