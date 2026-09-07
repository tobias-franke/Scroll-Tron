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
