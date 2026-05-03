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

private val PLAYER_COLORS = listOf(
    Color(0xFF00FFFF),  // P1: Cyan (host)
    Color(0xFFFF00FF),  // P2: Pink
    Color(0xFF39FF14),  // P3: Lime
    Color(0xFFFFFF00),  // P4: Yellow
)
private val PLAYER_COLOR_NAMES = listOf("CYAN", "PINK", "LIME", "YELLOW")
private val NEON_LIME = Color(0xFF39FF14)

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

private fun mpInitialState(numPlayers: Int): MultiplayerGameState {
    val gridStep = 60f
    val w = GAME_WIDTH
    val h = GAME_HEIGHT

    val startPositions = listOf(
        Point(w * 0.25f, h * 0.5f) to 0f,                           // P1: left quarter, right
        Point(w * 0.75f, h * 0.5f) to kotlin.math.PI.toFloat(),    // P2: right quarter, left
        Point(w * 0.5f, h * 0.25f) to (kotlin.math.PI / 2).toFloat(), // P3: top quarter, down
        Point(w * 0.5f, h * 0.75f) to (-kotlin.math.PI / 2).toFloat(), // P4: bottom quarter, up
    )

    val players = (0 until numPlayers).map { i ->
        val (pos, angle) = startPositions[i]
        GameState(
            position = Point(
                kotlin.math.round(pos.x / gridStep) * gridStep,
                kotlin.math.round(pos.y / gridStep) * gridStep
            ),
            angle = angle,
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = false
        )
    }

    return MultiplayerGameState(
        players = players,
        winner = null,
    )
}

// ---------------------------------------------------------------------------
// Physics step for one player (with opponent trail for cross-collision)
// ---------------------------------------------------------------------------

private fun stepPlayer(
    state: GameState,
    allTrails: List<List<LineSegment>>,
): GameState {
    if (state.isDead) return state

    val newAngle = state.angle + state.angularVelocity
    val newAngVel = state.angularVelocity * ANGULAR_DECAY

    val dx = SPEED * cos(newAngle)
    val dy = SPEED * sin(newAngle)
    val oldPos = state.position
    val newPos = Point(oldPos.x + dx, oldPos.y + dy)

    val newSegment = LineSegment(oldPos, newPos)
    state.trail.add(newSegment)

    // Wall collision
    val wallHit = newPos.x < 0 || newPos.x > GAME_WIDTH ||
                  newPos.y < 0 || newPos.y > GAME_HEIGHT

    // Self-collision
    var selfHit = false
    val endIdx = state.trail.size - SKIP_SEGMENTS - 1
    for (i in 0..endIdx) {
        val seg = state.trail[i]
        if (segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end)) {
            selfHit = true
            break
        }
    }

    // Cross-player collision (hit any trail)
    var crossHit = false
    for (trail in allTrails) {
        if (trail === state.trail) continue // skip own trail (handled by self-collision)
        for (i in 0 until trail.size) {
            val seg = trail[i]
            if (segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end)) {
                crossHit = true
                break
            }
        }
        if (crossHit) break
    }

    return state.copy(
        position = newPos,
        angle = newAngle,
        angularVelocity = newAngVel,
        isDead = wallHit || selfHit || crossHit,
    )
}

// ---------------------------------------------------------------------------
// Client-side prediction step (no collision detection — host is authoritative)
// ---------------------------------------------------------------------------

private fun stepPredicted(state: GameState): GameState {
    if (state.isDead) return state

    val newAngle = state.angle + state.angularVelocity
    val newAngVel = state.angularVelocity * ANGULAR_DECAY

    val dx = SPEED * cos(newAngle)
    val dy = SPEED * sin(newAngle)
    val oldPos = state.position
    val newPos = Point(oldPos.x + dx, oldPos.y + dy)

    state.trail.add(LineSegment(oldPos, newPos))

    return state.copy(
        position = newPos,
        angle = newAngle,
        angularVelocity = newAngVel,
    )
}

private fun stepMultiplayerPredicted(
    state: MultiplayerGameState,
): MultiplayerGameState {
    if (state.winner != null) return state
    return MultiplayerGameState(
        players = state.players.map { stepPredicted(it) },
        winner = null,
    )
}

// ---------------------------------------------------------------------------
// Full multiplayer step (host-authoritative)
// ---------------------------------------------------------------------------

private fun stepMultiplayer(
    state: MultiplayerGameState,
): MultiplayerGameState {
    if (state.winner != null) return state

    val allTrails = state.players.map { it.trail }
    val newPlayers = state.players.map { stepPlayer(it, allTrails) }

    // Determine winner
    val aliveIndices = newPlayers.indices.filter { !newPlayers[it].isDead }
    val winner = when {
        aliveIndices.size == 1 -> PlayerId.entries[aliveIndices[0]]
        aliveIndices.size == 0 -> state.winner ?: PlayerId.Player1 // Tie -> fallback to previous or P1
        else -> null
    }

    return MultiplayerGameState(
        players = newPlayers,
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

    val path = Path()
    path.moveTo(trail[0].start.x, trail[0].start.y)
    for (i in trail.indices) {
        path.lineTo(trail[i].end.x, trail[i].end.y)
    }

    drawPath(
        path = path,
        color = trailColor.copy(alpha = 0.35f),
        style = Stroke(width = 8f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
    drawPath(
        path = path,
        color = trailColor,
        style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
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
        players = state.players.map { p ->
            PlayerSyncData(
                x = p.position.x,
                y = p.position.y,
                angle = p.angle,
                angVel = p.angularVelocity,
                isDead = p.isDead
            )
        }
    )
}

private fun MultiplayerGameState.applySyncData(data: GameSyncData): MultiplayerGameState {
    val newPlayers = players.mapIndexed { i, player ->
        val pData = data.players.getOrNull(i) ?: return@mapIndexed player
        
        val newPos = Point(pData.x, pData.y)
        if (player.trail.isNotEmpty() && !pData.isDead) {
            val lastSeg = player.trail.last()
            player.trail[player.trail.lastIndex] = LineSegment(lastSeg.start, newPos)
        }
        
        player.copy(
            position = newPos,
            angle = pData.angle,
            angularVelocity = pData.angVel,
            isDead = pData.isDead
        )
    }

    val aliveIndices = newPlayers.indices.filter { !newPlayers[it].isDead }
    val newWinner = when {
        aliveIndices.size == 1 && newPlayers.size > 1 -> PlayerId.entries[aliveIndices[0]]
        else -> null
    }

    return MultiplayerGameState(
        players = newPlayers,
        winner = newWinner
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
    var myPlayerIndex by remember { mutableStateOf(if (isHost) 0 else -1) }
    var mpState by remember { mutableStateOf(mpInitialState(1)) } // Start with 1, will reset
    var readyPlayers by remember { mutableStateOf(setOf<Int>()) }
    var gameStarted by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        if (isHost) {
            mpState = mpInitialState(connector.connectedPlayers)
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

        connector.onGameStartReceived { _, _, playerIndex ->
            if (!isHost) {
                // Guest received start signal from host
                myPlayerIndex = playerIndex
                // Reset local state. By clearing players, we force the next GameSync to re-initialize them with empty trails.
                readyPlayers = emptySet()
                mpState = mpState.copy(winner = null, players = emptyList())
                gameStarted = true
            }
        }

        connector.onPlayerInputReceived { playerIndex, angVel ->
            if (isHost) {
                // Host received steering input from guest
                val players = mpState.players.toMutableList()
                if (playerIndex in players.indices) {
                    val p = players[playerIndex]
                    players[playerIndex] = p.copy(angularVelocity = p.angularVelocity + angVel)
                    mpState = mpState.copy(players = players)
                }
            }
        }

        connector.onGameSyncReceived { syncData ->
            if (!isHost) {
                // Guest received authoritative state from host
                if (mpState.players.size != syncData.players.size) {
                    // Initialize player list with correct size
                    mpState = mpState.copy(
                        players = syncData.players.map { p ->
                            GameState(
                                position = Point(p.x, p.y),
                                angle = p.angle,
                                angularVelocity = p.angVel,
                                trail = mutableListOf(),
                                isDead = p.isDead
                            )
                        }
                    )
                }
                mpState = mpState.applySyncData(syncData)
                gameStarted = true // Start game once we have state
            }
        }

        connector.onGameOverReceived { _ ->
            // Game over handled by state (mpState.winner)
        }

        connector.onRematchReceived { playerIndex ->
            readyPlayers = readyPlayers + playerIndex
            if (isHost && readyPlayers.size == mpState.players.size) {
                // Everyone is ready, start!
                mpState = mpInitialState(mpState.players.size)
                readyPlayers = emptySet()
                connector.sendGameStart(GAME_WIDTH, GAME_HEIGHT)
            }
        }
    }

    // Game loop (host runs physics, guest predicts locally)
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
                        // Host steps physics (authoritative)
                        mpState = stepMultiplayer(mpState)

                        // Send state sync to guest
                        syncCounter++
                        if (syncCounter >= syncInterval) {
                            syncCounter = 0
                            connector.sendGameSync(stateToSyncData(mpState))
                        }

                        // Notify game over
                        if (mpState.winner != null) {
                            connector.sendGameOver(mpState.winner!!.ordinal)
                        }
                    } else {
                        // Guest: predict locally for smooth rendering
                        mpState = stepMultiplayerPredicted(mpState)
                    }
                }
            }
        }
    }

    val doRematch: () -> Unit = {
        if (!readyPlayers.contains(myPlayerIndex)) {
            readyPlayers = readyPlayers + myPlayerIndex
            connector.sendRematch()
            if (isHost && readyPlayers.size == mpState.players.size) {
                // Everyone is ready, start!
                mpState = mpInitialState(mpState.players.size)
                readyPlayers = emptySet()
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
                        // Host controls player 0 directly
                        val players = mpState.players.toMutableList()
                        val p0 = players[0]
                        players[0] = p0.copy(angularVelocity = p0.angularVelocity + impulse)
                        mpState = mpState.copy(players = players)
                    } else {
                        // Guest sends input to host
                        connector.sendPlayerInput(myPlayerIndex, impulse)
                    }
                }
            },
    ) {
        // My player index
        val myPlayerId = if (myPlayerIndex != -1) PlayerId.entries[myPlayerIndex] else null

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
                    mpState.players.forEachIndexed { i, player ->
                        val color = PLAYER_COLORS[i % PLAYER_COLORS.size]
                        drawTrail(player.trail, color)
                        if (!player.isDead) {
                            val angleDeg = (player.angle * (180.0 / kotlin.math.PI)).toFloat()
                            drawHead(player.position, angleDeg, color)
                        }
                    }

                    // Player labels HUD (top-left)
                    val pad = 40f
                    val topInset = 120f
                    var currentY = pad + topInset

                    mpState.players.forEachIndexed { i, player ->
                        val isMe = i == myPlayerIndex
                        val colorName = PLAYER_COLOR_NAMES.getOrNull(i) ?: "P${i+1}"
                        val label = if (isMe) "$colorName (YOU)" else colorName
                        val color = PLAYER_COLORS[i % PLAYER_COLORS.size]
                        val style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = gameFont,
                            color = if (player.isDead) color.copy(alpha = 0.3f) else color,
                        )
                        val measured = textMeasurer.measure(label, style)
                        drawText(measured, topLeft = Offset(pad, currentY))
                        currentY += measured.size.height + 10f
                    }

                    // Game over or Connection Lost overlay
                    if (mpState.winner != null || connectionLost) {
                        drawRect(Color(0xCC000000), size = Size(GAME_WIDTH, GAME_HEIGHT))

                        val title = when {
                            connectionLost -> "CONNECTION LOST"
                            mpState.winner == myPlayerId -> "YOU WIN"
                            mpState.winner != null -> "${PLAYER_COLOR_NAMES.getOrNull(mpState.winner!!.ordinal) ?: "OPPONENT"} WINS"
                            else -> "GAME OVER"
                        }
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
                            color = PLAYER_COLORS[0],
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
                            val isReady = readyPlayers.contains(myPlayerIndex)
                            val rematchColor = if (isReady) Color(0xFFAAAAAA) else NEON_LIME
                            val rematchText = if (isReady) "WAITING (${readyPlayers.size}/${mpState.players.size})" else "REMATCH"
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = rematchColor,
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                    .clickable(enabled = !isReady) { doRematch() }
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
                                    color = Color(0xFFAAAAAA),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .clickable { onBack() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "LEAVE",
                                fontFamily = gameFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFAAAAAA),
                            )
                        }
                    }
                }
            }
        }


    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(mpState.winner) { focusRequester.requestFocus() }
}
