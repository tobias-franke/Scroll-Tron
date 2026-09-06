package de.geek_hub.scroll_tron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmMultiplayerConnectorTest {

    @Test
    fun testInitialState() {
        val connector = createMultiplayerConnector()
        assertEquals(LobbyConnectionState.Idle, connector.state)
        assertEquals("", connector.roomCode)
        assertEquals(0, connector.connectedPlayers)
        assertEquals(null, connector.errorMessage)
    }

    @Test
    fun testHostGame() {
        val connector = createMultiplayerConnector()
        var stateChanged: LobbyConnectionState? = null
        connector.onStateChanged { stateChanged = it }

        connector.hostGame()

        assertEquals(LobbyConnectionState.WaitingForGuest, connector.state)
        assertEquals(LobbyConnectionState.WaitingForGuest, stateChanged)
        assertEquals(4, connector.roomCode.length)
        assertTrue(connector.roomCode.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        assertEquals(1, connector.connectedPlayers)
        assertEquals(null, connector.errorMessage)
    }

    @Test
    fun testJoinGameShowsError() {
        val connector = createMultiplayerConnector()
        var stateChanged: LobbyConnectionState? = null
        connector.onStateChanged { stateChanged = it }

        connector.joinGame("ABCD")

        assertEquals(LobbyConnectionState.Error, connector.state)
        assertEquals(LobbyConnectionState.Error, stateChanged)
        assertNotNull(connector.errorMessage)
        assertTrue(connector.errorMessage!!.contains("desktop"))
    }

    @Test
    fun testDisconnectResetsState() {
        val connector = createMultiplayerConnector()
        connector.hostGame()
        assertEquals(LobbyConnectionState.WaitingForGuest, connector.state)

        connector.disconnect()
        assertEquals(LobbyConnectionState.Idle, connector.state)
        assertEquals("", connector.roomCode)
        assertEquals(0, connector.connectedPlayers)
        assertEquals(null, connector.errorMessage)
    }

    @Test
    fun testSendGameStartTransitionsToConnected() {
        val connector = createMultiplayerConnector()
        connector.hostGame()
        assertEquals(LobbyConnectionState.WaitingForGuest, connector.state)

        var stateChanged: LobbyConnectionState? = null
        connector.onStateChanged { stateChanged = it }

        connector.sendGameStart(3200f, 1800f)
        assertEquals(LobbyConnectionState.Connected, connector.state)
        assertEquals(LobbyConnectionState.Connected, stateChanged)
        assertEquals(1, connector.connectedPlayers)
    }

    @Test
    fun testIsMultiplayerSupportedOnJvm() {
        assertTrue(isMultiplayerSupported())
    }
}
