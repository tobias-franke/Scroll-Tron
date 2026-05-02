@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.geek_hub.scroll_tron

import kotlin.random.Random

// Helper JS functions for WasmJS interop
@JsFun("function() { return {}; }")
internal external fun createJsObject(): JsAny

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsString(obj: JsAny, key: String, value: String)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsFloat(obj: JsAny, key: String, value: Float)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsBoolean(obj: JsAny, key: String, value: Boolean)

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsInt(obj: JsAny, key: String, value: Int)

@JsFun("function(obj, key) { return obj[key] != null ? String(obj[key]) : null; }")
internal external fun getJsString(obj: JsAny, key: String): String?

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsFloat(obj: JsAny, key: String): Float

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsBoolean(obj: JsAny, key: String): Boolean

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsInt(obj: JsAny, key: String): Int

@JsFun("function() { return window.performance.now(); }")
internal external fun performanceNow(): Float

@JsFun("function(message) { console.log(message); }")
internal external fun consoleLog(message: String)

@JsFun("function(obj) { return obj.type ? obj.type.toString() : obj.toString(); }")
internal external fun getErrorString(obj: JsAny): String

object MessageType {
    const val GAME_START   = "gameStart"
    const val PLAYER_INPUT = "playerInput"
    const val GAME_SYNC    = "gameSync"
    const val GAME_OVER    = "gameOver"
    const val REMATCH      = "rematch"
}

enum class ConnectionState {
    Idle,
    WaitingForGuest,
    Connecting,
    Connected,
    Error,
}

class WasmJsNetworkManager {

    var state: ConnectionState = ConnectionState.Idle
        private set

    var errorMessage: String? = null
        private set

    var roomCode: String = ""
        private set

    private var peer: JsPeer? = null
    private var connection: JsDataConnection? = null

    // Callbacks set by the lobby/game composables
    var onStateChanged: ((ConnectionState) -> Unit)? = null
    var onMessageReceived: ((type: String, data: JsAny) -> Unit)? = null

    // -----------------------------------------------------------------------
    // Host flow
    // -----------------------------------------------------------------------

    fun hostGame() {
        val code = generateRoomCode()
        roomCode = code
        val peerId = "STRON-$code"
        updateState(ConnectionState.WaitingForGuest)

        try {
            peer = JsPeer(peerId)
        } catch (e: Throwable) {
            updateState(ConnectionState.Error)
            errorMessage = "Failed to create peer: ${e.message}"
            return
        }

        peer!!.on("open") { _ ->
            consoleLog("Host peer registered as: $peerId")
        }

        peer!!.on("connection") { connAny ->
            val dataConn = connAny!!.unsafeCast<JsDataConnection>()
            connection = dataConn
            setupDataConnection(dataConn)
        }

        peer!!.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("PeerJS host error: $errStr")
            errorMessage = "Connection error: $errStr"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Join flow
    // -----------------------------------------------------------------------

    fun joinGame(code: String) {
        roomCode = code.uppercase()
        val peerId = "STRON-${roomCode}"
        updateState(ConnectionState.Connecting)

        try {
            peer = JsPeer()
        } catch (e: Throwable) {
            updateState(ConnectionState.Error)
            errorMessage = "Failed to create peer: ${e.message}"
            return
        }

        peer!!.on("open") { _ ->
            consoleLog("Guest peer open, connecting to host: $peerId")
            val conn = peer!!.connect(peerId)
            connection = conn
            setupDataConnection(conn)
        }

        peer!!.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("PeerJS guest error: $errStr")
            errorMessage = "Connection error: $errStr"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Data channel setup
    // -----------------------------------------------------------------------

    private fun setupDataConnection(conn: JsDataConnection) {
        conn.on("open") { _ ->
            consoleLog("Data channel open with peer: ${conn.peer}")
            updateState(ConnectionState.Connected)
        }

        conn.on("data") { dataAny ->
            if (dataAny == null) return@on
            val type = getJsString(dataAny, "type") ?: return@on
            onMessageReceived?.invoke(type, dataAny)
        }

        conn.on("close") { _ ->
            consoleLog("Data channel closed")
            updateState(ConnectionState.Idle)
        }

        conn.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("Data channel error: $errStr")
            errorMessage = "Data channel error: $errStr"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Send messages
    // -----------------------------------------------------------------------

    fun send(message: JsAny) {
        connection?.send(message)
    }

    fun sendPlayerInput(angularVelocity: Float) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.PLAYER_INPUT)
        setJsFloat(msg, "angularVelocity", angularVelocity)
        send(msg)
    }

    fun sendGameSync(
        p1X: Float, p1Y: Float, p1Angle: Float, p1AngVel: Float, p1Dead: Boolean,
        p2X: Float, p2Y: Float, p2Angle: Float, p2AngVel: Float, p2Dead: Boolean,
    ) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.GAME_SYNC)
        setJsFloat(msg, "p1X", p1X); setJsFloat(msg, "p1Y", p1Y); setJsFloat(msg, "p1Angle", p1Angle); setJsFloat(msg, "p1AngVel", p1AngVel); setJsBoolean(msg, "p1Dead", p1Dead)
        setJsFloat(msg, "p2X", p2X); setJsFloat(msg, "p2Y", p2Y); setJsFloat(msg, "p2Angle", p2Angle); setJsFloat(msg, "p2AngVel", p2AngVel); setJsBoolean(msg, "p2Dead", p2Dead)
        setJsFloat(msg, "timestamp", performanceNow())
        send(msg)
    }

    fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.GAME_START)
        setJsFloat(msg, "canvasWidth", canvasWidth)
        setJsFloat(msg, "canvasHeight", canvasHeight)
        send(msg)
    }

    fun sendGameOver(winnerId: Int) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.GAME_OVER)
        setJsInt(msg, "winner", winnerId)
        send(msg)
    }

    fun sendRematch() {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.REMATCH)
        send(msg)
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    fun disconnect() {
        connection?.close()
        connection = null
        peer?.destroy()
        peer = null
        updateState(ConnectionState.Idle)
        errorMessage = null
        roomCode = ""
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun updateState(newState: ConnectionState) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  // no I/O/0/1 to avoid confusion
        return (1..4).map { chars.random() }.joinToString("")
    }
}
