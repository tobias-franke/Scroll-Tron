package de.geek_hub.scroll_tron

expect fun openUrl(url: String)
expect fun getPlatformScaleFactor(): Float
expect fun isMultiplayerSupported(): Boolean
expect fun copyToClipboard(text: String)
expect fun getFromClipboard(onResult: (String?) -> Unit)
expect fun registerClipboardPasteListener(onPaste: (String) -> Unit): () -> Unit