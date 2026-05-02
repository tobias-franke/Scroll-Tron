@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// WasmJS implementation of MultiplayerConnector
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = WasmJsMultiplayerConnector()

class WasmJsMultiplayerConnector : MultiplayerConnector() {

    private val network = WasmJsNetworkManager()

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Float, Float) -> Unit)? = null
    private var playerInputCallback: ((Float) -> Unit)? = null
    private var gameSyncCallback: ((GameSyncData) -> Unit)? = null
    private var gameOverCallback: ((Int) -> Unit)? = null
    private var rematchCallback: (() -> Unit)? = null

    init {
        network.onStateChanged = { connState ->
            state = when (connState) {
                ConnectionState.Idle -> LobbyConnectionState.Idle
                ConnectionState.WaitingForGuest -> LobbyConnectionState.WaitingForGuest
                ConnectionState.Connecting -> LobbyConnectionState.Connecting
                ConnectionState.Connected -> LobbyConnectionState.Connected
                ConnectionState.Error -> LobbyConnectionState.Error
            }
            errorMessage = network.errorMessage
            stateCallback?.invoke(state)
        }

        network.onMessageReceived = { type, dataAny ->
            when (type) {
                MessageType.GAME_START -> {
                    val w = getJsFloat(dataAny, "canvasWidth")
                    val h = getJsFloat(dataAny, "canvasHeight")
                    gameStartCallback?.invoke(w, h)
                }
                MessageType.PLAYER_INPUT -> {
                    val angVel = getJsFloat(dataAny, "angularVelocity")
                    playerInputCallback?.invoke(angVel)
                }
                MessageType.GAME_SYNC -> {
                    val syncData = GameSyncData(
                        p1X = getJsFloat(dataAny, "p1X"),
                        p1Y = getJsFloat(dataAny, "p1Y"),
                        p1Angle = getJsFloat(dataAny, "p1Angle"),
                        p1AngVel = getJsFloat(dataAny, "p1AngVel"),
                        p1Dead = getJsBoolean(dataAny, "p1Dead"),
                        p2X = getJsFloat(dataAny, "p2X"),
                        p2Y = getJsFloat(dataAny, "p2Y"),
                        p2Angle = getJsFloat(dataAny, "p2Angle"),
                        p2AngVel = getJsFloat(dataAny, "p2AngVel"),
                        p2Dead = getJsBoolean(dataAny, "p2Dead"),
                    )
                    gameSyncCallback?.invoke(syncData)
                }
                MessageType.GAME_OVER -> {
                    val winner = getJsInt(dataAny, "winner")
                    gameOverCallback?.invoke(winner)
                }
                MessageType.REMATCH -> {
                    rematchCallback?.invoke()
                }
            }
        }
    }

    override fun hostGame() {
        network.hostGame()
        roomCode = network.roomCode
    }

    override fun joinGame(code: String) {
        network.joinGame(code)
        roomCode = network.roomCode
    }

    override fun disconnect() {
        network.disconnect()
        state = LobbyConnectionState.Idle
        errorMessage = null
        roomCode = ""
    }

    override fun onStateChanged(callback: (LobbyConnectionState) -> Unit) {
        stateCallback = callback
    }

    override fun onGameStartReceived(callback: (canvasWidth: Float, canvasHeight: Float) -> Unit) {
        gameStartCallback = callback
    }

    override fun onPlayerInputReceived(callback: (angularVelocity: Float) -> Unit) {
        playerInputCallback = callback
    }

    override fun onGameSyncReceived(callback: (GameSyncData) -> Unit) {
        gameSyncCallback = callback
    }

    override fun onGameOverReceived(callback: (winnerIndex: Int) -> Unit) {
        gameOverCallback = callback
    }

    override fun onRematchReceived(callback: () -> Unit) {
        rematchCallback = callback
    }

    // -----------------------------------------------------------------------
    // Send typed messages
    // -----------------------------------------------------------------------

    override fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        network.sendGameStart(canvasWidth, canvasHeight)
    }

    override fun sendPlayerInput(angularVelocity: Float) {
        network.sendPlayerInput(angularVelocity)
    }

    override fun sendGameSync(data: GameSyncData) {
        network.sendGameSync(
            p1X = data.p1X, p1Y = data.p1Y, p1Angle = data.p1Angle, p1AngVel = data.p1AngVel, p1Dead = data.p1Dead,
            p2X = data.p2X, p2Y = data.p2Y, p2Angle = data.p2Angle, p2AngVel = data.p2AngVel, p2Dead = data.p2Dead
        )
    }

    override fun sendGameOver(winnerIndex: Int) {
        network.sendGameOver(winnerIndex)
    }

    override fun sendRematch() {
        network.sendRematch()
    }
}
