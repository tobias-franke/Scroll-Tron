package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Neon palette — each life picks a hue-offset for the trail
// ---------------------------------------------------------------------------

private val NEON_CYAN   = Color(0xFF00FFFF)
private val NEON_PINK   = Color(0xFFFF00FF)
private val NEON_LIME   = Color(0xFF39FF14)
private val NEON_ORANGE = Color(0xFFFF6600)
private val TRAIL_COLORS = listOf(NEON_CYAN, NEON_PINK, NEON_LIME, NEON_ORANGE)
private var colorIndex = 0

private fun nextTrailColor() = TRAIL_COLORS[colorIndex++ % TRAIL_COLORS.size]

// ---------------------------------------------------------------------------
// Collision helpers (parametric segment-segment intersection)
// ---------------------------------------------------------------------------

/** Cross product of 2-D vectors (u × v). */
private fun cross(ux: Float, uy: Float, vx: Float, vy: Float) = ux * vy - uy * vx

/**
 * Returns true if segment [p1→p2] intersects [q1→q2].
 * Uses the standard parametric formula:
 *   t = (q1 - p1) × s / (r × s)
 *   u = (q1 - p1) × r / (r × s)
 *   intersection iff 0 ≤ t ≤ 1 and 0 ≤ u ≤ 1
 */
private fun segmentsIntersect(
    p1: Point, p2: Point,
    q1: Point, q2: Point,
): Boolean {
    val rx = p2.x - p1.x;  val ry = p2.y - p1.y
    val sx = q2.x - q1.x;  val sy = q2.y - q1.y
    val denom = cross(rx, ry, sx, sy)
    if (abs(denom) < 1e-6f) return false          // parallel / collinear
    val dx = q1.x - p1.x;  val dy = q1.y - p1.y
    val t = cross(dx, dy, sx, sy) / denom
    val u = cross(dx, dy, rx, ry) / denom
    return t in 0f..1f && u in 0f..1f
}

// ---------------------------------------------------------------------------
// Initial state factory
// ---------------------------------------------------------------------------

private fun initialState(canvasWidth: Float, canvasHeight: Float): GameState {
    val gridStep = 60f
    val rawCx = if (canvasWidth  > 0f) canvasWidth  / 2f else 400f
    val rawCy = if (canvasHeight > 0f) canvasHeight / 2f else 300f
    // Snap to the nearest grid intersection
    val cx = kotlin.math.round(rawCx / gridStep) * gridStep
    val cy = kotlin.math.round(rawCy / gridStep) * gridStep
    return GameState(
        position        = Point(cx, cy),
        angle           = 0f,      // heading right
        angularVelocity = 0f,
        trail           = emptyList(),
        isDead          = false,
    )
}

// ---------------------------------------------------------------------------
// One frame of physics + collision
// ---------------------------------------------------------------------------

private fun stepGame(state: GameState, canvasWidth: Float, canvasHeight: Float): GameState {
    if (state.isDead) return state

    // 1. Apply angular velocity → update angle → decay
    val newAngle = state.angle + state.angularVelocity
    val newAngVel = state.angularVelocity * ANGULAR_DECAY

    // 2. Move forward
    val dx = SPEED * cos(newAngle)
    val dy = SPEED * sin(newAngle)
    val oldPos = state.position
    val newPos = Point(oldPos.x + dx, oldPos.y + dy)

    // 3. Build new segment
    val newSegment = LineSegment(oldPos, newPos)
    val newTrail = state.trail + newSegment

    // 4. Wall collision
    val wallHit = newPos.x < 0 || newPos.x > canvasWidth ||
                  newPos.y < 0 || newPos.y > canvasHeight

    // 5. Self-collision — check new segment vs all but last SKIP_SEGMENTS
    val selfHit = if (newTrail.size > SKIP_SEGMENTS) {
        val safeTrail = newTrail.dropLast(SKIP_SEGMENTS)
        safeTrail.any { seg -> segmentsIntersect(newSegment.start, newSegment.end, seg.start, seg.end) }
    } else false

    return state.copy(
        position        = newPos,
        angle           = newAngle,
        angularVelocity = newAngVel,
        trail           = newTrail,
        isDead          = wallHit || selfHit,
    )
}

// ---------------------------------------------------------------------------
// Rendering helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawTrail(trail: List<LineSegment>, trailColor: Color) {
    if (trail.isEmpty()) return

    val total = trail.size.toFloat()
    trail.forEachIndexed { i, seg ->
        // Fade older segments slightly; recent ones are fully bright
        val alpha = 0.35f + 0.65f * (i / total)
        // Glow: draw twice — wide+dim then narrow+bright
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
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(trailColor.copy(alpha = 0.7f), Color.Transparent),
            center = Offset(pos.x, pos.y),
            radius = 18f,
        ),
        radius = 18f,
        center = Offset(pos.x, pos.y),
    )
    // Triangle pointing in the direction of travel
    rotate(degrees = angleDeg, pivot = Offset(pos.x, pos.y)) {
        val path = Path().apply {
            moveTo(pos.x + 10f, pos.y)
            lineTo(pos.x -  7f, pos.y -  6f)
            lineTo(pos.x -  7f, pos.y +  6f)
            close()
        }
        drawPath(path, color = Color.White)
        drawPath(
            path,
            color = trailColor,
            style = Stroke(width = 1.5f),
        )
    }
}

private fun DrawScope.drawGrid() {
    val step = 60f
    val lineColor = Color(0xFF0D2A0D)
    var x = 0f
    while (x <= size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawBorder(trailColor: Color) {
    val strokeWidth = 3f
    val inset = strokeWidth / 2f
    drawRect(
        color = trailColor.copy(alpha = 0.6f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        style = Stroke(width = strokeWidth),
    )
}

private fun DrawScope.drawDeadOverlay(textMeasurer: androidx.compose.ui.text.TextMeasurer) {
    // Dim overlay
    drawRect(Color(0xCC000000))

    val title = "SYSTEM FAILURE"
    val sub   = "Press  R  to restart"

    val titleMeasured = textMeasurer.measure(
        title,
        TextStyle(
            fontSize   = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color      = NEON_PINK,
        ),
    )
    val subMeasured = textMeasurer.measure(
        sub,
        TextStyle(
            fontSize   = 20.sp,
            fontFamily = FontFamily.Monospace,
            color      = Color(0xFFAAAAAA),
        ),
    )

    val cx = size.width  / 2f
    val cy = size.height / 2f

    drawText(
        titleMeasured,
        topLeft = Offset(cx - titleMeasured.size.width / 2f, cy - 60f),
    )
    drawText(
        subMeasured,
        topLeft = Offset(cx - subMeasured.size.width / 2f, cy + 20f),
    )
}

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun App(onExit: () -> Unit = {}) {
    // Canvas size is set on first layout pass. Use mutableStateOf so recompose
    // triggers when it's first known.
    var canvasWidth  by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    var gameState  by remember { mutableStateOf(initialState(canvasWidth, canvasHeight)) }
    var trailColor by remember { mutableStateOf(nextTrailColor()) }

    val focusRequester = remember { FocusRequester() }
    val textMeasurer   = rememberTextMeasurer()

    // Re-initialise when canvas size becomes known for the first time
    LaunchedEffect(canvasWidth, canvasHeight) {
        if (canvasWidth > 0f && canvasHeight > 0f && gameState.trail.isEmpty() && !gameState.isDead) {
            gameState = initialState(canvasWidth, canvasHeight)
        }
    }

    // 60-FPS game loop
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                if (lastFrame == 0L) { lastFrame = nanos; return@withFrameNanos }
                val elapsed = (nanos - lastFrame) / 1_000_000L  // ms
                if (elapsed >= 14L) {                             // ~60 fps
                    lastFrame = nanos
                    if (canvasWidth > 0f && canvasHeight > 0f) {
                        gameState = stepGame(gameState, canvasWidth, canvasHeight)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            // Key bindings
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Escape -> { onExit(); true }
                    Key.R -> if (gameState.isDead) {
                        trailColor = nextTrailColor()
                        gameState  = initialState(canvasWidth, canvasHeight)
                        true
                    } else false
                    else -> false
                }
            }
            // Scroll-wheel steering
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (!gameState.isDead && delta != 0f) {
                    val sign = if (delta > 0f) 1f else -1f
                    gameState = gameState.copy(
                        angularVelocity = gameState.angularVelocity + sign * STEERING_SENSITIVITY
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Capture canvas size
            if (canvasWidth  != size.width)  canvasWidth  = size.width
            if (canvasHeight != size.height) canvasHeight = size.height

            // Background
            drawRect(Color(0xFF020C02))
            drawGrid()
            drawBorder(trailColor)

            // Trail
            drawTrail(gameState.trail, trailColor)

            // Head
            if (!gameState.isDead) {
                val angleDeg = (gameState.angle * (180.0 / kotlin.math.PI)).toFloat()
                drawHead(gameState.position, angleDeg, trailColor)
            }

            // Death overlay
            if (gameState.isDead) {
                drawDeadOverlay(textMeasurer)
            }
        }
    }

    // Grab keyboard focus so onKeyEvent works immediately
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}