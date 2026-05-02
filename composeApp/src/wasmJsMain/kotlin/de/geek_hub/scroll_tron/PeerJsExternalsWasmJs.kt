@file:JsModule("peerjs")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// Kotlin/WasmJS external declarations for PeerJS
// ---------------------------------------------------------------------------

@JsName("Peer")
external class JsPeer : JsAny {
    constructor()
    constructor(id: String)
    constructor(id: String, options: JsAny)

    val id: String
    val open: Boolean
    val disconnected: Boolean
    val destroyed: Boolean

    fun connect(id: String): JsDataConnection
    fun connect(id: String, options: JsAny): JsDataConnection
    fun on(event: String, callback: (JsAny?) -> Unit)
    fun disconnect()
    fun destroy()
    fun reconnect()
}

external interface JsDataConnection : JsAny {
    val peer: String
    val open: Boolean
    val label: String
    val metadata: JsAny?

    fun send(data: JsAny)
    fun close()
    fun on(event: String, callback: (JsAny?) -> Unit)
}
