package de.geek_hub.scroll_tron

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Cyberpunk / Tron Palette & Visual Constants
// ---------------------------------------------------------------------------

object CyberColors {
    val BG_DARK_CENTER = Color(0xFF030D08)
    val BG_DARK_EDGE   = Color(0xFF010402)
    val GRID_MINOR     = Color(0xFF072012)
    val GRID_MAJOR     = Color(0xFF0E3820)
    val GRID_DOT       = Color(0xFF14502C)

    val NEON_CYAN      = Color(0xFF00FFFF)
    val NEON_PINK      = Color(0xFFFF00FF)
    val NEON_LIME      = Color(0xFF39FF14)
    val NEON_ORANGE    = Color(0xFFFF6600)
    val NEON_YELLOW    = Color(0xFFFFFF00)

    val PANEL_BG       = Color(0xEE030A06)
    val PANEL_BORDER   = Color(0x5500FFFF)
    val DIM_TEXT       = Color(0xFF88AA99)
    val BRIGHT_TEXT    = Color(0xFFE0FFEE)
}

// ---------------------------------------------------------------------------
// Cyber Grid & Background Rendering
// ---------------------------------------------------------------------------

fun DrawScope.drawCyberGrid(width: Float, height: Float, step: Float = 60f) {
    // 1. Deep space cyber void radial gradient
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(CyberColors.BG_DARK_CENTER, CyberColors.BG_DARK_EDGE),
            center = Offset(width / 2f, height / 2f),
            radius = kotlin.math.max(width, height) * 0.85f,
        ),
        size = Size(width, height),
    )

    // 2. Grid lines (minor every 60px, major every 240px)
    var x = 0f
    var col = 0
    while (x <= width) {
        val isMajor = col % 4 == 0
        val lineColor = if (isMajor) CyberColors.GRID_MAJOR else CyberColors.GRID_MINOR
        val lineWidth = if (isMajor) 1.2f else 0.8f
        drawLine(lineColor, Offset(x, 0f), Offset(x, height), strokeWidth = lineWidth)
        x += step
        col++
    }

    var y = 0f
    var row = 0
    while (y <= height) {
        val isMajor = row % 4 == 0
        val lineColor = if (isMajor) CyberColors.GRID_MAJOR else CyberColors.GRID_MINOR
        val lineWidth = if (isMajor) 1.2f else 0.8f
        drawLine(lineColor, Offset(0f, y), Offset(width, y), strokeWidth = lineWidth)
        y += step
        row++
    }

    // 3. Subtle glowing intersection nodes at major crossings
    val dotStep = step * 2f
    var dotX = 0f
    while (dotX <= width) {
        var dotY = 0f
        while (dotY <= height) {
            drawCircle(
                color = CyberColors.GRID_DOT,
                radius = 1.6f,
                center = Offset(dotX, dotY),
            )
            dotY += dotStep
        }
        dotX += dotStep
    }
}

// ---------------------------------------------------------------------------
// Arena Perimeter & Corner Brackets
// ---------------------------------------------------------------------------

fun DrawScope.drawCyberBorder(width: Float, height: Float, accentColor: Color) {
    val strokeWidth = 2.5f
    val inset = strokeWidth / 2f

    // Ambient wall glow
    drawRect(
        color = accentColor.copy(alpha = 0.16f),
        topLeft = Offset(inset, inset),
        size = Size(width - strokeWidth, height - strokeWidth),
        style = Stroke(width = 8f),
    )

    // Vibrant neon wall
    drawRect(
        color = accentColor.copy(alpha = 0.75f),
        topLeft = Offset(inset, inset),
        size = Size(width - strokeWidth, height - strokeWidth),
        style = Stroke(width = strokeWidth),
    )

    // Futuristic corner brackets
    drawCornerBrackets(width, height, accentColor)
}

fun DrawScope.drawCornerBrackets(
    width: Float,
    height: Float,
    color: Color,
    bracketLength: Float = 28f,
    bracketStroke: Float = 3.5f,
    margin: Float = 4f,
) {
    val bColor = color.copy(alpha = 0.95f)

    // Top-Left
    drawLine(bColor, Offset(margin, margin), Offset(margin + bracketLength, margin), strokeWidth = bracketStroke, cap = StrokeCap.Square)
    drawLine(bColor, Offset(margin, margin), Offset(margin, margin + bracketLength), strokeWidth = bracketStroke, cap = StrokeCap.Square)

    // Top-Right
    drawLine(bColor, Offset(width - margin, margin), Offset(width - margin - bracketLength, margin), strokeWidth = bracketStroke, cap = StrokeCap.Square)
    drawLine(bColor, Offset(width - margin, margin), Offset(width - margin, margin + bracketLength), strokeWidth = bracketStroke, cap = StrokeCap.Square)

    // Bottom-Left
    drawLine(bColor, Offset(margin, height - margin), Offset(margin + bracketLength, height - margin), strokeWidth = bracketStroke, cap = StrokeCap.Square)
    drawLine(bColor, Offset(margin, height - margin), Offset(margin, height - margin - bracketLength), strokeWidth = bracketStroke, cap = StrokeCap.Square)

    // Bottom-Right
    drawLine(bColor, Offset(width - margin, height - margin), Offset(width - margin - bracketLength, height - margin), strokeWidth = bracketStroke, cap = StrokeCap.Square)
    drawLine(bColor, Offset(width - margin, height - margin), Offset(width - margin, height - margin - bracketLength), strokeWidth = bracketStroke, cap = StrokeCap.Square)
}

// ---------------------------------------------------------------------------
// 3-Tier Laser Trail Bloom
// ---------------------------------------------------------------------------

fun DrawScope.drawLaserTrail(path: Path, trailColor: Color) {
    // 1. Ambient outer bloom: broad, soft glow
    drawPath(
        path = path,
        color = trailColor.copy(alpha = 0.16f),
        style = Stroke(width = 13f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // 2. Vibrant neon beam aura
    drawPath(
        path = path,
        color = trailColor.copy(alpha = 0.60f),
        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // 3. High-brightness electric core
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.95f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

// ---------------------------------------------------------------------------
// Cyber Light Cycle Head & Headlight Projection
// ---------------------------------------------------------------------------

fun DrawScope.drawCyberHead(pos: Point, angleDeg: Float, trailColor: Color) {
    // 1. Forward Headlight Projection
    rotate(degrees = angleDeg, pivot = Offset(pos.x, pos.y)) {
        val beamLength = 85f
        val beamWidth = 34f
        val headlightPath = Path().apply {
            moveTo(pos.x + 8f, pos.y)
            lineTo(pos.x + beamLength, pos.y - beamWidth)
            lineTo(pos.x + beamLength, pos.y + beamWidth)
            close()
        }
        drawPath(
            path = headlightPath,
            brush = Brush.horizontalGradient(
                colors = listOf(trailColor.copy(alpha = 0.28f), trailColor.copy(alpha = 0.05f), Color.Transparent),
                startX = pos.x + 8f,
                endX = pos.x + beamLength,
            ),
        )
    }

    // 2. Cycle chassis glow aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(trailColor.copy(alpha = 0.75f), trailColor.copy(alpha = 0.2f), Color.Transparent),
            center = Offset(pos.x, pos.y),
            radius = 20f,
        ),
        radius = 20f,
        center = Offset(pos.x, pos.y),
    )

    // 3. Sleek cyber dart cycle chassis
    rotate(degrees = angleDeg, pivot = Offset(pos.x, pos.y)) {
        // Engine thruster flare
        drawCircle(
            color = trailColor.copy(alpha = 0.85f),
            radius = 4f,
            center = Offset(pos.x - 7f, pos.y),
        )

        // Outer chassis
        val chassisPath = Path().apply {
            moveTo(pos.x + 13f, pos.y)                 // Nose tip
            lineTo(pos.x + 2f, pos.y - 4.5f)          // Front top angle
            lineTo(pos.x - 8f, pos.y - 7.5f)          // Rear top wing
            lineTo(pos.x - 5f, pos.y)                 // Rear thruster exhaust notch
            lineTo(pos.x - 8f, pos.y + 7.5f)          // Rear bottom wing
            lineTo(pos.x + 2f, pos.y + 4.5f)          // Front bottom angle
            close()
        }
        // Dark metallic body fill
        drawPath(chassisPath, color = Color(0xFF040E0A))
        // Neon edge outline
        drawPath(chassisPath, color = trailColor, style = Stroke(width = 1.8f, join = StrokeJoin.Round))

        // Cockpit energy core (diamond)
        val corePath = Path().apply {
            moveTo(pos.x + 4f, pos.y)
            lineTo(pos.x - 1f, pos.y - 2.5f)
            lineTo(pos.x - 3f, pos.y)
            lineTo(pos.x - 1f, pos.y + 2.5f)
            close()
        }
        drawPath(corePath, color = Color.White)
    }
}

// ---------------------------------------------------------------------------
// De-Rez Crash Particle Explosion System
// ---------------------------------------------------------------------------

class DeRezParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val maxLife: Float,
    var life: Float,
    val size: Float,
)

class DeRezShockwave(
    val x: Float,
    val y: Float,
    val color: Color,
    val maxRadius: Float = 70f,
    var currentRadius: Float = 6f,
    var alpha: Float = 1f,
)

class DeRezSystem {
    val particles = mutableListOf<DeRezParticle>()
    val shockwaves = mutableListOf<DeRezShockwave>()

    fun triggerExplosion(x: Float, y: Float, color: Color) {
        // Shockwaves
        shockwaves.add(DeRezShockwave(x = x, y = y, color = color, maxRadius = 75f))
        shockwaves.add(DeRezShockwave(x = x, y = y, color = Color.White, maxRadius = 40f, currentRadius = 3f))

        // Particle sparks
        for (i in 0 until 30) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 2f + Random.nextFloat() * 7f
            val maxLife = 0.5f + Random.nextFloat() * 0.45f
            val size = 2f + Random.nextFloat() * 2.5f
            val pColor = if (Random.nextBoolean()) color else Color.White
            particles.add(
                DeRezParticle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = pColor,
                    maxLife = maxLife,
                    life = maxLife,
                    size = size,
                )
            )
        }
    }

    fun update() {
        // Update shockwaves
        val swIterator = shockwaves.iterator()
        while (swIterator.hasNext()) {
            val sw = swIterator.next()
            sw.currentRadius += 3.2f
            sw.alpha = (1f - (sw.currentRadius / sw.maxRadius)).coerceIn(0f, 1f)
            if (sw.currentRadius >= sw.maxRadius || sw.alpha <= 0.01f) {
                swIterator.remove()
            }
        }

        // Update particles
        val pIterator = particles.iterator()
        while (pIterator.hasNext()) {
            val p = pIterator.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.93f
            p.vy *= 0.93f
            p.life -= 0.022f
            if (p.life <= 0f) {
                pIterator.remove()
            }
        }
    }

    fun clear() {
        particles.clear()
        shockwaves.clear()
    }

    fun draw(drawScope: DrawScope) {
        with(drawScope) {
            // Draw shockwaves
            for (sw in shockwaves) {
                drawCircle(
                    color = sw.color.copy(alpha = sw.alpha * 0.75f),
                    radius = sw.currentRadius,
                    center = Offset(sw.x, sw.y),
                    style = Stroke(width = 2.5f),
                )
            }
            // Draw particle debris
            for (p in particles) {
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size * (0.4f + 0.6f * alpha),
                    center = Offset(p.x, p.y),
                )
            }
        }
    }
}
