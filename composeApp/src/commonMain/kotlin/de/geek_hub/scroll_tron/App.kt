package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.tooling.preview.Preview
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
// 🎵 Easter egg — every 5th death reveals a lyric (shh, don't tell anyone)
// ---------------------------------------------------------------------------

private val RICK_LINES = listOf(
    "Never gonna give you up",
    "Never gonna let you down",
    "Never gonna run around and desert you",
    "Never gonna make you cry",
    "Never gonna say goodbye",
    "Never gonna tell a lie and hurt you",
)

private fun DrawScope.drawRickRollOverlay(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    deathCount: Int,
    score: Int,
    highScore: Int,
    gameFont: FontFamily,
    scaleFactor: Float,
    onLyricBounds: (Rect) -> Unit,
) {
    drawRect(Color(0xCC000000))

    val lyric     = RICK_LINES[(deathCount / 5 - 1) % RICK_LINES.size]
    val isNewBest = score > 0 && score >= highScore
    val scoreLine = if (isNewBest) "NEW BEST: $score" else "SCORE: $score"
    val bestLine  = if (isNewBest) ""                   else "BEST:  $highScore"

    val lyricMeasured = textMeasurer.measure(
        lyric,
        TextStyle(
            fontSize   = (32 / scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = gameFont,
            color      = NEON_CYAN,
        ),
    )
    val scoreMeasured = textMeasurer.measure(
        scoreLine,
        TextStyle(
            fontSize   = (22 / scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = gameFont,
            color      = if (isNewBest) NEON_LIME else Color(0xFFEEEEEE),
        ),
    )
    val bestMeasured = if (bestLine.isNotEmpty()) textMeasurer.measure(
        bestLine,
        TextStyle(
            fontSize   = (16 / scaleFactor).sp,
            fontFamily = gameFont,
            color      = Color(0xFF666666),
        ),
    ) else null

    val cx     = size.width  / 2f
    val cy     = size.height / 2f
    val gap    = 12f / scaleFactor  // vertical margin between lines

    // Compute total block height so we can centre it vertically
    val lyricH = lyricMeasured.size.height.toFloat()
    val scoreH = scoreMeasured.size.height.toFloat()
    val bestH  = bestMeasured?.size?.height?.toFloat() ?: 0f
    val totalH = lyricH + gap + scoreH + if (bestMeasured != null) gap + bestH else 0f
    var cursorY = cy - totalH / 2f

    // Lyric
    val lyricW = lyricMeasured.size.width.toFloat()
    val lyricX = cx - lyricW / 2f
    onLyricBounds(Rect(lyricX, cursorY, lyricX + lyricW, cursorY + lyricH))
    drawText(lyricMeasured, topLeft = Offset(lyricX, cursorY))
    cursorY += lyricH + gap

    // Score
    drawText(scoreMeasured,
        topLeft = Offset(cx - scoreMeasured.size.width / 2f, cursorY))
    cursorY += scoreH + gap

    // Best (optional)
    if (bestMeasured != null) drawText(bestMeasured,
        topLeft = Offset(cx - bestMeasured.size.width / 2f, cursorY))
}

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

private fun DrawScope.drawScoreHud(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    score: Int,
    highScore: Int,
    trailColor: Color,
    gameFont: FontFamily,
    scaleFactor: Float,
) {
    val scoreText = "SCORE  " + score.toString().padStart(6, '0')
    val hiText    = "BEST   " + highScore.toString().padStart(6, '0')

    val scoreMeasured = textMeasurer.measure(
        scoreText,
        TextStyle(
            fontSize   = (14 / scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color      = trailColor,
        ),
    )
    val hiMeasured = textMeasurer.measure(
        hiText,
        TextStyle(
            fontSize   = (14 / scaleFactor).sp,
            fontFamily = FontFamily.Monospace,
            color      = Color(0xFF666666),
        ),
    )

    val padding   = 16f / scaleFactor
    val topInset  = 48f / scaleFactor
    val scoreW    = scoreMeasured.multiParagraph.width
    val scoreH    = scoreMeasured.multiParagraph.height
    val hiW       = hiMeasured.multiParagraph.width
    val lineH     = scoreH + 4f

    // Semi-transparent pill background
    val boxW = maxOf(scoreW, hiW) + padding * 2
    val boxH = lineH * 2 + padding
    drawRect(
        color   = Color(0xCC000000),
        topLeft = Offset(size.width - boxW - padding, padding + topInset),
        size    = Size(boxW, boxH),
    )

    drawText(
        scoreMeasured,
        topLeft = Offset(size.width - scoreW - padding * 2, padding + topInset + 6f),
    )
    drawText(
        hiMeasured,
        topLeft = Offset(size.width - hiW - padding * 2, padding + topInset + 6f + lineH),
    )
}

private fun DrawScope.drawDeadOverlay(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    score: Int,
    highScore: Int,
    gameFont: FontFamily,
    scaleFactor: Float,
) {
    // Dim overlay
    drawRect(Color(0xCC000000))

    val isNewBest = score > 0 && score >= highScore

    val title      = "SYSTEM FAILURE"
    val scoreLine  = if (isNewBest) "NEW BEST: $score" else "SCORE: $score"
    val bestLine   = if (isNewBest) ""                    else "BEST:  $highScore"

    val titleMeasured = textMeasurer.measure(
        title,
        TextStyle(
            fontSize   = (48 / scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = gameFont,
            color      = NEON_PINK,
        ),
    )
    val scoreMeasured = textMeasurer.measure(
        scoreLine,
        TextStyle(
            fontSize   = (22 / scaleFactor).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = gameFont,
            color      = if (isNewBest) NEON_LIME else Color(0xFFEEEEEE),
        ),
    )
    val bestMeasured = if (bestLine.isNotEmpty()) textMeasurer.measure(
        bestLine,
        TextStyle(
            fontSize   = (16 / scaleFactor).sp,
            fontFamily = gameFont,
            color      = Color(0xFF666666),
        ),
    ) else null

    val cx  = size.width  / 2f
    val cy  = size.height / 2f
    val gap = 12f / scaleFactor  // vertical margin between lines

    // Compute total block height so we can centre it vertically
    val titleH = titleMeasured.size.height.toFloat()
    val scoreH = scoreMeasured.size.height.toFloat()
    val bestH  = bestMeasured?.size?.height?.toFloat() ?: 0f
    val totalH = titleH + gap + scoreH + if (bestMeasured != null) gap + bestH else 0f
    var cursorY = cy - totalH / 2f

    // Title
    drawText(titleMeasured,
        topLeft = Offset(cx - titleMeasured.size.width / 2f, cursorY))
    cursorY += titleH + gap

    // Score
    drawText(scoreMeasured,
        topLeft = Offset(cx - scoreMeasured.size.width / 2f, cursorY))
    cursorY += scoreH + gap

    // Best (optional)
    if (bestMeasured != null) drawText(bestMeasured,
        topLeft = Offset(cx - bestMeasured.size.width / 2f, cursorY))
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

    var gameState     by remember { mutableStateOf(initialState(canvasWidth, canvasHeight)) }
    var trailColor    by remember { mutableStateOf(nextTrailColor()) }
    var highScore     by remember { mutableStateOf(0) }
    var deathCount    by remember { mutableStateOf(0) }
    var rickLyricRect  by remember { mutableStateOf<Rect?>(null) }
    var isHoveringLyric by remember { mutableStateOf(false) }
    var showHint        by remember { mutableStateOf(true) }

    val doRestart: () -> Unit = {
        trailColor = nextTrailColor()
        gameState  = initialState(canvasWidth, canvasHeight)
    }

    val focusRequester = remember { FocusRequester() }
    val textMeasurer   = rememberTextMeasurer()
    val gameFont = FontFamily(
        Font(Res.font.orbitron_regular, FontWeight.Normal),
        Font(Res.font.orbitron_bold, FontWeight.Bold),
    )

    // Re-initialise when canvas size becomes known for the first time
    var hasInitialisedWithSize by remember { mutableStateOf(false) }
    LaunchedEffect(canvasWidth, canvasHeight) {
        if (canvasWidth > 0f && canvasHeight > 0f && !hasInitialisedWithSize) {
            hasInitialisedWithSize = true
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
                        val prev = gameState
                        gameState = stepGame(prev, canvasWidth, canvasHeight)
                        // Update highscore and death counter at the moment of death
                        if (!prev.isDead && gameState.isDead) {
                            deathCount++
                            val score = gameState.trail.size
                            if (score > highScore) highScore = score
                        }
                    }
                }
            }
        }
    }

    val scaleFactor = getPlatformScaleFactor()

    BoxWithConstraints(
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
                        doRestart()
                        true
                    } else false
                    else -> false
                }
            }
            // Scroll-wheel steering
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (!gameState.isDead && delta != 0f) {
                    showHint = false
                    val sign = if (delta > 0f) 1f else -1f
                    gameState = gameState.copy(
                        angularVelocity = gameState.angularVelocity + sign * STEERING_SENSITIVITY
                    )
                }
            }
            // Rick Roll click — only active when the easter egg overlay is showing
            .onPointerEvent(PointerEventType.Move) { event ->
                val pos  = event.changes.firstOrNull()?.position
                val rect = rickLyricRect
                isHoveringLyric = gameState.isDead && deathCount % 5 == 0
                    && rect != null && pos != null && rect.contains(pos)
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                val rect = rickLyricRect
                if (gameState.isDead && deathCount % 5 == 0 && rect != null && rect.contains(pos)) {
                    openUrl("https://youtu.be/dQw4w9WgXcQ")
                }
            }
            .pointerHoverIcon(
                if (isHoveringLyric) PointerIcon.Hand else PointerIcon.Default,
                overrideDescendants = true,
            ),
    ) {
        val logicalWidth = maxWidth / scaleFactor
        val logicalHeight = maxHeight / scaleFactor

        Box(
            modifier = Modifier
                .requiredSize(logicalWidth, logicalHeight)
                .graphicsLayer {
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                    transformOrigin = TransformOrigin(0f, 0f)
                }
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

            // Live score HUD
            drawScoreHud(textMeasurer, gameState.trail.size, highScore, trailColor, gameFont, scaleFactor)

            // First-start hint
            if (showHint && !gameState.isDead) {
                // Pulse alpha between 0.4 and 1.0 based on trail length as a simple frame proxy
                val pulse = 0.4f + 0.6f * ((1f + sin(gameState.trail.size.toFloat() * 0.08f)) / 2f)
                val hintMeasured = textMeasurer.measure(
                    "SCROLL TO STEER",
                    TextStyle(
                        fontSize   = (18 / scaleFactor).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = gameFont,
                        color      = trailColor.copy(alpha = pulse),
                    ),
                )
                val textW = hintMeasured.size.width.toFloat()
                val textH = hintMeasured.size.height.toFloat()
                val arrowGap = 16f / scaleFactor          // space between arrows and text
                val arrowH   = 14f / scaleFactor          // height of each triangle
                val arrowW   = 12f / scaleFactor          // half-width of each triangle
                val arrowSpacing = 6f / scaleFactor       // gap between the two triangles
                val totalW   = arrowW * 2 + arrowGap + textW
                val startX   = size.width / 2f - totalW / 2f
                val textY    = size.height / 2f + 60f
                val arrowCx  = startX + arrowW  // centre-x of arrows
                val arrowCy  = textY + textH / 2f  // vertically centred on text

                val arrowColor = trailColor.copy(alpha = pulse)

                // ▲ up triangle
                val upPath = Path().apply {
                    moveTo(arrowCx, arrowCy - arrowSpacing / 2f - arrowH)
                    lineTo(arrowCx - arrowW, arrowCy - arrowSpacing / 2f)
                    lineTo(arrowCx + arrowW, arrowCy - arrowSpacing / 2f)
                    close()
                }
                drawPath(upPath, color = arrowColor)

                // ▼ down triangle
                val downPath = Path().apply {
                    moveTo(arrowCx, arrowCy + arrowSpacing / 2f + arrowH)
                    lineTo(arrowCx - arrowW, arrowCy + arrowSpacing / 2f)
                    lineTo(arrowCx + arrowW, arrowCy + arrowSpacing / 2f)
                    close()
                }
                drawPath(downPath, color = arrowColor)

                // Text
                drawText(
                    hintMeasured,
                    topLeft = Offset(startX + arrowW * 2 + arrowGap, textY),
                )
            }

            // Death overlay
            if (gameState.isDead) {
                if (deathCount % 5 == 0) {
                    // 🎵 every 5th death: surprise!
                    drawRickRollOverlay(textMeasurer, deathCount, gameState.trail.size, highScore, gameFont, scaleFactor) { rickLyricRect = it }
                } else {
                    rickLyricRect = null
                    drawDeadOverlay(textMeasurer, gameState.trail.size, highScore, gameFont, scaleFactor)
                }
            }
        }

        // Restart button — shown on both death screens, above the LaunchedEffect focus grab
        if (gameState.isDead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = (56 / scaleFactor).dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .border(
                            width = (1 / scaleFactor).dp,
                            color = trailColor,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .clickable { doRestart() }
                        .padding(horizontal = (36 / scaleFactor).dp, vertical = (12 / scaleFactor).dp),
                ) {
                    Text(
                        text       = "RESTART",
                        fontFamily = gameFont,
                        fontWeight = FontWeight.Bold,
                        fontSize   = (16 / scaleFactor).sp,
                        color      = trailColor,
                    )
                }
            }
        }
        
    } // Closes inner scaled Box
    } // Closes BoxWithConstraints

    // Grab keyboard focus initially and re-claim it whenever isDead changes.
    // The restart button (.clickable) steals focus when it appears; this ensures
    // Escape and R always route back to the main Box.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(gameState.isDead) { focusRequester.requestFocus() }
}
