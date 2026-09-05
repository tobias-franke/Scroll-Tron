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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import org.jetbrains.compose.resources.Font
import scrolltron.composeapp.generated.resources.Res
import scrolltron.composeapp.generated.resources.orbitron_bold
import scrolltron.composeapp.generated.resources.orbitron_regular
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Lobby palette
// ---------------------------------------------------------------------------

private val NEON_CYAN   = Color(0xFF00FFFF)
private val NEON_PINK   = Color(0xFFFF00FF)
private val NEON_LIME   = Color(0xFF39FF14)
private val GRID_COLOR  = Color(0xFF0D2A0D)
private val BG_COLOR    = Color(0xFF020C02)
private val DIM_TEXT    = Color(0xFFCCCCCC)

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
    var copiedCode by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(lobbyMode) {
        copiedCode = false
        if (lobbyMode == LobbyMode.Join) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(2000)
            copiedCode = false
        }
    }

    DisposableEffect(lobbyMode, connState) {
        if (lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Idle) {
            val unregister = registerClipboardPasteListener { pasted ->
                val code = sanitizeRoomCode(pasted)
                if (code.isNotEmpty()) {
                    joinCode = code
                }
            }
            onDispose {
                unregister()
            }
        } else {
            onDispose {}
        }
    }

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

    // Auto-transition to game when connected (GUEST ONLY)
    LaunchedEffect(connState) {
        if (connState == LobbyConnectionState.Connected && lobbyMode == LobbyMode.Join) {
            onGameReady(connector, false)
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
                        fontSize = 22.sp,
                        color = Color(0xFFFF3333),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg ?: "Unknown error",
                        fontFamily = gameFont,
                        fontSize = 15.sp,
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
                    val fadeDuration = if (copiedCode) 150 else 600
                    val codeBorderColor by animateColorAsState(
                        targetValue = if (copiedCode) NEON_LIME else NEON_CYAN.copy(alpha = 0.5f),
                        animationSpec = tween(durationMillis = fadeDuration),
                        label = "codeBorderColor"
                    )
                    val codeTextColor by animateColorAsState(
                        targetValue = if (copiedCode) NEON_LIME else NEON_CYAN,
                        animationSpec = tween(durationMillis = fadeDuration),
                        label = "codeTextColor"
                    )
                    val feedbackColor by animateColorAsState(
                        targetValue = if (copiedCode) NEON_LIME else NEON_CYAN.copy(alpha = 0.85f),
                        animationSpec = tween(durationMillis = fadeDuration),
                        label = "feedbackColor"
                    )

                    Text(
                        text = "ROOM CODE",
                        fontFamily = gameFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Big room code display
                    Box(
                        modifier = Modifier
                            .border(2.dp, codeBorderColor, RoundedCornerShape(8.dp))
                            .clickable {
                                if (connector.roomCode.isNotEmpty()) {
                                    copyToClipboard(connector.roomCode)
                                    copiedCode = true
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 36.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = connector.roomCode,
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            color = codeTextColor,
                            letterSpacing = 12.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (copiedCode) "COPIED TO CLIPBOARD!" else "CLICK CODE TO COPY",
                        fontFamily = gameFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = feedbackColor,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "PLAYERS (${connector.connectedPlayers}/4)",
                        fontFamily = gameFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NEON_CYAN,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Player slots
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until 4) {
                            val isConnected = i < connector.connectedPlayers
                            val color = if (isConnected) PLAYER_COLORS[i] else DIM_TEXT
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(1.5.dp, color, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P${i + 1}",
                                    fontFamily = gameFont,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Box(
                        modifier = Modifier.height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (connector.connectedPlayers >= 2) {
                            LobbyButton("START GAME", NEON_LIME, gameFont) {
                                onGameReady(connector, true)
                            }
                        } else {
                            val dots = ".".repeat(((frameCount * 0.5f).toInt() % 4))
                            Text(
                                text = "WAITING FOR OPPONENT$dots",
                                fontFamily = gameFont,
                                fontSize = 16.sp,
                                color = DIM_TEXT,
                            )
                        }
                    }
                }

                // Join: enter code
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Idle -> {
                    Text(
                        text = "ENTER ROOM CODE",
                        fontFamily = gameFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = DIM_TEXT,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Code input field
                    Box(
                        modifier = Modifier
                            .border(2.dp, NEON_PINK.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { focusRequester.requestFocus() }
                            .pointerHoverIcon(PointerIcon.Text)
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = joinCode,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        if (event.key == Key.Enter) {
                                            if (joinCode.length == 4) {
                                                connector.joinGame(joinCode)
                                                true
                                            } else false
                                        } else if ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.V) {
                                            getFromClipboard { text ->
                                                if (!text.isNullOrBlank()) {
                                                    val code = sanitizeRoomCode(text)
                                                    if (code.isNotEmpty()) {
                                                        joinCode = code
                                                    }
                                                }
                                            }
                                            true
                                        } else false
                                    } else false
                                },
                            onValueChange = { newValue ->
                                joinCode = sanitizeRoomCode(newValue)
                            },
                            textStyle = TextStyle(
                                fontSize = 48.sp,
                                fontFamily = gameFont,
                                color = Color.Transparent, // Hide actual text
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.Transparent), // Hide default cursor
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        for (i in 0 until 4) {
                                            val char = joinCode.getOrNull(i)
                                            val isCurrentFocus = i == joinCode.length
                                            val charToDraw = char?.toString() ?: "_"
                                            val alpha = if (char != null) 1f else if (isCurrentFocus) pulse else 0.3f
                                            
                                            Text(
                                                text = charToDraw,
                                                fontFamily = gameFont,
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NEON_PINK.copy(alpha = alpha),
                                            )
                                        }
                                    }
                                    Box(modifier = Modifier.matchParentSize()) {
                                        innerTextField()
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Paste / Clear buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LobbyButton(
                            text = "PASTE",
                            color = NEON_PINK,
                            gameFont = gameFont,
                            modifier = Modifier.width(if (joinCode.isNotEmpty()) 134.dp else 280.dp),
                        ) {
                            getFromClipboard { text ->
                                if (!text.isNullOrBlank()) {
                                    val code = sanitizeRoomCode(text)
                                    if (code.isNotEmpty()) {
                                        joinCode = code
                                    }
                                }
                            }
                            focusRequester.requestFocus()
                        }

                        if (joinCode.isNotEmpty()) {
                            LobbyButton(
                                text = "CLEAR",
                                color = DIM_TEXT,
                                gameFont = gameFont,
                                modifier = Modifier.width(134.dp),
                            ) {
                                joinCode = ""
                                focusRequester.requestFocus()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (joinCode.length == 4) {
                        LobbyButton("CONNECT", NEON_LIME, gameFont) {
                            connector.joinGame(joinCode)
                        }
                    }
                }

                // Join: connecting
                lobbyMode == LobbyMode.Join && connState == LobbyConnectionState.Connecting -> {
                    val dots = ".".repeat(((frameCount * 0.5f).toInt() % 4))
                    Text(
                        text = "CONNECTING$dots",
                        fontFamily = gameFont,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NEON_PINK,
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Back button
            if (connState != LobbyConnectionState.Connected || lobbyMode == LobbyMode.Host) {
                LobbyButton("BACK", DIM_TEXT, gameFont) {
                    connector.disconnect()
                    onBack()
                }
            }
        }
    }
}

internal fun sanitizeRoomCode(raw: String): String {
    val clean = raw.trim().uppercase().removePrefix("STRON-")
    return clean.filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }.take(4)
}

private val PLAYER_COLORS = listOf(
    Color(0xFF00FFFF),  // Cyan
    Color(0xFFFF00FF),  // Pink
    Color(0xFF39FF14),  // Lime
    Color(0xFFFFFF00),  // Yellow
)

// ---------------------------------------------------------------------------
// Internal
// ---------------------------------------------------------------------------

private enum class LobbyMode { Host, Join }

@Composable
private fun LobbyButton(
    text: String,
    color: Color,
    gameFont: FontFamily,
    modifier: Modifier = Modifier.width(280.dp),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 24.dp),
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
