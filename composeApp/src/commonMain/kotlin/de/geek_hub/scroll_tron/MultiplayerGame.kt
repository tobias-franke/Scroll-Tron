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
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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

internal fun format1Dec(v: Float): String {
    val rounded10 = kotlin.math.round(kotlin.math.abs(v) * 10).toInt()
    val sign = if (v < 0f && rounded10 > 0) "-" else ""
    return "$sign${rounded10 / 10}.${rounded10 % 10}"
}

// ---------------------------------------------------------------------------
// Collision helpers (same as singleplayer)
// ---------------------------------------------------------------------------

private fun cross(ux: Float, uy: Float, vx: Float, vy: Float) = ux * vy - uy * vx

private fun segmentsIntersect(
    p1: Point, p2: Point,
    q1: Point, q2: Point,
): Boolean {
    val pMinX = if (p1.x < p2.x) p1.x else p2.x
    val pMaxX = if (p1.x > p2.x) p1.x else p2.x
    val qMinX = if (q1.x < q2.x) q1.x else q2.x
    val qMaxX = if (q1.x > q2.x) q1.x else q2.x
    if (pMaxX < qMinX - 1e-4f || pMinX > qMaxX + 1e-4f) return false

    val pMinY = if (p1.y < p2.y) p1.y else p2.y
    val pMaxY = if (p1.y > p2.y) p1.y else p2.y
    val qMinY = if (q1.y < q2.y) q1.y else q2.y
    val qMaxY = if (q1.y > q2.y) q1.y else q2.y
    if (pMaxY < qMinY - 1e-4f || pMinY > qMaxY + 1e-4f) return false

    val rx = p2.x - p1.x;  val ry = p2.y - p1.y
    val sx = q2.x - q1.x;  val sy = q2.y - q1.y
    val denom = cross(rx, ry, sx, sy)
    val dx = q1.x - p1.x;  val dy = q1.y - p1.y
    
    if (abs(denom) < 1e-6f) {
        // Parallel or collinear
        if (abs(cross(dx, dy, rx, ry)) < 1e-6f) {
            // Collinear: Check bounding box overlap
            return max(pMinX, qMinX) <= min(pMaxX, qMaxX) + 1e-4f &&
                   max(pMinY, qMinY) <= min(pMaxY, qMaxY) + 1e-4f
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

internal fun mpInitialState(numPlayers: Int, aiCount: Int = 0): MultiplayerGameState {
    val gridStep = 60f
    val w = GAME_WIDTH
    val h = GAME_HEIGHT

    val startPositions = listOf(
        Point(w * 0.25f, h * 0.5f) to 0f,                           // P1: left quarter, right
        Point(w * 0.75f, h * 0.5f) to kotlin.math.PI.toFloat(),    // P2: right quarter, left
        Point(w * 0.5f, h * 0.25f) to (kotlin.math.PI / 2).toFloat(), // P3: top quarter, down
        Point(w * 0.5f, h * 0.75f) to (-kotlin.math.PI / 2).toFloat(), // P4: bottom quarter, up
    )

    val botStartIndex = numPlayers - aiCount
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
            isDead = false,
            isBot = i >= botStartIndex,
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
    playerIndex: Int = -1,
    grid: SpatialGrid? = null,
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
    val segIdx = state.trail.lastIndex

    // Wall collision
    val wallHit = newPos.x < 0 || newPos.x > GAME_WIDTH ||
                  newPos.y < 0 || newPos.y > GAME_HEIGHT

    var collision = false
    if (!wallHit) {
        if (grid != null && playerIndex >= 0) {
            val sMinX = min(newSegment.start.x, newSegment.end.x)
            val sMaxX = max(newSegment.start.x, newSegment.end.x)
            val sMinY = min(newSegment.start.y, newSegment.end.y)
            val sMaxY = max(newSegment.start.y, newSegment.end.y)

            val cMinX = (sMinX / grid.cellSize).toInt().coerceIn(0, grid.cols - 1)
            val cMaxX = (sMaxX / grid.cellSize).toInt().coerceIn(0, grid.cols - 1)
            val cMinY = (sMinY / grid.cellSize).toInt().coerceIn(0, grid.rows - 1)
            val cMaxY = (sMaxY / grid.cellSize).toInt().coerceIn(0, grid.rows - 1)

            val selfSafeLimit = segIdx - SKIP_SEGMENTS

            outer@ for (cy in cMinY..cMaxY) {
                val offset = cy * grid.cols
                for (cx in cMinX..cMaxX) {
                    val cell = grid.cells[offset + cx]
                    for (k in cell.indices) {
                        val item = cell[k]
                        if (item.playerIndex == playerIndex && item.segmentIndex >= selfSafeLimit) {
                            continue
                        }
                        if (segmentsIntersect(newSegment.start, newSegment.end, item.seg.start, item.seg.end)) {
                            collision = true
                            break@outer
                        }
                    }
                }
            }
            grid.addSegment(newSegment, playerIndex, segIdx)
        } else {
            // Fallback trail checking
            val endIdx = state.trail.size - SKIP_SEGMENTS - 1
            for (i in 0..endIdx) {
                val seg = state.trail[i]
                if (segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end)) {
                    collision = true
                    break
                }
            }
            if (!collision) {
                for (trail in allTrails) {
                    if (trail === state.trail) continue
                    for (i in 0 until trail.size) {
                        val seg = trail[i]
                        if (segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end)) {
                            collision = true
                            break
                        }
                    }
                    if (collision) break
                }
            }
        }
    }

    return state.copy(
        position = newPos,
        angle = newAngle,
        angularVelocity = newAngVel,
        isDead = wallHit || collision,
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

internal fun stepMultiplayer(
    state: MultiplayerGameState,
    grid: SpatialGrid? = null,
): MultiplayerGameState {
    if (state.winner != null) return state

    val allTrails = state.players.map { it.trail }
    val newPlayers = state.players.mapIndexed { i, p -> stepPlayer(p, allTrails, i, grid) }

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

private fun DrawScope.drawTrail(path: Path, trailColor: Color) {
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
                isDead = p.isDead,
                isBot = p.isBot,
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
            isDead = pData.isDead,
            isBot = pData.isBot,
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
// Incrementally cached, simplified Skia trail paths
// ---------------------------------------------------------------------------

private class CachedTrailPath {
    val path = Path()
    var lastCommittedX = 0f
    var lastCommittedY = 0f
    var lastCommittedAngle = 0f
    var segmentCount = 0

    fun reset() {
        path.reset()
        lastCommittedX = 0f
        lastCommittedY = 0f
        lastCommittedAngle = 0f
        segmentCount = 0
    }

    fun addSegment(seg: LineSegment, playerAngle: Float) {
        if (segmentCount == 0) {
            path.moveTo(seg.start.x, seg.start.y)
            path.lineTo(seg.end.x, seg.end.y)
            lastCommittedX = seg.end.x
            lastCommittedY = seg.end.y
            lastCommittedAngle = playerAngle
            segmentCount = 1
            return
        }

        val dx = seg.end.x - lastCommittedX
        val dy = seg.end.y - lastCommittedY
        val distSq = dx * dx + dy * dy

        var angleDiff = abs(playerAngle - lastCommittedAngle)
        if (angleDiff > kotlin.math.PI.toFloat()) {
            angleDiff = (2 * kotlin.math.PI).toFloat() - angleDiff
        }

        // Commit a new vertex if:
        // 1. Turned noticeably by >= ~2.6 degrees (0.045 rad)
        // 2. Traveled >= 24px in a straight line (distSq >= 576f)
        if (angleDiff >= 0.045f || distSq >= 576f) {
            path.lineTo(seg.end.x, seg.end.y)
            lastCommittedX = seg.end.x
            lastCommittedY = seg.end.y
            lastCommittedAngle = playerAngle
        }
        segmentCount++
    }

    fun finishTrail(lastPos: Point) {
        if (segmentCount > 0 && (lastCommittedX != lastPos.x || lastCommittedY != lastPos.y)) {
            path.lineTo(lastPos.x, lastPos.y)
            lastCommittedX = lastPos.x
            lastCommittedY = lastPos.y
        }
    }
}

// ---------------------------------------------------------------------------
// Multiplayer Game composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultiplayerGame(
    connector: MultiplayerConnector,
    isHost: Boolean,
    aiCount: Int = 0,
    onBack: () -> Unit,
) {
    var myPlayerIndex by remember { mutableStateOf(if (isHost) 0 else -1) }
    var mpState by remember { mutableStateOf(mpInitialState(1)) } // Start with 1, will reset
    var readyPlayers by remember { mutableStateOf(setOf<Int>()) }
    var gameStarted by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var connectionLost by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(60) }
    var tps by remember { mutableStateOf(60) }
    var showDebugOverlay by remember { mutableStateOf(true) }

    val spatialGrid = remember { SpatialGrid() }
    val trailCaches = remember { mutableMapOf<Int, CachedTrailPath>() }

    val resetRoundResources = {
        spatialGrid.clear()
        trailCaches.clear()
    }

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
            val totalPlayers = minOf(4, connector.connectedPlayers + aiCount)
            resetRoundResources()
            mpState = mpInitialState(totalPlayers, aiCount)
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
                resetRoundResources()
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
                                isDead = p.isDead,
                                isBot = p.isBot,
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
            val humanIndices = mpState.players.indices.filter { !mpState.players[it].isBot }
            if (isHost && humanIndices.isNotEmpty() && humanIndices.all { readyPlayers.contains(it) }) {
                // Everyone is ready, start!
                resetRoundResources()
                mpState = mpInitialState(mpState.players.size, aiCount)
                readyPlayers = emptySet()
                connector.sendGameStart(GAME_WIDTH, GAME_HEIGHT)
            }
        }
    }

    // Game loop (host runs physics and steers bots, guest predicts locally)
    LaunchedEffect(gameStarted) {
        if (!gameStarted) return@LaunchedEffect
        var lastFrame = 0L
        var frameCount = 0L
        var lastStatNanos = 0L
        var frameCounter = 0
        var tickCounter = 0
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastStatNanos == 0L) lastStatNanos = nanos
                frameCounter++
                val statElapsed = nanos - lastStatNanos
                if (statElapsed >= 500_000_000L) {
                    val sec = statElapsed / 1_000_000_000.0
                    fps = (frameCounter / sec).roundToInt()
                    tps = (tickCounter / sec).roundToInt()
                    frameCounter = 0
                    tickCounter = 0
                    lastStatNanos = nanos
                }

                if (lastFrame == 0L) { lastFrame = nanos; return@withFrameNanos }
                val elapsed = (nanos - lastFrame) / 1_000_000L
                if (elapsed >= 14L && !connectionLost) {
                    lastFrame = nanos
                    frameCount++
                    tickCounter++
                    if (isHost) {
                        // Steer AI bots before stepping physics
                        var stateWithAi = mpState
                        if (stateWithAi.winner == null) {
                            val updatedPlayers = stateWithAi.players.toMutableList()
                            var changed = false
                            val aliveBotIndices = updatedPlayers.indices.filter { updatedPlayers[it].isBot && !updatedPlayers[it].isDead }
                            val numAliveBots = aliveBotIndices.size
                            for (k in aliveBotIndices.indices) {
                                val botIdx = aliveBotIndices[k]
                                val shouldEvaluate = numAliveBots <= 1 || (frameCount % numAliveBots == k.toLong())
                                val p = updatedPlayers[botIdx]
                                val impulse = if (shouldEvaluate) {
                                    computeAiSteering(botIdx, stateWithAi, grid = spatialGrid)
                                } else 0f
                                if (impulse != 0f) {
                                    updatedPlayers[botIdx] = p.copy(angularVelocity = p.angularVelocity + impulse)
                                    changed = true
                                }
                            }
                            if (changed) {
                                stateWithAi = stateWithAi.copy(players = updatedPlayers)
                            }
                        }

                        // Host steps physics (authoritative)
                        mpState = stepMultiplayer(stateWithAi, spatialGrid)

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
            val humanIndices = mpState.players.indices.filter { !mpState.players[it].isBot }
            if (isHost && humanIndices.isNotEmpty() && humanIndices.all { readyPlayers.contains(it) }) {
                // Everyone is ready, start!
                resetRoundResources()
                mpState = mpInitialState(mpState.players.size, aiCount)
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
                    Key.F3, Key.D -> {
                        showDebugOverlay = !showDebugOverlay
                        true
                    }
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
                        val trail = player.trail
                        val cachedTrail = trailCaches.getOrPut(i) { CachedTrailPath() }

                        if (trail.isEmpty()) {
                            cachedTrail.reset()
                        } else if (cachedTrail.segmentCount == 0 || cachedTrail.segmentCount > trail.size) {
                            cachedTrail.reset()
                            for (s in trail) {
                                cachedTrail.addSegment(s, player.angle)
                            }
                        } else if (cachedTrail.segmentCount < trail.size) {
                            for (k in cachedTrail.segmentCount until trail.size) {
                                cachedTrail.addSegment(trail[k], player.angle)
                            }
                        }
                        if (player.isDead) {
                            cachedTrail.finishTrail(player.position)
                        }

                        drawTrail(cachedTrail.path, color)

                        // Connect uncommitted tip to current head
                        if (cachedTrail.segmentCount > 0 && !player.isDead) {
                            val headX = player.position.x
                            val headY = player.position.y
                            if (cachedTrail.lastCommittedX != headX || cachedTrail.lastCommittedY != headY) {
                                val start = Offset(cachedTrail.lastCommittedX, cachedTrail.lastCommittedY)
                                val end = Offset(headX, headY)
                                drawLine(
                                    color = color.copy(alpha = 0.35f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 8f,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = color,
                                    start = start,
                                    end = end,
                                    strokeWidth = 2.5f,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }

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
                        val label = when {
                            isMe -> "$colorName (YOU)"
                            player.isBot -> "$colorName [BOT]"
                            else -> colorName
                        }
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

                        val winnerPlayer = mpState.winner?.let { mpState.players.getOrNull(it.ordinal) }
                        val winnerColorName = mpState.winner?.let { PLAYER_COLOR_NAMES.getOrNull(it.ordinal) } ?: "OPPONENT"
                        val title = when {
                            connectionLost -> "CONNECTION LOST"
                            mpState.winner == myPlayerId -> "YOU WIN"
                            winnerPlayer?.isBot == true -> "$winnerColorName [BOT] WINS"
                            mpState.winner != null -> "$winnerColorName WINS"
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

                    // Debug overlay HUD (top-right)
                    if (showDebugOverlay) {
                        val myPlayer = mpState.players.getOrNull(if (myPlayerIndex in mpState.players.indices) myPlayerIndex else 0)
                        val angVel = myPlayer?.angularVelocity ?: 0f
                        val degPerSec = angVel * (180f / kotlin.math.PI.toFloat()) * tps
                        val linearSpeedSec = (SPEED * tps).roundToInt()
                        val totalSegs = mpState.players.sumOf { it.trail.size }
                        val botCount = mpState.players.count { it.isBot }
                        val aliveBots = mpState.players.count { it.isBot && !it.isDead }

                        val fpsColor = when {
                            fps >= 55 -> Color(0xFF39FF14)
                            fps >= 30 -> Color(0xFFFFCC00)
                            else -> Color(0xFFFF3333)
                        }
                        val tpsColor = when {
                            tps >= 55 -> Color(0xFF39FF14)
                            tps >= 30 -> Color(0xFFFFCC00)
                            else -> Color(0xFFFF3333)
                        }

                        val headerStyle = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = gameFont,
                            color = Color(0xFF00FFCC),
                        )
                        val labelStyle = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF88AA99),
                        )

                        class StatEntry(val label: String, val value: String, val valueColor: Color)
                        val statList = mutableListOf(
                            StatEntry("FPS", "$fps", fpsColor),
                            StatEntry("TPS", "$tps", tpsColor),
                            StatEntry("SPEED", "$linearSpeedSec px/s (3.0 px/t)", Color(0xFFE0FFEE)),
                            StatEntry("ANG VEL", "${format1Dec(angVel)} rad/t (${format1Dec(degPerSec)}°/s)", Color(0xFFE0FFEE)),
                            StatEntry("TRAILS", "$totalSegs segs", Color(0xFFE0FFEE)),
                        )
                        if (botCount > 0) {
                            statList.add(StatEntry("BOTS", "$aliveBots/$botCount alive", Color(0xFFE0FFEE)))
                        }

                        val headerMeasured = textMeasurer.measure("DEBUG STATS [F3 / D]", headerStyle)
                        val measuredEntries = statList.map { entry ->
                            val labelM = textMeasurer.measure(entry.label.padEnd(9), labelStyle)
                            val valueM = textMeasurer.measure(entry.value, labelStyle.copy(color = entry.valueColor))
                            Triple(labelM, valueM, labelM.size.width + valueM.size.width)
                        }

                        val maxContentWidth = maxOf(
                            headerMeasured.size.width.toFloat(),
                            (measuredEntries.maxOfOrNull { it.third } ?: 0).toFloat()
                        )
                        val boxPadding = 24f
                        val panelWidth = maxOf(520f, maxContentWidth + boxPadding * 2)
                        val lineHeight = 32f
                        val panelHeight = boxPadding * 2 + headerMeasured.size.height + 16f + (statList.size * lineHeight)
                        val panelX = GAME_WIDTH - pad - panelWidth
                        val panelY = pad + topInset

                        // Background panel
                        drawRoundRect(
                            color = Color(0xDD05100B),
                            topLeft = Offset(panelX, panelY),
                            size = Size(panelWidth, panelHeight),
                            cornerRadius = CornerRadius(12f, 12f),
                        )
                        drawRoundRect(
                            color = Color(0x6600FFCC),
                            topLeft = Offset(panelX, panelY),
                            size = Size(panelWidth, panelHeight),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = Stroke(width = 2f),
                        )

                        // Title
                        drawText(headerMeasured, topLeft = Offset(panelX + boxPadding, panelY + boxPadding))

                        // Divider line
                        val dividerY = panelY + boxPadding + headerMeasured.size.height + 8f
                        drawLine(
                            color = Color(0x4400FFCC),
                            start = Offset(panelX + boxPadding, dividerY),
                            end = Offset(panelX + panelWidth - boxPadding, dividerY),
                            strokeWidth = 1.5f,
                        )

                        // Rows
                        var textY = dividerY + 12f
                        for ((labelM, valueM, _) in measuredEntries) {
                            drawText(labelM, topLeft = Offset(panelX + boxPadding, textY))
                            drawText(valueM, topLeft = Offset(panelX + boxPadding + labelM.size.width, textY))
                            textY += lineHeight
                        }
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
                            val humanCount = maxOf(1, mpState.players.count { !it.isBot })
                            val isReady = readyPlayers.contains(myPlayerIndex)
                            val rematchColor = if (isReady) Color(0xFFAAAAAA) else NEON_LIME
                            val rematchText = if (isReady) "WAITING (${readyPlayers.size}/$humanCount)" else "REMATCH"
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
