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

        network.onMessageReceived = { type, data ->
            when (type) {
                MessageType.GAME_START -> {
                    val w = (data.canvasWidth as Number).toFloat()
                    val h = (data.canvasHeight as Number).toFloat()
                    gameStartCallback?.invoke(w, h)
                }
                MessageType.PLAYER_INPUT -> {
                    val angVel = (data.angularVelocity as Number).toFloat()
                    playerInputCallback?.invoke(angVel)
                }
                MessageType.GAME_SYNC -> {
                    val syncData = parseGameSync(data)
                    gameSyncCallback?.invoke(syncData)
                }
                MessageType.GAME_OVER -> {
                    val winner = (data.winner as Number).toInt()
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
        val msg = js("{}")
        msg.type = MessageType.GAME_SYNC
        msg.p1X = data.p1X; msg.p1Y = data.p1Y; msg.p1Angle = data.p1Angle
        msg.p1AngVel = data.p1AngVel; msg.p1Dead = data.p1Dead
        msg.p2X = data.p2X; msg.p2Y = data.p2Y; msg.p2Angle = data.p2Angle
        msg.p2AngVel = data.p2AngVel; msg.p2Dead = data.p2Dead
        network.send(msg)
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

    private fun parseGameSync(data: dynamic): GameSyncData {
        return GameSyncData(
            p1X = (data.p1X as Number).toFloat(),
            p1Y = (data.p1Y as Number).toFloat(),
            p1Angle = (data.p1Angle as Number).toFloat(),
            p1AngVel = (data.p1AngVel as Number).toFloat(),
            p1Dead = data.p1Dead as Boolean,
            p2X = (data.p2X as Number).toFloat(),
            p2Y = (data.p2Y as Number).toFloat(),
            p2Angle = (data.p2Angle as Number).toFloat(),
            p2AngVel = (data.p2AngVel as Number).toFloat(),
            p2Dead = data.p2Dead as Boolean,
        )
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    private fun parseFloatArray(arr: dynamic): List<Float> {
        val jsArray = arr as? Array<Any?> ?: return emptyList()
        return jsArray.map { (it as Number).toFloat() }
    }
}
