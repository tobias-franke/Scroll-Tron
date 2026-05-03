@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// WasmJS implementation of MultiplayerConnector
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = WasmJsMultiplayerConnector()

@JsFun("function(obj, key) { return obj[key]; }")
internal external fun getJsArray(obj: JsAny, key: String): JsAny

class WasmJsMultiplayerConnector : MultiplayerConnector() {

    private val network = WasmJsNetworkManager()

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    override val connectedPlayers: Int
        get() = if (network.isGuest) 2 else network.numConnections + 1

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Float, Float, Int) -> Unit)? = null
    private var playerInputCallback: ((Int, Float) -> Unit)? = null
    private var gameSyncCallback: ((GameSyncData) -> Unit)? = null
    private var gameOverCallback: ((Int) -> Unit)? = null
    private var rematchCallback: ((Int) -> Unit)? = null

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
                    val idx = getJsInt(dataAny, "playerIndex")
                    gameStartCallback?.invoke(w, h, idx)
                }
                MessageType.PLAYER_INPUT -> {
                    val idx = getJsInt(dataAny, "playerIndex")
                    val angVel = getJsFloat(dataAny, "angularVelocity")
                    playerInputCallback?.invoke(idx, angVel)
                }
                MessageType.GAME_SYNC -> {
                    val playersArr = getJsArray(dataAny, "players")
                    val len = getJsArrayLength(playersArr)
                    val players = mutableListOf<PlayerSyncData>()
                    for (i in 0 until len) {
                        val pObj = getJsArrayItem(playersArr, i)
                        players.add(
                            PlayerSyncData(
                                x = getJsFloat(pObj, "x"),
                                y = getJsFloat(pObj, "y"),
                                angle = getJsFloat(pObj, "angle"),
                                angVel = getJsFloat(pObj, "angVel"),
                                isDead = getJsBoolean(pObj, "isDead")
                            )
                        )
                    }
                    gameSyncCallback?.invoke(GameSyncData(players))
                }
                MessageType.GAME_OVER -> {
                    val winner = getJsInt(dataAny, "winner")
                    gameOverCallback?.invoke(winner)
                }
                MessageType.REMATCH -> {
                    val idx = if (getJsBoolean(dataAny, "hasPlayerIndex")) getJsInt(dataAny, "playerIndex") else 0
                    rematchCallback?.invoke(idx)
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

    override fun onGameStartReceived(callback: (canvasWidth: Float, canvasHeight: Float, playerIndex: Int) -> Unit) {
        gameStartCallback = callback
    }

    override fun onPlayerInputReceived(callback: (playerIndex: Int, angularVelocity: Float) -> Unit) {
        playerInputCallback = callback
    }

    override fun onGameSyncReceived(callback: (GameSyncData) -> Unit) {
        gameSyncCallback = callback
    }

    override fun onGameOverReceived(callback: (winnerIndex: Int) -> Unit) {
        gameOverCallback = callback
    }

    override fun onRematchReceived(callback: (playerIndex: Int) -> Unit) {
        rematchCallback = callback
    }

    // -----------------------------------------------------------------------
    // Send typed messages
    // -----------------------------------------------------------------------

    override fun sendGameStart(canvasWidth: Float, canvasHeight: Float) {
        network.sendGameStart(canvasWidth, canvasHeight)
    }

    override fun sendPlayerInput(playerIndex: Int, angularVelocity: Float) {
        network.sendPlayerInput(playerIndex, angularVelocity)
    }

    override fun sendGameSync(data: GameSyncData) {
        network.sendGameSync(data.players)
    }

    override fun sendGameOver(winnerIndex: Int) {
        network.sendGameOver(winnerIndex)
    }

    override fun sendRematch() {
        network.sendRematch()
    }
}
