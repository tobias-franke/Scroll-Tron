package de.geek_hub.scroll_tron

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

data class Point(val x: Float, val y: Float)

data class LineSegment(val start: Point, val end: Point)

data class GameState(
    val position: Point,
    val angle: Float,               // current heading in radians
    val angularVelocity: Float,     // weighty steering — decays each frame
    val trail: List<LineSegment>,
    val isDead: Boolean,
)

// ---------------------------------------------------------------------------
// Multiplayer models
// ---------------------------------------------------------------------------

enum class PlayerId { Player1, Player2 }

data class MultiplayerGameState(
    val player1: GameState,
    val player2: GameState,
    val winner: PlayerId? = null,    // set when one player survives
)

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const val SPEED = 3f
const val STEERING_SENSITIVITY = 0.03f
const val ANGULAR_DECAY = 0.80f    // fraction of angularVelocity kept per frame
const val SKIP_SEGMENTS = 4        // ignore last N trail segs for self-collision

