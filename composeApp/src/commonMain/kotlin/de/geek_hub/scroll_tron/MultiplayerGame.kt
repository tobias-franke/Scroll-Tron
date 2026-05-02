package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.Font
import scrolltron.composeapp.generated.resources.Res
import scrolltron.composeapp.generated.resources.orbitron_bold
import scrolltron.composeapp.generated.resources.orbitron_regular
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Neon palette for two players
// ---------------------------------------------------------------------------

private val PLAYER1_COLOR = Color(0xFF00FFFF)  // Cyan (host)
private val PLAYER2_COLOR = Color(0xFFFF00FF)  // Pink (guest)
private val NEON_LIME     = Color(0xFF39FF14)

// ---------------------------------------------------------------------------
// Collision helpers (same as singleplayer)
// ---------------------------------------------------------------------------

private fun cross(ux: Float, uy: Float, vx: Float, vy: Float) = ux * vy - uy * vx

private fun segmentsIntersect(
    p1: Point, p2: Point,
    q1: Point, q2: Point,
): Boolean {
    val rx = p2.x - p1.x;  val ry = p2.y - p1.y
    val sx = q2.x - q1.x;  val sy = q2.y - q1.y
    val denom = cross(rx, ry, sx, sy)
    val dx = q1.x - p1.x;  val dy = q1.y - p1.y
    
    if (abs(denom) < 1e-6f) {
        // Parallel or collinear
        if (abs(cross(dx, dy, rx, ry)) < 1e-6f) {
            // Collinear: Check bounding box overlap
            val pMinX = kotlin.math.min(p1.x, p2.x)
            val pMaxX = kotlin.math.max(p1.x, p2.x)
            val qMinX = kotlin.math.min(q1.x, q2.x)
            val qMaxX = kotlin.math.max(q1.x, q2.x)
            
            val pMinY = kotlin.math.min(p1.y, p2.y)
            val pMaxY = kotlin.math.max(p1.y, p2.y)
            val qMinY = kotlin.math.min(q1.y, q2.y)
            val qMaxY = kotlin.math.max(q1.y, q2.y)
            
            return kotlin.math.max(pMinX, qMinX) <= kotlin.math.min(pMaxX, qMaxX) + 1e-4f &&
                   kotlin.math.max(pMinY, qMinY) <= kotlin.math.min(pMaxY, qMaxY) + 1e-4f
        }
        return false
    }
    
    val t = cross(dx, dy, sx, sy) / denom
    val u = cross(dx, dy, rx, ry) / denom
    return t in -1e-4f..1.0001f && u in -1e-4f..1.0001f
}

// ---------------------------------------------------------------------------
// Multiplayer initial state
// ---------------------------------------------------------------------------

const val GAME_WIDTH = 3200f
const val GAME_HEIGHT = 1800f

private fun mpInitialState(): MultiplayerGameState {
    val gridStep = 60f
    val w = GAME_WIDTH
    val h = GAME_HEIGHT

    // Player 1 starts on the left quarter, heading right
    val p1x = kotlin.math.round(w * 0.25f / gridStep) * gridStep
    val p1y = kotlin.math.round(h * 0.5f  / gridStep) * gridStep

    // Player 2 starts on the right quarter, heading left
    val p2x = kotlin.math.round(w * 0.75f / gridStep) * gridStep
    val p2y = kotlin.math.round(h * 0.5f  / gridStep) * gridStep

    return MultiplayerGameState(
        player1 = GameState(
            position = Point(p1x, p1y),
            angle = 0f,          // heading right →
            angularVelocity = 0f,
            trail = emptyList(),
            isDead = false,
        ),
        player2 = GameState(
            position = Point(p2x, p2y),
            angle = kotlin.math.PI.toFloat(),  // heading left ←
            angularVelocity = 0f,
            trail = emptyList(),
            isDead = false,
        ),
        winner = null,
    )
}

// ---------------------------------------------------------------------------
// Physics step for one player (with opponent trail for cross-collision)
// ---------------------------------------------------------------------------

private fun stepPlayer(
    state: GameState,
    opponentTrail: List<LineSegment>,
): GameState {
    if (state.isDead) return state

    val newAngle = state.angle + state.angularVelocity
    val newAngVel = state.angularVelocity * ANGULAR_DECAY

    val dx = SPEED * cos(newAngle)
    val dy = SPEED * sin(newAngle)
    val oldPos = state.position
    val newPos = Point(oldPos.x + dx, oldPos.y + dy)

    val newSegment = LineSegment(oldPos, newPos)
    val newTrail = state.trail + newSegment

    // Wall collision
    val wallHit = newPos.x < 0 || newPos.x > GAME_WIDTH ||
                  newPos.y < 0 || newPos.y > GAME_HEIGHT

    // Self-collision
    val selfHit = if (newTrail.size > SKIP_SEGMENTS) {
        val safeTrail = newTrail.dropLast(SKIP_SEGMENTS)
        safeTrail.any { seg -> segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end) }
    } else false

    // Cross-player collision (hit opponent's trail)
    val crossHit = opponentTrail.any { seg ->
        segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end)
    }

    return state.copy(
        position = newPos,
        angle = newAngle,
        angularVelocity = newAngVel,
        trail = newTrail,
        isDead = wallHit || selfHit || crossHit,
    )
}

// ---------------------------------------------------------------------------
// Full multiplayer step (host-authoritative)
// ---------------------------------------------------------------------------

private fun stepMultiplayer(
    state: MultiplayerGameState,
): MultiplayerGameState {
    if (state.winner != null) return state

    val newP1 = stepPlayer(state.player1, state.player2.trail)
    val newP2 = stepPlayer(state.player2, state.player1.trail)

    // Determine winner
    val winner = when {
        newP1.isDead && newP2.isDead -> PlayerId.Player1  // simultaneous death → Player 1 wins (host advantage)
        newP1.isDead -> PlayerId.Player2
        newP2.isDead -> PlayerId.Player1
        else -> null
    }

    return MultiplayerGameState(
        player1 = newP1,
        player2 = newP2,
        winner = winner,
    )
}

// ---------------------------------------------------------------------------
// Rendering helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawGrid() {
    val step = 60f
    val lineColor = Color(0xFF0D2A0D)
    var x = 0f
    while (x <= GAME_WIDTH) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, GAME_HEIGHT), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= GAME_HEIGHT) {
        drawLine(lineColor, Offset(0f, y), Offset(GAME_WIDTH, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawBorder() {
    val strokeWidth = 3f
    val inset = strokeWidth / 2f
    drawRect(
        color = Color(0xFF00FFFF).copy(alpha = 0.4f),
        topLeft = Offset(inset, inset),
        size = Size(GAME_WIDTH - strokeWidth, GAME_HEIGHT - strokeWidth),
        style = Stroke(width = strokeWidth),
    )
}

private fun DrawScope.drawTrail(trail: List<LineSegment>, trailColor: Color) {
    if (trail.isEmpty()) return
    val total = trail.size.toFloat()
    trail.forEachIndexed { i, seg ->
        val alpha = 0.35f + 0.65f * (i / total)
        drawLine(
            color = trailColor.copy(alpha = alpha * 0.35f),
            start = Offset(seg.start.x, seg.start.y),
            end   = Offset(seg.end.x,   seg.end.y),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = trailColor.copy(alpha = alpha),
            start = Offset(seg.start.x, seg.start.y),
            end   = Offset(seg.end.x,   seg.end.y),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawHead(pos: Point, angleDeg: Float, trailColor: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(trailColor.copy(alpha = 0.7f), Color.Transparent),
            center = Offset(pos.x, pos.y),
            radius = 18f,
        ),
        radius = 18f,
        center = Offset(pos.x, pos.y),
    )
    rotate(degrees = angleDeg, pivot = Offset(pos.x, pos.y)) {
        val path = Path().apply {
            moveTo(pos.x + 10f, pos.y)
            lineTo(pos.x - 7f, pos.y - 6f)
            lineTo(pos.x - 7f, pos.y + 6f)
            close()
        }
        drawPath(path, color = Color.White)
        drawPath(path, color = trailColor, style = Stroke(width = 1.5f))
    }
}



private fun stateToSyncData(state: MultiplayerGameState): GameSyncData {
    return GameSyncData(
        p1X = state.player1.position.x, p1Y = state.player1.position.y,
        p1Angle = state.player1.angle, p1AngVel = state.player1.angularVelocity,
        p1Dead = state.player1.isDead,
        p2X = state.player2.position.x, p2Y = state.player2.position.y,
        p2Angle = state.player2.angle, p2AngVel = state.player2.angularVelocity,
        p2Dead = state.player2.isDead,
    )
}

private fun MultiplayerGameState.applySyncData(data: GameSyncData): MultiplayerGameState {
    val newWinner = winner ?: when {
        data.p1Dead && data.p2Dead -> PlayerId.Player1
        data.p1Dead -> PlayerId.Player2
        data.p2Dead -> PlayerId.Player1
        else -> null
    }
    
    val newP1Pos = Point(data.p1X, data.p1Y)
    val p1DistSq = (player1.position.x - newP1Pos.x)*(player1.position.x - newP1Pos.x) + (player1.position.y - newP1Pos.y)*(player1.position.y - newP1Pos.y)
    val p1Trail = if (data.p1Dead || p1DistSq > 10000f) {
        player1.trail
    } else {
        player1.trail + LineSegment(player1.position, newP1Pos)
    }

    val newP2Pos = Point(data.p2X, data.p2Y)
    val p2DistSq = (player2.position.x - newP2Pos.x)*(player2.position.x - newP2Pos.x) + (player2.position.y - newP2Pos.y)*(player2.position.y - newP2Pos.y)
    val p2Trail = if (data.p2Dead || p2DistSq > 10000f) {
        player2.trail
    } else {
        player2.trail + LineSegment(player2.position, newP2Pos)
    }

    return MultiplayerGameState(
        player1 = GameState(
            position = newP1Pos,
            angle = data.p1Angle,
            angularVelocity = data.p1AngVel,
            trail = p1Trail,
            isDead = data.p1Dead,
        ),
        player2 = GameState(
            position = newP2Pos,
            angle = data.p2Angle,
            angularVelocity = data.p2AngVel,
            trail = p2Trail,
            isDead = data.p2Dead,
        ),
        winner = newWinner,
    )
}

// ---------------------------------------------------------------------------
// Multiplayer Game composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultiplayerGame(
    connector: MultiplayerConnector,
    isHost: Boolean,
    onBack: () -> Unit,
) {
    var mpState by remember { mutableStateOf(mpInitialState()) }
    var gameStarted by remember { mutableStateOf(false) }
    var rematchRequested by remember { mutableStateOf(false) }
    var opponentRematch  by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var connectionLost by remember { mutableStateOf(false) }

    // Sync counter — send full state every N frames (host only)
    var syncCounter by remember { mutableStateOf(0) }
    val syncInterval = 1  // host sends syncs every frame

    val focusRequester = remember { FocusRequester() }
    val textMeasurer   = rememberTextMeasurer()
    val gameFont = FontFamily(
        Font(Res.font.orbitron_regular, FontWeight.Normal),
        Font(Res.font.orbitron_bold, FontWeight.Bold),
    )

    // Host starts immediately, guest waits for GAME_START message
    LaunchedEffect(Unit) {
        if (isHost) {
            connector.sendGameStart(GAME_WIDTH, GAME_HEIGHT)
            gameStarted = true
        }
    }

    // Set up message handlers
    LaunchedEffect(connector) {
        connector.onStateChanged { state ->
            if ((state == LobbyConnectionState.Idle || state == LobbyConnectionState.Error) && !isLeaving) {
                connectionLost = true
            }
        }

        connector.onGameStartReceived { _, _ ->
            if (!isHost) {
                // Guest received start signal from host
                mpState = mpInitialState()
                gameStarted = true
            }
        }

        connector.onPlayerInputReceived { angVel ->
            if (isHost) {
                // Host received steering input from guest → apply to player2
                val p2 = mpState.player2
                mpState = mpState.copy(
                    player2 = p2.copy(angularVelocity = p2.angularVelocity + angVel)
                )
            }
        }

        connector.onGameSyncReceived { syncData ->
            if (!isHost) {
                // Guest received authoritative state from host, append trail locally
                mpState = mpState.applySyncData(syncData)
            }
        }

        connector.onGameOverReceived { _ ->
            // Game over handled by state (mpState.winner)
        }

        connector.onRematchReceived {
            opponentRematch = true
            if (rematchRequested) {
                // Both want rematch — restart
                mpState = mpInitialState()
                rematchRequested = false
                opponentRematch = false
                if (isHost) {
                    connector.sendGameStart(GAME_WIDTH, GAME_HEIGHT)
                }
            }
        }
    }

    // Game loop (host runs physics, guest just renders)
    LaunchedEffect(gameStarted) {
        if (!gameStarted) return@LaunchedEffect
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastFrame == 0L) { lastFrame = nanos; return@withFrameNanos }
                val elapsed = (nanos - lastFrame) / 1_000_000L
                if (elapsed >= 14L && !connectionLost) {
                    lastFrame = nanos
                    if (isHost) {
                        // Host steps physics
                        mpState = stepMultiplayer(mpState)

                        // Send state sync to guest
                        syncCounter++
                        if (syncCounter >= syncInterval) {
                            syncCounter = 0
                            connector.sendGameSync(stateToSyncData(mpState))
                        }

                        // Notify game over
                        if (mpState.winner != null) {
                            connector.sendGameOver(if (mpState.winner == PlayerId.Player1) 1 else 2)
                        }
                    }
                }
            }
        }
    }

    val doRematch: () -> Unit = {
        rematchRequested = true
        connector.sendRematch()
        if (opponentRematch) {
            mpState = mpInitialState()
            rematchRequested = false
            opponentRematch = false
            if (isHost) {
                connector.sendGameStart(GAME_WIDTH, GAME_HEIGHT)
            }
        }
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        if (!isLeaving) {
                            isLeaving = true
                            onBack()
                        }
                        true
                    }
                    Key.R -> if (mpState.winner != null) { doRematch(); true } else false
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f && gameStarted && mpState.winner == null) {
                    val sign = if (delta > 0f) 1f else -1f
                    val impulse = sign * STEERING_SENSITIVITY
                    if (isHost) {
                        // Host controls player1 directly
                        val p1 = mpState.player1
                        mpState = mpState.copy(
                            player1 = p1.copy(angularVelocity = p1.angularVelocity + impulse)
                        )
                    } else {
                        // Guest sends input to host
                        connector.sendPlayerInput(impulse)
                    }
                }
            },
    ) {
        // My player index
        val myPlayerId = if (isHost) PlayerId.Player1 else PlayerId.Player2

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw background for the entire screen (including letterbox areas)
                drawRect(Color(0xFF020C02))

                val fitScale = minOf(size.width / GAME_WIDTH, size.height / GAME_HEIGHT)
                val dx = (size.width - GAME_WIDTH * fitScale) / 2f
                val dy = (size.height - GAME_HEIGHT * fitScale) / 2f

                translate(left = dx, top = dy) {
                    scale(scale = fitScale, pivot = Offset.Zero) {
                        drawGrid()
                        drawBorder()

                if (gameStarted) {
                    // Player 1 (host) trail & head
                    drawTrail(mpState.player1.trail, PLAYER1_COLOR)
                    if (!mpState.player1.isDead) {
                        val a1 = (mpState.player1.angle * (180.0 / kotlin.math.PI)).toFloat()
                        drawHead(mpState.player1.position, a1, PLAYER1_COLOR)
                    }

                    // Player 2 (guest) trail & head
                    drawTrail(mpState.player2.trail, PLAYER2_COLOR)
                    if (!mpState.player2.isDead) {
                        val a2 = (mpState.player2.angle * (180.0 / kotlin.math.PI)).toFloat()
                        drawHead(mpState.player2.position, a2, PLAYER2_COLOR)
                    }

                    // Player labels HUD (top-left)
                    val myColor = if (isHost) PLAYER1_COLOR else PLAYER2_COLOR
                    val myLabel = if (isHost) "YOU (P1)" else "YOU (P2)"
                    val oppLabel = if (isHost) "OPPONENT (P2)" else "OPPONENT (P1)"
                    val oppColor = if (isHost) PLAYER2_COLOR else PLAYER1_COLOR

                    val pad = 40f
                    val topInset = 120f

                    val myMeasured = textMeasurer.measure(
                        myLabel,
                        TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = myColor,
                        ),
                    )
                    val oppMeasured = textMeasurer.measure(
                        oppLabel,
                        TextStyle(
                            fontSize = 30.sp,
                            fontFamily = FontFamily.Monospace,
                            color = oppColor,
                        ),
                    )
                    drawText(myMeasured, topLeft = Offset(pad, pad + topInset))
                    drawText(oppMeasured, topLeft = Offset(pad, pad + topInset + myMeasured.size.height + 10f))

                    // Game over or Connection Lost overlay
                    if (mpState.winner != null || connectionLost) {
                        drawRect(Color(0xCC000000), size = Size(GAME_WIDTH, GAME_HEIGHT))

                        val title = if (connectionLost) "CONNECTION LOST"
                                    else if (mpState.winner == myPlayerId) "YOU WIN"
                                    else "YOU LOSE"
                        val titleColor = if (connectionLost) Color(0xFFFFCC00)
                                         else if (mpState.winner == myPlayerId) NEON_LIME
                                         else Color(0xFFFF3333)

                        val titleMeasured = textMeasurer.measure(
                            title,
                            TextStyle(
                                fontSize = 120.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = gameFont,
                                color = titleColor,
                            ),
                        )
                        drawText(
                            titleMeasured,
                            topLeft = Offset(
                                GAME_WIDTH / 2f - titleMeasured.size.width / 2f,
                                GAME_HEIGHT / 2f - titleMeasured.size.height / 2f - 50f,
                            ),
                        )
                    }
                } else {
                    // Waiting for game start
                    val waitText = "WAITING FOR GAME START..."
                    val waitMeasured = textMeasurer.measure(
                        waitText,
                        TextStyle(
                            fontSize = 45.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = gameFont,
                            color = PLAYER1_COLOR,
                        ),
                    )
                    drawText(
                        waitMeasured,
                        topLeft = Offset(
                            GAME_WIDTH / 2f - waitMeasured.size.width / 2f,
                            GAME_HEIGHT / 2f - waitMeasured.size.height / 2f,
                        ),
                    )
                }
                    }
                }
            }
        }

            // Rematch / back buttons overlay
            if (mpState.winner != null || connectionLost) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 56.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Rematch button (only if not disconnected)
                        if (!connectionLost) {
                            val rematchColor = if (rematchRequested && !opponentRematch)
                            Color(0xFF666666) else NEON_LIME
                        val rematchText = when {
                            rematchRequested && !opponentRematch -> "WAITING..."
                            else -> "REMATCH"
                        }
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = rematchColor,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .let { if (!rematchRequested) it.clickable { doRematch() } else it }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = rematchText,
                                fontFamily = gameFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = rematchColor,
                            )
                        }
                        }

                        // Back to menu
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF666666),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable {
                                    if (!isLeaving) {
                                        isLeaving = true
                                        connector.disconnect()
                                        onBack()
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "LEAVE",
                                fontFamily = gameFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF666666),
                            )
                        }
                    }
                }
            }
        }


    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(mpState.winner) { focusRequester.requestFocus() }
}
