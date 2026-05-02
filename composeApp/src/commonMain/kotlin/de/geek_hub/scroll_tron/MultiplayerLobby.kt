package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import scrolltron.composeapp.generated.resources.Res
import scrolltron.composeapp.generated.resources.orbitron_bold
import scrolltron.composeapp.generated.resources.orbitron_regular
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Lobby palette
// ---------------------------------------------------------------------------

private val NEON_CYAN   = Color(0xFF00FFFF)
private val NEON_PINK   = Color(0xFFFF00FF)
private val NEON_LIME   = Color(0xFF39FF14)
private val GRID_COLOR  = Color(0xFF0D2A0D)
private val BG_COLOR    = Color(0xFF020C02)
private val DIM_TEXT    = Color(0xFF666666)

// ---------------------------------------------------------------------------
// Multiplayer Lobby
// ---------------------------------------------------------------------------

@Composable
fun MultiplayerLobby(
    onBack: () -> Unit,
    onGameReady: (connector: MultiplayerConnector, isHost: Boolean) -> Unit,
) {
    val gameFont = FontFamily(
        Font(Res.font.orbitron_regular, FontWeight.Normal),
        Font(Res.font.orbitron_bold, FontWeight.Bold),
    )

    var lobbyMode by remember { mutableStateOf<LobbyMode?>(null) } // null = choosing
    val connector = remember { createMultiplayerConnector() }
    var connState by remember { mutableStateOf(LobbyConnectionState.Idle) }
    var joinCode  by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    // Pulse animation
    var frameCount by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameCount += 0.03f }
        }
    }
    val pulse = 0.5f + 0.5f * sin(frameCount)

    // Listen for state changes
    LaunchedEffect(connector) {
        connector.onStateChanged { newState ->
            connState = newState
            errorMsg = connector.errorMessage
        }
    }

    // Auto-transition to game when connected
    LaunchedEffect(connState) {
        if (connState == LobbyConnectionState.Connected) {
            onGameReady(connector, lobbyMode == LobbyMode.Host)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (connState != LobbyConnectionState.Connected) {
                connector.disconnect()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Grid background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(BG_COLOR)
            val step = 60f
            var x = 0f
            while (x <= size.width) {
                drawLine(GRID_COLOR, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(GRID_COLOR, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            // Title
            Text(
                text = "MULTIPLAYER",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = gameFont,
                    color = NEON_PINK,
                ),
            )

            Spacer(modifier = Modifier.height(48.dp))

            when {
                // Error state
                connState == LobbyConnectionState.Error -> {
                    Text(
                        text = "CONNECTION ERROR",
                        fontFamily = gameFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFFFF3333),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg ?: "Unknown error",
                        fontFamily = gameFont,
                        fontSize = 12.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LobbyButton("RETRY", NEON_CYAN, gameFont) {
                        connector.disconnect()
                        connState = LobbyConnectionState.Idle
                        lobbyMode = null
                    }
                }

                // Choosing mode
                lobbyMode == null -> {
                    LobbyButton("HOST GAME", NEON_CYAN, gameFont) {
                        lobbyMode = LobbyMode.Host
                        connector.hostGame()
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    LobbyButton("JOIN GAME", NEON_PINK, gameFont) {
                        lobbyMode = LobbyMode.Join
                    }
                }

                // Host: waiting for guest
                lobbyMode == LobbyMode.Host -> {
                    Text(
                        text = "YOUR ROOM CODE",
                        fontFamily = gameFont,
                        fontSize = 14.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Big room code display
                    Box(
                        modifier = Modifier
                            .border(2.dp, NEON_CYAN.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = connector.roomCode,
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            color = NEON_CYAN,
                            letterSpacing = 12.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "SHARE THIS CODE WITH YOUR OPPONENT",
                        fontFamily = gameFont,
                        fontSize = 11.sp,
                        color = DIM_TEXT,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated waiting indicator
                    val dots = ".".repeat(((frameCount * 2).toInt() % 4))
                    Text(
                        text = "WAITING FOR PLAYER$dots",
                        fontFamily = gameFont,
                        fontSize = 14.sp,
                        color = NEON_CYAN.copy(alpha = pulse),
                    )
                }

                // Join: enter code
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Idle -> {
                    Text(
                        text = "ENTER ROOM CODE",
                        fontFamily = gameFont,
                        fontSize = 14.sp,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Code input field
                    Box(
                        modifier = Modifier
                            .border(2.dp, NEON_PINK.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .width(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = joinCode,
                            onValueChange = { newValue ->
                                // Only allow valid characters, max 4
                                val filtered = newValue.uppercase()
                                    .filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }
                                    .take(4)
                                joinCode = filtered
                            },
                            textStyle = TextStyle(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = gameFont,
                                color = NEON_PINK,
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(NEON_PINK),
                        )
                        if (joinCode.isEmpty()) {
                            Text(
                                text = "_ _ _ _",
                                fontFamily = gameFont,
                                fontSize = 36.sp,
                                color = NEON_PINK.copy(alpha = 0.3f),
                                letterSpacing = 8.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (joinCode.length == 4) {
                        LobbyButton("CONNECT", NEON_LIME, gameFont) {
                            connector.joinGame(joinCode)
                        }
                    }
                }

                // Join: connecting
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Connecting -> {
                    val dots = ".".repeat(((frameCount * 2).toInt() % 4))
                    Text(
                        text = "CONNECTING$dots",
                        fontFamily = gameFont,
                        fontSize = 18.sp,
                        color = NEON_PINK.copy(alpha = pulse),
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Back button
            LobbyButton("BACK", DIM_TEXT, gameFont) {
                connector.disconnect()
                onBack()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Internal
// ---------------------------------------------------------------------------

private enum class LobbyMode { Host, Join }

@Composable
private fun LobbyButton(
    text: String,
    color: Color,
    gameFont: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = gameFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color,
        )
    }
}
