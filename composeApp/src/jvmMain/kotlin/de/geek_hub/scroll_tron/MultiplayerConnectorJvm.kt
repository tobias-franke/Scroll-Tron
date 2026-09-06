package de.geek_hub.scroll_tron

import kotlin.random.Random

// ---------------------------------------------------------------------------
// JVM implementation of MultiplayerConnector
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector = JvmMultiplayerConnector()

class JvmMultiplayerConnector : MultiplayerConnector() {

    private val roomChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    override var state: LobbyConnectionState = LobbyConnectionState.Idle
        private set

    override var errorMessage: String? = null
        private set

    override var roomCode: String = ""
        private set

    override val connectedPlayers: Int
        get() = if (state == LobbyConnectionState.WaitingForGuest || state == LobbyConnectionState.Connected) 1 else 0

    private var stateCallback: ((LobbyConnectionState) -> Unit)? = null
    private var gameStartCallback: ((Float, Float, Int) -> Unit)? = null
    private var playerInputCallback: ((Int, Float) -> Unit)? = null
    private var gameSyncCallback: ((GameSyncData) -> Unit)? = null
    private var gameOverCallback: ((Int) -> Unit)? = null
    private var rematchCallback: ((Int) -> Unit)? = null

    override fun hostGame() {
        roomCode = (1..4).map { roomChars[Random.nextInt(roomChars.length)] }.joinToString("")
        state = LobbyConnectionState.WaitingForGuest
        errorMessage = null
        stateCallback?.invoke(state)
    }

    override fun joinGame(code: String) {
        state = LobbyConnectionState.Error
        errorMessage = "Online play is not supported on desktop yet. Host a game with bots!"
        stateCallback?.invoke(state)
    }

    override fun disconnect() {
        state = LobbyConnectionState.Idle
        errorMessage = null
        roomCode = ""
        stateCallback?.invoke(state)
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
        // Local host drives game state directly
    }

    override fun sendPlayerInput(playerIndex: Int, angularVelocity: Float) {
        // Local host: input directly modifies host state
    }

    override fun sendGameSync(data: GameSyncData) {
        // Local host: no remote peers to sync
    }

    override fun sendGameOver(winnerIndex: Int) {
        // Local host: game over handled directly
    }

    override fun sendRematch() {
        // Local host: rematch handled directly
    }
}
