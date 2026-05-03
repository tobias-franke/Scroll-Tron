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

@JsFun("function(obj, key, value) { obj[key] = value; }")
internal external fun setJsAny(obj: JsAny, key: String, value: JsAny)

@JsFun("function() { return []; }")
internal external fun createJsArray(): JsAny

@JsFun("function(arr, item) { arr.push(item); }")
internal external fun pushJsArray(arr: JsAny, item: JsAny)

@JsFun("function(arr) { return arr.length; }")
internal external fun getJsArrayLength(arr: JsAny): Int

@JsFun("function(arr, index) { return arr[index]; }")
internal external fun getJsArrayItem(arr: JsAny, index: Int): JsAny

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

    val numConnections: Int
        get() = connections.size

    val isGuest: Boolean
        get() = hostConnection != null

    private var peer: JsPeer? = null
    internal val connections = mutableMapOf<Int, JsDataConnection>() // Player index -> Connection
    internal var hostConnection: JsDataConnection? = null // For guests: connection to host

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
            if (connections.size >= 3) {
                consoleLog("Maximum 4 players reached, rejecting connection from ${dataConn.peer}")
                dataConn.close()
                return@on
            }
            val playerIndex = connections.size + 1
            connections[playerIndex] = dataConn
            setupDataConnection(dataConn, playerIndex)
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
            hostConnection = conn
            setupDataConnection(conn, -1) // playerIndex -1 means we are a guest
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

    private fun setupDataConnection(conn: JsDataConnection, playerIndex: Int) {
        conn.on("open") { _ ->
            consoleLog("Data channel open with peer: ${conn.peer} (Player $playerIndex)")
            updateState(ConnectionState.Connected)
        }

        conn.on("data") { dataAny ->
            if (dataAny == null) return@on
            val type = getJsString(dataAny, "type") ?: return@on
            
            // If we are host and received input, we need to know which player it is
            if (playerIndex != -1) {
                setJsInt(dataAny, "playerIndex", playerIndex)
                setJsBoolean(dataAny, "hasPlayerIndex", true)
                
                // Host broadcasts rematch requests to all other guests
                if (type == MessageType.REMATCH) {
                    connections.values.forEach { it.send(dataAny) }
                }
            }

            onMessageReceived?.invoke(type, dataAny)
        }

        conn.on("close") { _ ->
            consoleLog("Data channel closed for Player $playerIndex")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                if (connections.isEmpty()) updateState(ConnectionState.WaitingForGuest)
            } else {
                updateState(ConnectionState.Idle)
            }
        }

        conn.on("error") { errAny ->
            val errStr = if (errAny != null) getErrorString(errAny) else "Unknown error"
            consoleLog("Data channel error (Player $playerIndex): $errStr")
            errorMessage = "Data channel error: $errStr"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Send messages
    // -----------------------------------------------------------------------

    fun send(message: JsAny) {
        if (hostConnection != null) {
            hostConnection?.send(message)
        } else {
            // Broadcast to all guests
            connections.values.forEach { it.send(message) }
        }
    }

    fun sendPlayerInput(playerIndex: Int, angularVelocity: Float) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.PLAYER_INPUT)
        setJsInt(msg, "playerIndex", playerIndex)
        setJsFloat(msg, "angularVelocity", angularVelocity)
        send(msg)
    }

    fun sendGameSync(players: List<PlayerSyncData>) {
        val msg = createJsObject()
        setJsString(msg, "type", MessageType.GAME_SYNC)
        
        val playersArr = createJsArray()
        players.forEach { p ->
            val pObj = createJsObject()
            setJsFloat(pObj, "x", p.x)
            setJsFloat(pObj, "y", p.y)
            setJsFloat(pObj, "angle", p.angle)
            setJsFloat(pObj, "angVel", p.angVel)
            setJsBoolean(pObj, "isDead", p.isDead)
            pushJsArray(playersArr, pObj)
        }
        
        // Use property name "players" for the array
        setJsAny(msg, "players", playersArr)
        setJsFloat(msg, "timestamp", performanceNow())
        send(msg)
    }

    fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        // Host sends to each guest their player index
        connections.forEach { (index, conn) ->
            val msg = createJsObject()
            setJsString(msg, "type", MessageType.GAME_START)
            setJsFloat(msg, "canvasWidth", canvasWidth)
            setJsFloat(msg, "canvasHeight", canvasHeight)
            setJsInt(msg, "playerIndex", index)
            conn.send(msg)
        }
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
        hostConnection?.close()
        hostConnection = null
        connections.values.toList().forEach { it.close() }
        connections.clear()
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
