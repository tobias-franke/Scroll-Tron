package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// Multiplayer connection abstraction (expect/actual)
// ---------------------------------------------------------------------------
// The lobby UI lives in commonMain but needs to drive the PeerJS connection
// (which is JS-only). This abstract class lets commonMain code drive the
// connection without depending on JS-specific types.
//
// On JS: implemented by wrapping NetworkManager (PeerJS).
// On JVM/WasmJS: stub implementation (multiplayer is unsupported).

/** Represents the state of a multiplayer connection. */
enum class LobbyConnectionState {
    Idle,
    WaitingForGuest,
    Connecting,
    Connected,
    Error,
}

/** Platform-specific multiplayer connector created via [createMultiplayerConnector]. */
abstract class MultiplayerConnector {

    abstract val state: LobbyConnectionState
    abstract val errorMessage: String?
    abstract val roomCode: String

    /** Host a new game — generates a room code. */
    abstract fun hostGame()

    /** Join an existing game by room code. */
    abstract fun joinGame(code: String)

    /** Tear down the connection. */
    abstract fun disconnect()

    /** Register a callback for state changes. */
    abstract fun onStateChanged(callback: (LobbyConnectionState) -> Unit)

    // -----------------------------------------------------------------------
    // Game message callbacks (set by the game composable)
    // -----------------------------------------------------------------------

    abstract fun onGameStartReceived(callback: (canvasWidth: Float, canvasHeight: Float) -> Unit)
    abstract fun onPlayerInputReceived(callback: (angularVelocity: Float) -> Unit)
    abstract fun onGameSyncReceived(callback: (GameSyncData) -> Unit)
    abstract fun onGameOverReceived(callback: (winnerIndex: Int) -> Unit)
    abstract fun onRematchReceived(callback: () -> Unit)

    // -----------------------------------------------------------------------
    // Send typed messages
    // -----------------------------------------------------------------------

    abstract fun sendGameStart(canvasWidth: Float, canvasHeight: Float)
    abstract fun sendPlayerInput(angularVelocity: Float)
    abstract fun sendGameSync(data: GameSyncData)
    abstract fun sendGameOver(winnerIndex: Int)
    abstract fun sendRematch()
}

/** Flattened game state for network transfer. */
data class GameSyncData(
    val p1X: Float, val p1Y: Float, val p1Angle: Float, val p1AngVel: Float, val p1Dead: Boolean,
    val p2X: Float, val p2Y: Float, val p2Angle: Float, val p2AngVel: Float, val p2Dead: Boolean,
)

/** Factory function — platform-specific. */
expect fun createMultiplayerConnector(): MultiplayerConnector
