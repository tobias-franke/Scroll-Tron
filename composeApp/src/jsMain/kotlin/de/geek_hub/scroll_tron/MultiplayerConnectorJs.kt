package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// JS implementation of MultiplayerConnector — wraps NetworkManager (PeerJS)
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = JsMultiplayerConnector()

class JsMultiplayerConnector : MultiplayerConnector() {

    private val network = NetworkManager()

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    override val connectedPlayers: Int
        get() = if (network.hostConnection != null) 2 else network.numConnections + 1

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Float, Float, Int) -> Unit)? = null
    private var playerInputCallback: ((Int, Float) -> Unit)? = null
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

        network.onMessageReceived = { type, data ->
            when (type) {
                MessageType.GAME_START -> {
                    val w = (data.canvasWidth as Number).toFloat()
                    val h = (data.canvasHeight as Number).toFloat()
                    val idx = (data.playerIndex as Number).toInt()
                    gameStartCallback?.invoke(w, h, idx)
                }
                MessageType.PLAYER_INPUT -> {
                    val idx = (data.playerIndex as Number).toInt()
                    val angVel = (data.angularVelocity as Number).toFloat()
                    playerInputCallback?.invoke(idx, angVel)
                }
                MessageType.GAME_SYNC -> {
                    val players = (data.players as Array<dynamic>).map { p ->
                        PlayerSyncData(
                            x = (p.x as Number).toFloat(),
                            y = (p.y as Number).toFloat(),
                            angle = (p.angle as Number).toFloat(),
                            angVel = (p.angVel as Number).toFloat(),
                            isDead = p.isDead as Boolean
                        )
                    }
                    gameSyncCallback?.invoke(GameSyncData(players))
                }
                MessageType.GAME_OVER -> {
                    val winner = (data.winner as Number).toInt()
                    gameOverCallback?.invoke(winner)
                }
                MessageType.REMATCH -> {
                    val idx = (data.playerIndex as Number?)?.toInt() ?: 0
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

    override fun onRematchReceived(callback: () -> Unit) {
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

    // -----------------------------------------------------------------------
    // Parse helpers
    // -----------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    private fun parseFloatArray(arr: dynamic): List<Float> {
        val jsArray = arr as? Array<Any?> ?: return emptyList()
        return jsArray.map { (it as Number).toFloat() }
    }
}
