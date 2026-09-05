package de.geek_hub.scroll_tron

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// AI Player Steering Engine & Spatial Acceleration
// ---------------------------------------------------------------------------

private const val MAX_LOOKAHEAD = 600f
private const val DANGER_DISTANCE = 260f
private const val WARNING_DISTANCE = 450f
private const val WALL_MARGIN = 280f

/**
 * Segment entry stored in the spatial grid with ownership metadata.
 */
data class SpatialSegment(
    val seg: LineSegment,
    val playerIndex: Int,
    val segmentIndex: Int,
)

/**
 * Uniform spatial partition grid dividing the arena into cells to accelerate
 * segment queries during feeler raycasts and physics collision detection.
 */
class SpatialGrid(
    val arenaWidth: Float = GAME_WIDTH,
    val arenaHeight: Float = GAME_HEIGHT,
    val cellSize: Float = 200f,
) {
    val cols = (arenaWidth / cellSize).toInt() + 1
    val rows = (arenaHeight / cellSize).toInt() + 1
    val cells: Array<ArrayList<SpatialSegment>> = Array(cols * rows) { ArrayList() }

    fun clear() {
        for (i in cells.indices) {
            cells[i].clear()
        }
    }

    fun addSegment(seg: LineSegment, playerIndex: Int, segmentIndex: Int) {
        val s = SpatialSegment(seg, playerIndex, segmentIndex)
        val sMinX = min(seg.start.x, seg.end.x)
        val sMaxX = max(seg.start.x, seg.end.x)
        val sMinY = min(seg.start.y, seg.end.y)
        val sMaxY = max(seg.start.y, seg.end.y)

        val cMinX = (sMinX / cellSize).toInt().coerceIn(0, cols - 1)
        val cMaxX = (sMaxX / cellSize).toInt().coerceIn(0, cols - 1)
        val cMinY = (sMinY / cellSize).toInt().coerceIn(0, rows - 1)
        val cMaxY = (sMaxY / cellSize).toInt().coerceIn(0, rows - 1)

        for (cy in cMinY..cMaxY) {
            val offset = cy * cols
            for (cx in cMinX..cMaxX) {
                cells[offset + cx].add(s)
            }
        }
    }

    fun rebuildFrom(players: List<GameState>) {
        clear()
        for (pIdx in players.indices) {
            val trail = players[pIdx].trail
            for (sIdx in trail.indices) {
                addSegment(trail[sIdx], pIdx, sIdx)
            }
        }
    }
}

/**
 * Tests ray intersection with a single line segment using fast math without square roots.
 * Returns the intersection distance along the ray if closer than [closest], or [closest] otherwise.
 */
private fun testSegmentRay(
    seg: LineSegment,
    origin: Point,
    dirX: Float,
    dirY: Float,
    closest: Float,
): Float {
    val sx = seg.end.x - seg.start.x
    val sy = seg.end.y - seg.start.y
    val lenSq = sx * sx + sy * sy
    if (lenSq < 1e-8f) return closest

    val vx = seg.start.x - origin.x
    val vy = seg.start.y - origin.y
    val denom = dirX * sy - dirY * sx

    // Squared collinear check: (abs(denom)/segLen < 0.05) <=> denom^2 < 0.0025 * lenSq
    if (denom * denom < 0.0025f * lenSq) {
        val perpDist = abs(vx * dirY - vy * dirX)
        if (perpDist < 10.0f) {
            val tA = vx * dirX + vy * dirY
            val tB = (seg.end.x - origin.x) * dirX + (seg.end.y - origin.y) * dirY
            val tMin = min(tA, tB)
            val tMax = max(tA, tB)
            if (tMax > 1.0f) {
                val t = if (tMin > 1.0f) tMin else 1.0f
                if (t < closest) {
                    return t
                }
            }
        }
        return closest
    }

    val t = (vx * sy - vy * sx) / denom
    val u = (vx * dirY - vy * dirX) / denom

    if (t > 1.0f && t < closest && u in 0f..1f) {
        return t
    }
    return closest
}

/**
 * Casts a single ray using the SpatialGrid, testing only segments in cells along the ray path.
 */
fun raycastClearanceGrid(
    origin: Point,
    angle: Float,
    maxDist: Float,
    grid: SpatialGrid,
    botIndex: Int,
    botSafeLimit: Int,
    extras: List<LineSegment> = emptyList(),
    arenaWidth: Float = GAME_WIDTH,
    arenaHeight: Float = GAME_HEIGHT,
): Float {
    val dirX = cos(angle)
    val dirY = sin(angle)
    var closest = maxDist

    // 1. Boundary walls
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

    // 2. Query spatial grid cells in ray bounding box
    var rMinX = min(origin.x, origin.x + dirX * closest) - 1f
    var rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
    var rMinY = min(origin.y, origin.y + dirY * closest) - 1f
    var rMaxY = max(origin.y, origin.y + dirY * closest) + 1f

    val cMinX = (rMinX / grid.cellSize).toInt().coerceIn(0, grid.cols - 1)
    val cMaxX = (rMaxX / grid.cellSize).toInt().coerceIn(0, grid.cols - 1)
    val cMinY = (rMinY / grid.cellSize).toInt().coerceIn(0, grid.rows - 1)
    val cMaxY = (rMaxY / grid.cellSize).toInt().coerceIn(0, grid.rows - 1)

    for (cy in cMinY..cMaxY) {
        val offset = cy * grid.cols
        for (cx in cMinX..cMaxX) {
            val cell = grid.cells[offset + cx]
            for (i in cell.indices) {
                val item = cell[i]
                if (item.playerIndex == botIndex && item.segmentIndex > botSafeLimit) continue
                val seg = item.seg
                val sMinX = min(seg.start.x, seg.end.x)
                val sMaxX = max(seg.start.x, seg.end.x)
                if (sMaxX < rMinX || sMinX > rMaxX) continue

                val sMinY = min(seg.start.y, seg.end.y)
                val sMaxY = max(seg.start.y, seg.end.y)
                if (sMaxY < rMinY || sMinY > rMaxY) continue

                val hit = testSegmentRay(seg, origin, dirX, dirY, closest)
                if (hit < closest) {
                    closest = hit
                    rMinX = min(origin.x, origin.x + dirX * closest) - 1f
                    rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
                    rMinY = min(origin.y, origin.y + dirY * closest) - 1f
                    rMaxY = max(origin.y, origin.y + dirY * closest) + 1f
                }
            }
        }
    }

    // 3. Dynamic extras (active opponent heads and projected trajectories)
    for (i in extras.indices) {
        val seg = extras[i]
        val sMinX = min(seg.start.x, seg.end.x)
        val sMaxX = max(seg.start.x, seg.end.x)
        if (sMaxX < rMinX || sMinX > rMaxX) continue

        val sMinY = min(seg.start.y, seg.end.y)
        val sMaxY = max(seg.start.y, seg.end.y)
        if (sMaxY < rMinY || sMinY > rMaxY) continue

        val hit = testSegmentRay(seg, origin, dirX, dirY, closest)
        if (hit < closest) {
            closest = hit
        }
    }

    return closest
}

/**
 * Direct feeler raycast without SpatialGrid, reading directly from [players] without allocations.
 */
fun raycastClearanceDirect(
    origin: Point,
    angle: Float,
    maxDist: Float,
    players: List<GameState>,
    botIndex: Int,
    botSafeLimit: Int,
    extras: List<LineSegment> = emptyList(),
    arenaWidth: Float = GAME_WIDTH,
    arenaHeight: Float = GAME_HEIGHT,
): Float {
    val dirX = cos(angle)
    val dirY = sin(angle)
    var closest = maxDist

    // 1. Boundary walls
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

    var rMinX = min(origin.x, origin.x + dirX * closest) - 1f
    var rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
    var rMinY = min(origin.y, origin.y + dirY * closest) - 1f
    var rMaxY = max(origin.y, origin.y + dirY * closest) + 1f

    // 2. Direct trail check
    for (pIdx in players.indices) {
        val p = players[pIdx]
        val limit = if (pIdx == botIndex) botSafeLimit else p.trail.size - 1
        val trail = p.trail
        for (sIdx in 0..limit) {
            val seg = trail[sIdx]
            val sMinX = min(seg.start.x, seg.end.x)
            val sMaxX = max(seg.start.x, seg.end.x)
            if (sMaxX < rMinX || sMinX > rMaxX) continue

            val sMinY = min(seg.start.y, seg.end.y)
            val sMaxY = max(seg.start.y, seg.end.y)
            if (sMaxY < rMinY || sMinY > rMaxY) continue

            val hit = testSegmentRay(seg, origin, dirX, dirY, closest)
            if (hit < closest) {
                closest = hit
                rMinX = min(origin.x, origin.x + dirX * closest) - 1f
                rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
                rMinY = min(origin.y, origin.y + dirY * closest) - 1f
                rMaxY = max(origin.y, origin.y + dirY * closest) + 1f
            }
        }
    }

    // 3. Dynamic extras
    for (i in extras.indices) {
        val seg = extras[i]
        val sMinX = min(seg.start.x, seg.end.x)
        val sMaxX = max(seg.start.x, seg.end.x)
        if (sMaxX < rMinX || sMinX > rMaxX) continue

        val sMinY = min(seg.start.y, seg.end.y)
        val sMaxY = max(seg.start.y, seg.end.y)
        if (sMaxY < rMinY || sMinY > rMaxY) continue

        val hit = testSegmentRay(seg, origin, dirX, dirY, closest)
        if (hit < closest) closest = hit
    }

    return closest
}

/**
 * Casts a single ray from [origin] along [angle] against [segments], capped at [maxDist].
 */
fun raycastClearance(
    origin: Point,
    angle: Float,
    maxDist: Float,
    segments: List<LineSegment>,
    arenaWidth: Float = GAME_WIDTH,
    arenaHeight: Float = GAME_HEIGHT,
): Float {
    val dirX = cos(angle)
    val dirY = sin(angle)
    var closest = maxDist

    // 1. Boundary walls
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

    var rMinX = min(origin.x, origin.x + dirX * closest) - 1f
    var rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
    var rMinY = min(origin.y, origin.y + dirY * closest) - 1f
    var rMaxY = max(origin.y, origin.y + dirY * closest) + 1f

    for (i in segments.indices) {
        val seg = segments[i]
        val sMinX = min(seg.start.x, seg.end.x)
        val sMaxX = max(seg.start.x, seg.end.x)
        if (sMaxX < rMinX || sMinX > rMaxX) continue

        val sMinY = min(seg.start.y, seg.end.y)
        val sMaxY = max(seg.start.y, seg.end.y)
        if (sMaxY < rMinY || sMinY > rMaxY) continue

        val hit = testSegmentRay(seg, origin, dirX, dirY, closest)
        if (hit < closest) {
            closest = hit
            rMinX = min(origin.x, origin.x + dirX * closest) - 1f
            rMaxX = max(origin.x, origin.x + dirX * closest) + 1f
            rMinY = min(origin.y, origin.y + dirY * closest) - 1f
            rMaxY = max(origin.y, origin.y + dirY * closest) + 1f
        }
    }

    return closest
}

/**
 * Computes the steering impulse for an AI bot player.
 * Returns -STEERING_SENSITIVITY (turn left), +STEERING_SENSITIVITY (turn right), or 0f (straight).
 */
fun computeAiSteering(
    botIndex: Int,
    state: MultiplayerGameState,
    arenaWidth: Float = GAME_WIDTH,
    arenaHeight: Float = GAME_HEIGHT,
    grid: SpatialGrid? = null,
): Float {
    val bot = state.players.getOrNull(botIndex) ?: return 0f
    if (bot.isDead) return 0f

    val pos = bot.position
    val heading = bot.angle
    val botSafeLimit = bot.trail.size - SKIP_SEGMENTS - 1

    // Gather active opponent heads/trajectories (at most 6 segments total)
    val extras = mutableListOf<LineSegment>()
    for (i in state.players.indices) {
        if (i == botIndex) continue
        val p = state.players[i]
        if (!p.isDead) {
            // Crossbar across opponent head (36px wide)
            val perpX = -sin(p.angle) * 18f
            val perpY = cos(p.angle) * 18f
            extras.add(LineSegment(
                Point(p.position.x - perpX, p.position.y - perpY),
                Point(p.position.x + perpX, p.position.y + perpY)
            ))
            // Forward projected trajectory (150px ahead)
            val fwdX = cos(p.angle) * 150f
            val fwdY = sin(p.angle) * 150f
            extras.add(LineSegment(
                p.position,
                Point(p.position.x + fwdX, p.position.y + fwdY)
            ))
        }
    }

    // Feeler rays
    fun cast(angleOffsetRad: Float): Float {
        val rayAngle = heading + angleOffsetRad
        return if (grid != null) {
            raycastClearanceGrid(
                origin = pos,
                angle = rayAngle,
                maxDist = MAX_LOOKAHEAD,
                grid = grid,
                botIndex = botIndex,
                botSafeLimit = botSafeLimit,
                extras = extras,
                arenaWidth = arenaWidth,
                arenaHeight = arenaHeight,
            )
        } else {
            raycastClearanceDirect(
                origin = pos,
                angle = rayAngle,
                maxDist = MAX_LOOKAHEAD,
                players = state.players,
                botIndex = botIndex,
                botSafeLimit = botSafeLimit,
                extras = extras,
                arenaWidth = arenaWidth,
                arenaHeight = arenaHeight,
            )
        }
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
            -STEERING_SENSITIVITY
        }
    }

    return 0f
}

