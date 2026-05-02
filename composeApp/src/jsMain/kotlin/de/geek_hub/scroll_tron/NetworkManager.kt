package de.geek_hub.scroll_tron

import kotlinx.browser.window

// ---------------------------------------------------------------------------
// Network message types (serialised as JSON over PeerJS data channel)
// ---------------------------------------------------------------------------

// Messages are simple JSON objects with a "type" field for dispatch.
// We use dynamic JS objects for simplicity and performance — avoiding
// heavy serialisation libraries for this latency-sensitive game data.

object MessageType {
    const val GAME_START   = "gameStart"
    const val PLAYER_INPUT = "playerInput"
    const val GAME_SYNC    = "gameSync"
    const val GAME_OVER    = "gameOver"
    const val REMATCH      = "rematch"
}

// ---------------------------------------------------------------------------
// Connection state
// ---------------------------------------------------------------------------

enum class ConnectionState {
    Idle,
    WaitingForGuest,  // host: peer registered, waiting for incoming connection
    Connecting,       // guest: connecting to host
    Connected,        // data channel is open
    Error,
}

// ---------------------------------------------------------------------------
// Network Manager — wraps PeerJS for the multiplayer connection lifecycle
// ---------------------------------------------------------------------------

class NetworkManager {

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
    var onMessageReceived: ((type: String, data: dynamic) -> Unit)? = null

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
            console.log("Host peer registered as: $peerId")
        }

        peer!!.on("connection") { conn ->
            val dataConn = conn.unsafeCast<JsDataConnection>()
            connection = dataConn
            setupDataConnection(dataConn)
        }

        peer!!.on("error") { err ->
            console.log("PeerJS host error: $err")
            val errMsg = err.asDynamic().type?.toString() ?: err.toString()
            errorMessage = "Connection error: $errMsg"
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
            console.log("Guest peer open, connecting to host: $peerId")
            val conn = peer!!.connect(peerId)
            connection = conn
            setupDataConnection(conn)
        }

        peer!!.on("error") { err ->
            console.log("PeerJS guest error: $err")
            val errMsg = err.asDynamic().type?.toString() ?: err.toString()
            errorMessage = "Connection error: $errMsg"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Data channel setup
    // -----------------------------------------------------------------------

    private fun setupDataConnection(conn: JsDataConnection) {
        conn.on("open") { _ ->
            console.log("Data channel open with peer: ${conn.peer}")
            updateState(ConnectionState.Connected)
        }

        conn.on("data") { data ->
            val type = data.type?.toString() ?: return@on
            onMessageReceived?.invoke(type, data)
        }

        conn.on("close") { _ ->
            console.log("Data channel closed")
            updateState(ConnectionState.Idle)
        }

        conn.on("error") { err ->
            console.log("Data channel error: $err")
            errorMessage = "Data channel error: $err"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Send messages
    // -----------------------------------------------------------------------

    fun send(message: dynamic) {
        connection?.send(message)
    }

    fun sendPlayerInput(angularVelocity: Float) {
        val msg = js("{}")
        msg.type = MessageType.PLAYER_INPUT
        msg.angularVelocity = angularVelocity
        send(msg)
    }

    fun sendGameSync(
        p1X: Float, p1Y: Float, p1Angle: Float, p1AngVel: Float, p1Dead: Boolean,
        p1TrailXs: FloatArray, p1TrailYs: FloatArray,
        p2X: Float, p2Y: Float, p2Angle: Float, p2AngVel: Float, p2Dead: Boolean,
        p2TrailXs: FloatArray, p2TrailYs: FloatArray,
    ) {
        val msg = js("{}")
        msg.type = MessageType.GAME_SYNC
        msg.p1X = p1X; msg.p1Y = p1Y; msg.p1Angle = p1Angle; msg.p1AngVel = p1AngVel; msg.p1Dead = p1Dead
        msg.p1TrailXs = p1TrailXs; msg.p1TrailYs = p1TrailYs
        msg.p2X = p2X; msg.p2Y = p2Y; msg.p2Angle = p2Angle; msg.p2AngVel = p2AngVel; msg.p2Dead = p2Dead
        msg.p2TrailXs = p2TrailXs; msg.p2TrailYs = p2TrailYs
        msg.timestamp = window.performance.now()
        send(msg)
    }

    fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        val msg = js("{}")
        msg.type = MessageType.GAME_START
        msg.canvasWidth = canvasWidth
        msg.canvasHeight = canvasHeight
        send(msg)
    }

    fun sendGameOver(winnerId: Int) {
        val msg = js("{}")
        msg.type = MessageType.GAME_OVER
        msg.winner = winnerId
        send(msg)
    }

    fun sendRematch() {
        val msg = js("{}")
        msg.type = MessageType.REMATCH
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
