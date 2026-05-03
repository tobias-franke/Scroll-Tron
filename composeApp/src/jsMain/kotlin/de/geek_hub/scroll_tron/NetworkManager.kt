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
    private val connections = mutableMapOf<Int, JsDataConnection>() // Player index -> Connection
    private var hostConnection: JsDataConnection? = null // For guests: connection to host

    val numConnections: Int
        get() = connections.size

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
            if (connections.size >= 3) {
                console.log("Maximum 4 players reached, rejecting connection from ${dataConn.peer}")
                dataConn.close()
                return@on
            }
            val playerIndex = connections.size + 1
            connections[playerIndex] = dataConn
            setupDataConnection(dataConn, playerIndex)
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
            hostConnection = conn
            setupDataConnection(conn, -1) // playerIndex -1 means we are a guest
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

    private fun setupDataConnection(conn: JsDataConnection, playerIndex: Int) {
        conn.on("open") { _ ->
            console.log("Data channel open with peer: ${conn.peer} (Player $playerIndex)")
            updateState(ConnectionState.Connected)
        }

        conn.on("data") { data ->
            val type = data.type?.toString() ?: return@on
            
            // If we are host and received input, we need to know which player it is
            if (playerIndex != -1) {
                data.playerIndex = playerIndex
                
                // Host broadcasts rematch requests to all other guests
                if (type == MessageType.REMATCH) {
                    connections.values.forEach { it.send(data) }
                }
            }

            onMessageReceived?.invoke(type, data)
        }

        conn.on("close") { _ ->
            console.log("Data channel closed for Player $playerIndex")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                if (connections.isEmpty()) updateState(ConnectionState.WaitingForGuest)
            } else {
                updateState(ConnectionState.Idle)
            }
        }

        conn.on("error") { err ->
            console.log("Data channel error (Player $playerIndex): $err")
            errorMessage = "Data channel error: $err"
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Send messages
    // -----------------------------------------------------------------------

    fun send(message: dynamic) {
        if (hostConnection != null) {
            hostConnection?.send(message)
        } else {
            // Broadcast to all guests
            connections.values.forEach { it.send(message) }
        }
    }

    fun sendPlayerInput(playerIndex: Int, angularVelocity: Float) {
        val msg = js("{}")
        msg.type = MessageType.PLAYER_INPUT
        msg.playerIndex = playerIndex
        msg.angularVelocity = angularVelocity
        send(msg)
    }

    fun sendGameSync(players: List<PlayerSyncData>) {
        val msg = js("{}")
        msg.type = MessageType.GAME_SYNC
        msg.players = players.map { p ->
            val pObj = js("{}")
            pObj.x = p.x
            pObj.y = p.y
            pObj.angle = p.angle
            pObj.angVel = p.angVel
            pObj.isDead = p.isDead
            pObj
        }.toTypedArray()
        msg.timestamp = window.performance.now()
        send(msg)
    }

    fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        // Host sends to each guest their player index
        connections.forEach { (index, conn) ->
            val msg = js("{}")
            msg.type = MessageType.GAME_START
            msg.canvasWidth = canvasWidth
            msg.canvasHeight = canvasHeight
            msg.playerIndex = index
            conn.send(msg)
        }
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
