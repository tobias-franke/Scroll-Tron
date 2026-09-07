package de.geek_hub.scroll_tron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.CornerRadius
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
import kotlinx.coroutines.delay
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
        trail           = mutableListOf(),
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

    var minT = 1.0f
    var collision = false

    // Wall collision
    if (dx < 0f && newPos.x < 0f) {
        val t = (-oldPos.x / dx).coerceIn(0f, 1f)
        if (t < minT) { minT = t; collision = true }
    } else if (dx > 0f && newPos.x > canvasWidth) {
        val t = ((canvasWidth - oldPos.x) / dx).coerceIn(0f, 1f)
        if (t < minT) { minT = t; collision = true }
    }
    if (dy < 0f && newPos.y < 0f) {
        val t = (-oldPos.y / dy).coerceIn(0f, 1f)
        if (t < minT) { minT = t; collision = true }
    } else if (dy > 0f && newPos.y > canvasHeight) {
        val t = ((canvasHeight - oldPos.y) / dy).coerceIn(0f, 1f)
        if (t < minT) { minT = t; collision = true }
    }

    // Self-collision
    val endIdx = state.trail.size - SKIP_SEGMENTS - 1
    for (i in 0..endIdx) {
        val seg = state.trail[i]
        val t = intersectionT(oldPos, newPos, seg.start, seg.end)
        if (t != null && t < minT) {
            minT = t
            collision = true
        }
    }

    val finalPos = if (collision) {
        val impactX = oldPos.x + minT * dx
        val impactY = oldPos.y + minT * dy
        val distToImpact = minT * SPEED
        // Back off by up to 1.25f (half stroke width) so the round cap reaches the obstacle centerline and never penetrates beyond
        val backOff = minOf(1.25f, distToImpact * 0.8f)
        val backOffRatio = if (SPEED > 0f) backOff / SPEED else 0f
        Point(
            impactX - backOffRatio * dx,
            impactY - backOffRatio * dy,
        )
    } else {
        newPos
    }

    val finalSegment = LineSegment(oldPos, finalPos)
    state.trail.add(finalSegment)

    return state.copy(
        position        = finalPos,
        angle           = newAngle,
        angularVelocity = newAngVel,
        // trail reference stays the same
        isDead          = collision,
    )
}

// ---------------------------------------------------------------------------
// Rendering helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawTrail(trail: List<LineSegment>, trailColor: Color) {
    if (trail.isEmpty()) return

    val path = Path()
    path.moveTo(trail[0].start.x, trail[0].start.y)
    for (i in trail.indices) {
        path.lineTo(trail[i].end.x, trail[i].end.y)
    }

    // Glow: draw twice — wide+dim then narrow+bright
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
    val scoreValText = score.toString().padStart(5, '0')
    val hiValText    = highScore.toString().padStart(5, '0')

    val labelStyle = TextStyle(
        fontSize   = (10 / scaleFactor).sp,
        fontWeight = FontWeight.Bold,
        fontFamily = gameFont,
        color      = CyberColors.DIM_TEXT,
    )
    val scoreValStyle = TextStyle(
        fontSize   = (16 / scaleFactor).sp,
        fontWeight = FontWeight.Bold,
        fontFamily = gameFont,
        color      = trailColor,
    )
    val hiValStyle = TextStyle(
        fontSize   = (16 / scaleFactor).sp,
        fontWeight = FontWeight.Normal,
        fontFamily = gameFont,
        color      = if (score > 0 && score >= highScore) CyberColors.NEON_LIME else Color(0xFFAAAAAA),
    )

    val scoreLabelM = textMeasurer.measure("SCORE", labelStyle)
    val scoreValM   = textMeasurer.measure(scoreValText, scoreValStyle)
    val hiLabelM    = textMeasurer.measure("RECORD", labelStyle)
    val hiValM      = textMeasurer.measure(hiValText, hiValStyle)

    // Fixed column width for both score boxes so they never resize or jitter
    val colWidth   = 80f / scaleFactor
    val padH       = 14f / scaleFactor
    val padV       = 10f / scaleFactor
    val topInset   = 48f / scaleFactor
    val colGap     = 16f / scaleFactor

    val boxW       = padH * 2 + colWidth * 2 + colGap + 4f / scaleFactor
    val boxH       = padV * 2 + maxOf(scoreLabelM.size.height + scoreValM.size.height, hiLabelM.size.height + hiValM.size.height) + 4f
    val boxX       = size.width - boxW - padH
    val boxY       = padV + topInset

    // Floating transparent cyber glass panel (transparent so trail is clearly visible behind it)
    drawRoundRect(
        color = CyberColors.PANEL_BG.copy(alpha = 0.15f),
        topLeft = Offset(boxX, boxY),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(8f / scaleFactor, 8f / scaleFactor),
    )
    drawRoundRect(
        color = trailColor.copy(alpha = 0.35f),
        topLeft = Offset(boxX, boxY),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(8f / scaleFactor, 8f / scaleFactor),
        style = Stroke(width = 1.2f / scaleFactor),
    )
    // Left glowing accent bar
    drawRoundRect(
        color = trailColor.copy(alpha = 0.5f),
        topLeft = Offset(boxX, boxY),
        size = Size(3.5f / scaleFactor, boxH),
        cornerRadius = CornerRadius(2f / scaleFactor, 2f / scaleFactor),
    )

    // Score column (fixed width, centered)
    val c1X = boxX + padH + 4f / scaleFactor
    val scoreLabelX = c1X + (colWidth - scoreLabelM.size.width) / 2f
    val scoreValX   = c1X + (colWidth - scoreValM.size.width) / 2f
    drawText(scoreLabelM, topLeft = Offset(scoreLabelX, boxY + padV))
    drawText(scoreValM,   topLeft = Offset(scoreValX,   boxY + padV + scoreLabelM.size.height + 2f))

    // Divider
    val divX = c1X + colWidth + (colGap / 2f)
    drawLine(
        color = Color(0x33FFFFFF),
        start = Offset(divX, boxY + padV + 2f),
        end = Offset(divX, boxY + boxH - padV - 2f),
        strokeWidth = 1f,
    )

    // Record column (fixed width, centered)
    val c2X = divX + (colGap / 2f)
    val hiLabelX = c2X + (colWidth - hiLabelM.size.width) / 2f
    val hiValX   = c2X + (colWidth - hiValM.size.width) / 2f
    drawText(hiLabelM, topLeft = Offset(hiLabelX, boxY + padV))
    drawText(hiValM,   topLeft = Offset(hiValX,   boxY + padV + hiLabelM.size.height + 2f))
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
// Singleplayer game composable (extracted from original App)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SingleplayerGame(onBack: () -> Unit = {}) {
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
    val deRezSystem   = remember { DeRezSystem() }
    var animTick      by remember { mutableStateOf(0L) }
    var showEndScreen by remember { mutableStateOf(false) }

    val doRestart: () -> Unit = {
        showEndScreen = false
        trailColor = nextTrailColor()
        gameState  = initialState(canvasWidth, canvasHeight)
        deRezSystem.clear()
        animTick++
        SoundManager.playStart()
    }

    LaunchedEffect(Unit) {
        SoundManager.playStart()
    }

    LaunchedEffect(gameState.isDead) {
        if (gameState.isDead) {
            showEndScreen = false
            delay(700L)
            showEndScreen = true
        } else {
            showEndScreen = false
        }
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
                    if (deRezSystem.hasActive()) {
                        deRezSystem.update()
                        animTick++
                    }
                    if (canvasWidth > 0f && canvasHeight > 0f) {
                        val prev = gameState
                        gameState = stepGame(prev, canvasWidth, canvasHeight)
                        // Update highscore and death counter at the moment of death
                        if (!prev.isDead && gameState.isDead) {
                            SoundManager.playCrash()
                            deathCount++
                            val score = gameState.trail.size
                            if (score > highScore) highScore = score
                            val crashX = gameState.position.x.coerceIn(0f, canvasWidth)
                            val crashY = gameState.position.y.coerceIn(0f, canvasHeight)
                            deRezSystem.triggerExplosion(crashX, crashY, trailColor)
                            animTick++
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
                    Key.Escape -> { onBack(); true }
                    Key.M -> { SoundManager.toggleMute(); true }
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
                focusRequester.requestFocus()
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
            // Track animTick to drive continuous 60fps redraws during crash animation
            val _anim = animTick

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

            // De-rez explosion particles & shockwaves
            deRezSystem.draw(this)

            // Death overlay (shown a few moments later after the crash!)
            if (showEndScreen) {
                if (deathCount % 5 == 0) {
                    // 🎵 every 5th death: surprise!
                    drawRickRollOverlay(textMeasurer, deathCount, gameState.trail.size, highScore, gameFont, scaleFactor) { rickLyricRect = it }
                } else {
                    rickLyricRect = null
                    drawDeadOverlay(textMeasurer, gameState.trail.size, highScore, gameFont, scaleFactor)
                }
            }

            // Live score HUD (fixed width)
            drawScoreHud(textMeasurer, gameState.trail.size, highScore, trailColor, gameFont, scaleFactor)
        }

        // Restart button — shown once the end screen appears
        if (showEndScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = (56 / scaleFactor).dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((16 / scaleFactor).dp),
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = (1 / scaleFactor).dp,
                                color = trailColor,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clickable {
                                doRestart()
                            }
                            .padding(horizontal = (36 / scaleFactor).dp, vertical = (12 / scaleFactor).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = "RESTART",
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize   = (16 / scaleFactor).sp,
                            color      = trailColor,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .border(
                                width = (1 / scaleFactor).dp,
                                color = Color(0xFF666666),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clickable {
                                onBack()
                            }
                            .padding(horizontal = (36 / scaleFactor).dp, vertical = (12 / scaleFactor).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = "MAIN MENU",
                            fontFamily = gameFont,
                            fontWeight = FontWeight.Bold,
                            fontSize   = (16 / scaleFactor).sp,
                            color      = Color(0xFF666666),
                        )
                    }
                }
            }
        }
        
    } // Closes inner scaled Box
    } // Closes BoxWithConstraints

    // Grab keyboard focus initially and re-claim it whenever isDead / showEndScreen changes.
    // The restart button (.clickable) steals focus when it appears; this ensures
    // Escape and R always route back to the main Box.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(showEndScreen) { if (showEndScreen) focusRequester.requestFocus() }
}

// ---------------------------------------------------------------------------
// Top-level App with screen navigation
// ---------------------------------------------------------------------------

@Composable
@Preview
fun App(onExit: () -> Unit = {}) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    var mpConnector by remember { mutableStateOf<MultiplayerConnector?>(null) }
    var mpIsHost by remember { mutableStateOf(false) }

    when (val screen = currentScreen) {
        is Screen.MainMenu -> {
            MainMenu(
                onSingleplayer = { currentScreen = Screen.Singleplayer },
                onMultiplayer  = { currentScreen = Screen.MultiplayerLobby },
                onExit         = onExit,
            )
        }
        is Screen.Singleplayer -> {
            SingleplayerGame(
                onBack = { currentScreen = Screen.MainMenu },
            )
        }
        is Screen.MultiplayerLobby -> {
            MultiplayerLobby(
                onBack = { currentScreen = Screen.MainMenu },
                onGameReady = { connector, isHost, aiCount ->
                    mpConnector = connector
                    mpIsHost = isHost
                    currentScreen = Screen.MultiplayerGame(
                        isHost = isHost,
                        roomCode = connector.roomCode,
                        aiCount = aiCount,
                    )
                },
            )
        }
        is Screen.MultiplayerGame -> {
            val connector = mpConnector
            if (connector != null) {
                MultiplayerGame(
                    connector = connector,
                    isHost = screen.isHost,
                    aiCount = screen.aiCount,
                    onBack = {
                        connector.disconnect()
                        mpConnector = null
                        currentScreen = Screen.MainMenu
                    },
                )
            }
        }
    }
}
