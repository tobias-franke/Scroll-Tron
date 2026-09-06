package de.geek_hub.scroll_tron

actual fun openUrl(url: String) {
    try {
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    } catch (_: Exception) {}
}

actual fun getPlatformScaleFactor(): Float = 1.0f

actual fun isMultiplayerSupported(): Boolean = true

actual fun copyToClipboard(text: String) {
    try {
        val selection = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    } catch (_: Exception) {}
}

actual fun getFromClipboard(onResult: (String?) -> Unit) {
    try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
            val text = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            onResult(text)
            return
        }
    } catch (_: Exception) {}
    onResult(null)
}

actual fun registerClipboardPasteListener(onPaste: (String) -> Unit): () -> Unit = {}