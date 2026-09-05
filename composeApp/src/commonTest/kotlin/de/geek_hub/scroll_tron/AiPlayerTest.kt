package de.geek_hub.scroll_tron

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPlayerTest {

    @Test
    fun testRaycastClearance_hitsWall() {
        val origin = Point(150f, 500f)
        // Ray pointing left towards x = 0 wall
        val distLeft = raycastClearance(
            origin = origin,
            angle = PI.toFloat(),
            maxDist = 500f,
            segments = emptyList(),
            arenaWidth = 3200f,
            arenaHeight = 1800f,
        )
        assertEquals(150f, distLeft, 0.01f)

        // Ray pointing up towards y = 0 wall
        val distUp = raycastClearance(
            origin = Point(500f, 200f),
            angle = (-PI / 2).toFloat(),
            maxDist = 500f,
            segments = emptyList(),
            arenaWidth = 3200f,
            arenaHeight = 1800f,
        )
        assertEquals(200f, distUp, 0.01f)
    }

    @Test
    fun testRaycastClearance_hitsSegment() {
        val origin = Point(200f, 400f)
        val segment = LineSegment(Point(350f, 300f), Point(350f, 500f))

        // Ray pointing right (angle 0) directly at the segment at x = 350
        val dist = raycastClearance(
            origin = origin,
            angle = 0f,
            maxDist = 500f,
            segments = listOf(segment),
            arenaWidth = 3200f,
            arenaHeight = 1800f,
        )
        assertEquals(150f, dist, 0.01f)
    }

    @Test
    fun testComputeAiSteering_avoidsFrontWall() {
        // Bot at (80f, 500f) traveling straight towards left wall
        val bot = GameState(
            position = Point(80f, 500f),
            angle = PI.toFloat(), // heading directly at x = 0
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = false,
            isBot = true,
        )
        val state = MultiplayerGameState(players = listOf(bot))
        val impulse = computeAiSteering(botIndex = 0, state = state)

        // Bot MUST steer away from the wall
        assertTrue(impulse != 0f, "Bot should steer when approaching a wall")
    }

    @Test
    fun testComputeAiSteering_turnsTowardOpenSpace() {
        // Bot at (500f, 500f) heading right (0f)
        // Obstacle wall ahead at x = 650
        val frontWall = LineSegment(Point(650f, 300f), Point(650f, 700f))
        // Left side is blocked by a trail segment at y = 430
        val leftBlock = LineSegment(Point(400f, 430f), Point(700f, 430f))

        val bot = GameState(
            position = Point(500f, 500f),
            angle = 0f,
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = false,
            isBot = true,
        )
        val opponent = GameState(
            position = Point(1000f, 1000f),
            angle = 0f,
            angularVelocity = 0f,
            trail = mutableListOf(frontWall, leftBlock),
            isDead = false,
            isBot = false,
        )
        val state = MultiplayerGameState(players = listOf(bot, opponent))
        val impulse = computeAiSteering(botIndex = 0, state = state)

        // Right side is completely open, left side is blocked -> bot must steer Right (+STEERING_SENSITIVITY)
        assertEquals(STEERING_SENSITIVITY, impulse)
    }

    @Test
    fun testBotPlayerInitialization() {
        val state = mpInitialState(numPlayers = 4, aiCount = 2)
        assertEquals(4, state.players.size)
        // Players 0 and 1 are humans
        assertFalse(state.players[0].isBot)
        assertFalse(state.players[1].isBot)
        // Players 2 and 3 are bots
        assertTrue(state.players[2].isBot)
        assertTrue(state.players[3].isBot)
        // All alive
        assertTrue(state.players.all { !it.isDead })
    }

    @Test
    fun testDeadBotDoesNotSteer() {
        val bot = GameState(
            position = Point(80f, 500f),
            angle = PI.toFloat(),
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = true,
            isBot = true,
        )
        val state = MultiplayerGameState(players = listOf(bot))
        val impulse = computeAiSteering(botIndex = 0, state = state)
        assertEquals(0f, impulse)
    }

    @Test
    fun testRaycastClearance_collinearSegment() {
        // Ray pointing straight down along x = 1600
        val origin = Point(1600f, 500f)
        val angle = (PI / 2).toFloat() // heading down (+y)
        // Collinear vertical segment on x = 1600 from y = 700 to y = 900
        val segment = LineSegment(Point(1600f, 700f), Point(1600f, 900f))

        val dist = raycastClearance(
            origin = origin,
            angle = angle,
            maxDist = 600f,
            segments = listOf(segment),
            arenaWidth = 3200f,
            arenaHeight = 1800f,
        )
        // Segment starts at 700, origin is at 500 -> distance is 200
        assertEquals(200f, dist, 0.01f)
    }

    @Test
    fun testComputeAiSteering_twoBotsHeadOnAvoidance() {
        // Player 2 (Lime) at (1600, 600) heading DOWN (+y)
        val botLime = GameState(
            position = Point(1600f, 600f),
            angle = (PI / 2).toFloat(),
            angularVelocity = 0f,
            trail = mutableListOf(LineSegment(Point(1600f, 450f), Point(1600f, 600f))),
            isDead = false,
            isBot = true,
        )
        // Player 3 (Yellow) at (1600, 1000) heading UP (-y)
        val botYellow = GameState(
            position = Point(1600f, 1000f),
            angle = (-PI / 2).toFloat(),
            angularVelocity = 0f,
            trail = mutableListOf(LineSegment(Point(1600f, 1350f), Point(1600f, 1000f))),
            isDead = false,
            isBot = true,
        )
        val state = MultiplayerGameState(players = listOf(botLime, botYellow))

        val impulseLime = computeAiSteering(botIndex = 0, state = state)
        val impulseYellow = computeAiSteering(botIndex = 1, state = state)

        // Both bots must steer to avoid each other!
        assertTrue(impulseLime != 0f, "Lime bot must steer to avoid oncoming Yellow bot")
        assertTrue(impulseYellow != 0f, "Yellow bot must steer to avoid oncoming Lime bot")
        // Both bots turn to starboard (right):
        // Lime turns right -> towards -x (West)
        // Yellow turns right -> towards +x (East)
        assertEquals(STEERING_SENSITIVITY, impulseLime)
        assertEquals(STEERING_SENSITIVITY, impulseYellow)
    }

    @Test
    fun testTwoBotsSimulation_doNotCollideHeadOn() {
        // Reproduce P3 (Lime) and P4 (Yellow) exact match start positions
        val initialState = mpInitialState(numPlayers = 4, aiCount = 4)
        // Focus on Bot 2 (Lime at (1600, 450) heading down) and Bot 3 (Yellow at (1600, 1350) heading up)
        var state = MultiplayerGameState(
            players = listOf(
                initialState.players[2], // Lime
                initialState.players[3], // Yellow
            )
        )

        // Run 150 frames (~2.5 seconds of game time)
        for (frame in 0 until 150) {
            val updatedPlayers = state.players.toMutableList()
            for (i in updatedPlayers.indices) {
                val p = updatedPlayers[i]
                if (p.isBot && !p.isDead) {
                    val impulse = computeAiSteering(i, state)
                    if (impulse != 0f) {
                        updatedPlayers[i] = p.copy(angularVelocity = p.angularVelocity + impulse)
                    }
                }
            }
            state = stepMultiplayer(state.copy(players = updatedPlayers))
        }

        // Neither bot should have died from a head-on collision!
        assertFalse(state.players[0].isDead, "Lime bot should survive head-on approach")
        assertFalse(state.players[1].isDead, "Yellow bot should survive head-on approach")
    }

    @Test
    fun testSpatialGrid_accuracyAndRaycast() {
        val grid = SpatialGrid(3200f, 1800f, 200f)
        val seg1 = LineSegment(Point(1600f, 700f), Point(1600f, 900f))
        grid.addSegment(seg1, playerIndex = 1, segmentIndex = 0)

        // Ray from (1600, 500) heading down (+y) towards segment at y=700
        val dist = raycastClearanceGrid(
            origin = Point(1600f, 500f),
            angle = (PI / 2).toFloat(),
            maxDist = 600f,
            grid = grid,
            botIndex = 0,
            botSafeLimit = -1,
        )
        assertEquals(200f, dist, 0.01f)
    }

    @Test
    fun testLongRunningSimulation_performanceWithSpatialGrid() {
        // 4 players (1 human, 3 bots)
        var state = mpInitialState(numPlayers = 4, aiCount = 3)
        val grid = SpatialGrid()

        // Run 600 frames (~10 seconds of simulated 60 FPS gameplay, generating thousands of segments)
        for (frame in 0 until 600) {
            val updatedPlayers = state.players.toMutableList()
            val aliveBots = updatedPlayers.indices.filter { updatedPlayers[it].isBot && !updatedPlayers[it].isDead }
            for (botIdx in aliveBots) {
                // Stagger bots like in game loop
                val shouldEvaluate = aliveBots.size <= 1 || ((frame + botIdx) % 2 == 0)
                if (shouldEvaluate) {
                    val impulse = computeAiSteering(botIdx, state, grid = grid)
                    if (impulse != 0f) {
                        updatedPlayers[botIdx] = updatedPlayers[botIdx].copy(
                            angularVelocity = updatedPlayers[botIdx].angularVelocity + impulse
                        )
                    }
                }
            }
            state = stepMultiplayer(state.copy(players = updatedPlayers), grid)
            if (state.winner != null) break
        }

        // Verify simulation completed cleanly without exception
        assertTrue(state.players.isNotEmpty())
    }
}

