package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// Screen navigation
// ---------------------------------------------------------------------------

sealed class Screen {
    data object MainMenu : Screen()
    data object Singleplayer : Screen()
    data object MultiplayerLobby : Screen()
    data class MultiplayerGame(
        val isHost: Boolean,
        val roomCode: String,
    ) : Screen()
}
