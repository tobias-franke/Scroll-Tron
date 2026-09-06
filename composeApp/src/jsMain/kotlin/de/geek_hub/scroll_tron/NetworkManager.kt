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
    const val REJECTED     = "rejected"
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

    var isGameStarted: Boolean = false
        private set

    private var peer: JsPeer? = null
    private val connections = mutableMapOf<Int, JsDataConnection>() // Player index -> Connection
    internal var hostConnection: JsDataConnection? = null // For guests: connection to host

    val numConnections: Int
        get() = connections.values.count { it.open }

    // Callbacks set by the lobby/game composables
    var onStateChanged: ((ConnectionState) -> Unit)? = null
    var onMessageReceived: ((type: String, data: dynamic) -> Unit)? = null
    var onPlayerDisconnected: ((Int) -> Unit)? = null

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
            if (isGameStarted) {
                console.log("Game already in progress, rejecting connection from ${dataConn.peer}")
                rejectConnection(dataConn, "Game is already in progress")
                return@on
            }
            val availableIndex = (1..3).firstOrNull { it !in connections.keys }
            if (availableIndex == null) {
                console.log("Maximum 4 players reached, rejecting connection from ${dataConn.peer}")
                rejectConnection(dataConn, "Room is full (maximum 4 players)")
                return@on
            }
            connections[availableIndex] = dataConn
            setupDataConnection(dataConn, availableIndex)
        }

        peer!!.on("error") { err ->
            console.log("PeerJS host error: $err")
            val errType = err?.type?.toString()
            errorMessage = if (errType == "unavailable-id") {
                "Room code is already in use. Please try again."
            } else {
                val detail = errType ?: err?.message?.toString() ?: err?.toString() ?: "Unknown error"
                "Connection error: $detail"
            }
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
            val errType = err?.type?.toString()
            errorMessage = if (errType == "peer-unavailable") {
                "Room '$roomCode' not found. Please check the code."
            } else {
                val detail = errType ?: err?.message?.toString() ?: err?.toString() ?: "Unknown error"
                "Connection error: $detail"
            }
            updateState(ConnectionState.Error)
        }
    }

    // -----------------------------------------------------------------------
    // Data channel setup
    // -----------------------------------------------------------------------

    private fun rejectConnection(conn: JsDataConnection, reason: String) {
        var handled = false
        val sendAndClose = {
            if (!handled) {
                handled = true
                try {
                    val msg = js("{}")
                    msg.type = MessageType.REJECTED
                    msg.reason = reason
                    conn.send(msg)
                } catch (t: Throwable) {
                    console.log("Failed to send rejection message: ${t.message}")
                }
                try { conn.close() } catch (_: Throwable) {}
            }
        }
        conn.on("open") { _ ->
            sendAndClose()
        }
        conn.on("error") { _ ->
            // Ignore error on rejected connection so it doesn't affect host state
            try { conn.close() } catch (_: Throwable) {}
        }
        if (conn.open) {
            sendAndClose()
        }
    }

    private fun setupDataConnection(conn: JsDataConnection, playerIndex: Int) {
        conn.on("open") { _ ->
            console.log("Data channel open with peer: ${conn.peer} (Player $playerIndex)")
            if (playerIndex != -1) {
                connections[playerIndex] = conn
            }
            updateState(ConnectionState.Connected)
        }

        conn.on("data") { data ->
            val type = data.type?.toString() ?: return@on

            if (playerIndex == -1 && type == MessageType.REJECTED) {
                val reason = data.reason?.toString() ?: "Connection rejected"
                console.log("Guest connection rejected by host: $reason")
                errorMessage = reason
                updateState(ConnectionState.Error)
                try { hostConnection?.close() } catch (_: Throwable) {}
                hostConnection = null
                return@on
            }

            // If we are host and received input, we need to know which player it is
            if (playerIndex != -1) {
                data.playerIndex = playerIndex

                // Host broadcasts rematch requests to all other guests
                if (type == MessageType.REMATCH) {
                    connections.values.forEach {
                        if (it.open) {
                            try { it.send(data) } catch (_: Throwable) {}
                        }
                    }
                }
            }

            onMessageReceived?.invoke(type, data)
        }

        conn.on("close") { _ ->
            console.log("Data channel closed for Player $playerIndex")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                onPlayerDisconnected?.invoke(playerIndex)
                if (!isGameStarted && connections.isEmpty()) updateState(ConnectionState.WaitingForGuest)
            } else {
                updateState(ConnectionState.Idle)
            }
        }

        conn.on("error") { err ->
            console.log("Data channel error (Player $playerIndex): $err")
            if (playerIndex != -1) {
                connections.remove(playerIndex)
                onPlayerDisconnected?.invoke(playerIndex)
            } else {
                errorMessage = "Data channel error: $err"
                updateState(ConnectionState.Error)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Send messages
    // -----------------------------------------------------------------------

    fun send(message: dynamic) {
        if (hostConnection != null) {
            if (hostConnection?.open == true) {
                try { hostConnection?.send(message) } catch (t: Throwable) {
                    console.log("Guest send error: ${t.message}")
                }
            }
        } else {
            // Broadcast to all open guest connections
            connections.values.forEach { conn ->
                if (conn.open) {
                    try { conn.send(message) } catch (t: Throwable) {
                        console.log("Host send error: ${t.message}")
                    }
                }
            }
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
            pObj.isBot = p.isBot
            pObj
        }.toTypedArray()
        msg.timestamp = window.performance.now()
        send(msg)
    }

    fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        isGameStarted = true
        // Host sends to each guest their player index
        connections.forEach { (index, conn) ->
            if (conn.open) {
                val msg = js("{}")
                msg.type = MessageType.GAME_START
                msg.canvasWidth = canvasWidth
                msg.canvasHeight = canvasHeight
                msg.playerIndex = index
                try { conn.send(msg) } catch (_: Throwable) {}
            }
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
        if (hostConnection == null) {
            msg.playerIndex = 0
        }
        send(msg)
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    fun disconnect() {
        isGameStarted = false
        hostConnection?.close()
        hostConnection = null
        connections.values.toList().forEach { 
            try { it.close() } catch (_: Throwable) {}
        }
        connections.clear()
        try { peer?.destroy() } catch (_: Throwable) {}
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
