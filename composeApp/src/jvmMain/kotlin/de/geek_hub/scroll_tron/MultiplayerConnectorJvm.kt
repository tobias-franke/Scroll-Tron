package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// JVM stub — multiplayer is not supported on desktop
// ---------------------------------------------------------------------------

actual fun createMultiplayerConnector(): MultiplayerConnector =
    error("Multiplayer is not supported on this platform")
