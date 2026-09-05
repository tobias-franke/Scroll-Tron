package de.geek_hub.scroll_tron

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// AI Player Steering Engine
// ---------------------------------------------------------------------------

private const val MAX_LOOKAHEAD = 600f
private const val DANGER_DISTANCE = 260f
private const val WARNING_DISTANCE = 450f
private const val WALL_MARGIN = 280f

/**
 * Computes the steering impulse for an AI bot player.
 * Returns -STEERING_SENSITIVITY (turn left), +STEERING_SENSITIVITY (turn right), or 0f (straight).
 */
fun computeAiSteering(
    botIndex: Int,
    state: MultiplayerGameState,
    arenaWidth: Float = GAME_WIDTH,
    arenaHeight: Float = GAME_HEIGHT,
): Float {
    val bot = state.players.getOrNull(botIndex) ?: return 0f
    if (bot.isDead) return 0f

    val pos = bot.position
    val heading = bot.angle

    // Gather all trail segments to check against, plus active opponent heads/trajectories
    val segmentsToCheck = mutableListOf<LineSegment>()
    for (i in state.players.indices) {
        val p = state.players[i]
        if (i == botIndex) {
            val safeLimit = p.trail.size - SKIP_SEGMENTS - 1
            for (s in 0..safeLimit) {
                segmentsToCheck.add(p.trail[s])
            }
        } else {
            segmentsToCheck.addAll(p.trail)
            if (!p.isDead) {
                // Crossbar across opponent head (30px wide)
                val perpX = -sin(p.angle) * 18f
                val perpY = cos(p.angle) * 18f
                segmentsToCheck.add(LineSegment(
                    Point(p.position.x - perpX, p.position.y - perpY),
                    Point(p.position.x + perpX, p.position.y + perpY)
                ))
                // Forward projected trajectory (150px ahead)
                val fwdX = cos(p.angle) * 150f
                val fwdY = sin(p.angle) * 150f
                segmentsToCheck.add(LineSegment(
                    p.position,
                    Point(p.position.x + fwdX, p.position.y + fwdY)
                ))
            }
        }
    }

    // Feeler rays
    fun cast(angleOffsetRad: Float): Float {
        val rayAngle = heading + angleOffsetRad
        return raycastClearance(
            origin = pos,
            angle = rayAngle,
            maxDist = MAX_LOOKAHEAD,
            segments = segmentsToCheck,
            arenaWidth = arenaWidth,
            arenaHeight = arenaHeight,
        )
    }

    val distCenter = cast(0f)
    val distSlightL = cast(-0.20f)
    val distSlightR = cast(0.20f)
    val distAhead = minOf(distCenter, distSlightL, distSlightR)

    val leftFeeler1 = cast(-0.45f)
    val leftFeeler2 = cast(-1.00f)
    val leftFeeler3 = cast(-1.57f)
    val scoreLeft = leftFeeler1 * 0.50f + leftFeeler2 * 0.35f + leftFeeler3 * 0.15f

    val rightFeeler1 = cast(0.45f)
    val rightFeeler2 = cast(1.00f)
    val rightFeeler3 = cast(1.57f)
    val scoreRight = rightFeeler1 * 0.50f + rightFeeler2 * 0.35f + rightFeeler3 * 0.15f

    // 1. Immediate Danger — obstacle straight ahead
    if (distAhead < DANGER_DISTANCE) {
        return if (abs(scoreLeft - scoreRight) < 20f) {
            // Very close call: maintain existing turn momentum, or default to right (starboard rule)
            if (bot.angularVelocity < -0.005f) -STEERING_SENSITIVITY
            else if (bot.angularVelocity > 0.005f) STEERING_SENSITIVITY
            else STEERING_SENSITIVITY
        } else if (scoreLeft > scoreRight) {
            -STEERING_SENSITIVITY
        } else {
            STEERING_SENSITIVITY
        }
    }

    // 2. Approaching Obstacle — start turning toward greater open space
    if (distAhead < WARNING_DISTANCE) {
        return if (scoreLeft > scoreRight * 1.15f) {
            -STEERING_SENSITIVITY
        } else if (scoreRight > scoreLeft * 1.15f) {
            STEERING_SENSITIVITY
        } else {
            // Equal or near-equal clearance: turn to starboard or maintain turn momentum
            if (bot.angularVelocity < -0.005f) -STEERING_SENSITIVITY
            else STEERING_SENSITIVITY
        }
    }

    // 3. Wall Proximity Check — bias heading toward center when too close to boundaries
    val nearLeft = pos.x < WALL_MARGIN
    val nearRight = pos.x > arenaWidth - WALL_MARGIN
    val nearTop = pos.y < WALL_MARGIN
    val nearBottom = pos.y > arenaHeight - WALL_MARGIN

    if (nearLeft || nearRight || nearTop || nearBottom) {
        val centerX = arenaWidth / 2f
        val centerY = arenaHeight / 2f
        val angleToCenter = atan2(centerY - pos.y, centerX - pos.x)
        var angleDiff = angleToCenter - heading
        while (angleDiff > kotlin.math.PI.toFloat()) angleDiff -= (2 * kotlin.math.PI).toFloat()
        while (angleDiff < -kotlin.math.PI.toFloat()) angleDiff += (2 * kotlin.math.PI).toFloat()

        if (angleDiff > 0.15f && scoreRight > 100f) {
            return STEERING_SENSITIVITY
        } else if (angleDiff < -0.15f && scoreLeft > 100f) {
            return -STEERING_SENSITIVITY
        }
    }

    return 0f
}

/**
 * Casts a single ray from [origin] along [angle] and finds the shortest distance
 * to any arena boundary wall or line segment in [segments], capped at [maxDist].
 */
fun raycastClearance(
    origin: Point,
    angle: Float,
    maxDist: Float,
    segments: List<LineSegment>,
    arenaWidth: Float,
    arenaHeight: Float,
): Float {
    val dirX = cos(angle)
    val dirY = sin(angle)
    var closest = maxDist

    // 1. Check arena boundary walls
    if (dirX < -1e-5f) {
        val t = -origin.x / dirX
        if (t in 0f..closest) {
            val y = origin.y + t * dirY
            if (y in 0f..arenaHeight) closest = t
        }
    } else if (dirX > 1e-5f) {
        val t = (arenaWidth - origin.x) / dirX
        if (t in 0f..closest) {
            val y = origin.y + t * dirY
            if (y in 0f..arenaHeight) closest = t
        }
    }

    if (dirY < -1e-5f) {
        val t = -origin.y / dirY
        if (t in 0f..closest) {
            val x = origin.x + t * dirX
            if (x in 0f..arenaWidth) closest = t
        }
    } else if (dirY > 1e-5f) {
        val t = (arenaHeight - origin.y) / dirY
        if (t in 0f..closest) {
            val x = origin.x + t * dirX
            if (x in 0f..arenaWidth) closest = t
        }
    }

    // 2. Check trail segments with AABB spatial filter
    val endX = origin.x + dirX * closest
    val endY = origin.y + dirY * closest
    val rMinX = min(origin.x, endX) - 1f
    val rMaxX = max(origin.x, endX) + 1f
    val rMinY = min(origin.y, endY) - 1f
    val rMaxY = max(origin.y, endY) + 1f

    for (i in segments.indices) {
        val seg = segments[i]
        val sMinX = min(seg.start.x, seg.end.x)
        val sMaxX = max(seg.start.x, seg.end.x)
        if (sMaxX < rMinX || sMinX > rMaxX) continue

        val sMinY = min(seg.start.y, seg.end.y)
        val sMaxY = max(seg.start.y, seg.end.y)
        if (sMaxY < rMinY || sMinY > rMaxY) continue

        // Ray-segment intersection
        val sx = seg.end.x - seg.start.x
        val sy = seg.end.y - seg.start.y
        val segLen = kotlin.math.hypot(sx, sy)
        if (segLen < 1e-4f) continue

        val vx = seg.start.x - origin.x
        val vy = seg.start.y - origin.y
        val denom = dirX * sy - dirY * sx
        val crossDir = abs(denom) / segLen

        if (crossDir < 0.05f) {
            // Collinear or parallel check: perpendicular distance from ray to segment line
            val perpDist = abs(vx * dirY - vy * dirX)
            if (perpDist < 10.0f) {
                // Segment lies on the same path as the ray: compute distance along the ray
                val tA = vx * dirX + vy * dirY
                val tB = (seg.end.x - origin.x) * dirX + (seg.end.y - origin.y) * dirY
                val tMin = min(tA, tB)
                val tMax = max(tA, tB)
                if (tMax > 1.0f) {
                    val t = if (tMin > 1.0f) tMin else 1.0f
                    if (t < closest) {
                        closest = t
                    }
                }
            }
            continue
        }

        val t = (vx * sy - vy * sx) / denom
        val u = (vx * dirY - vy * dirX) / denom

        if (t > 1.0f && t < closest && u in 0f..1f) {
            closest = t
        }
    }

    return closest
}
