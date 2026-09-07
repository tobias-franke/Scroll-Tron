package de.geek_hub.scroll_tron

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Cyberpunk / Tron Palette Constants
// ---------------------------------------------------------------------------

object CyberColors {
    val PANEL_BG    = Color(0xEE030A06)
    val DIM_TEXT    = Color(0xFF88AA99)
    val NEON_LIME   = Color(0xFF39FF14)
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
    val maxRadius: Float = 75f,
    var currentRadius: Float = 6f,
    var alpha: Float = 1f,
    val strokeWidth: Float = 2.5f,
)

class DeRezSystem {
    val particles = mutableListOf<DeRezParticle>()
    val shockwaves = mutableListOf<DeRezShockwave>()

    fun hasActive(): Boolean = particles.isNotEmpty() || shockwaves.isNotEmpty()

    fun triggerExplosion(x: Float, y: Float, color: Color) {
        // Shockwaves: multiple waves for dramatic impact
        shockwaves.add(DeRezShockwave(x = x, y = y, color = color, maxRadius = 100f, currentRadius = 8f, strokeWidth = 3.5f))
        shockwaves.add(DeRezShockwave(x = x, y = y, color = Color.White, maxRadius = 55f, currentRadius = 4f, strokeWidth = 2.5f))
        shockwaves.add(DeRezShockwave(x = x, y = y, color = color.copy(alpha = 0.5f), maxRadius = 140f, currentRadius = 12f, strokeWidth = 4.5f))

        // Particle sparks: 45 energetic debris shards
        for (i in 0 until 45) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 2f + Random.nextFloat() * 9f
            val maxLife = 0.65f + Random.nextFloat() * 0.55f
            val size = 2.5f + Random.nextFloat() * 3.5f
            val pColor = if (Random.nextFloat() < 0.65f) color else Color.White
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
            sw.currentRadius += 3.8f
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
            p.vx *= 0.94f
            p.vy *= 0.94f
            p.life -= 0.018f
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
                    color = sw.color.copy(alpha = sw.alpha * 0.85f),
                    radius = sw.currentRadius,
                    center = Offset(sw.x, sw.y),
                    style = Stroke(width = sw.strokeWidth),
                )
            }
            // Draw particle debris with glowing halo
            for (p in particles) {
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                val curSize = p.size * (0.5f + 0.5f * alpha)
                // Outer glow
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.35f),
                    radius = curSize * 2.2f,
                    center = Offset(p.x, p.y),
                )
                // Intense core
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = curSize,
                    center = Offset(p.x, p.y),
                )
            }
        }
    }
}
