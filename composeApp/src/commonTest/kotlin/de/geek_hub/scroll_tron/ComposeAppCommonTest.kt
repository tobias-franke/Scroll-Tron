package de.geek_hub.scroll_tron

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun testSanitizeRoomCode_standardCode() {
        assertEquals("ABCD", sanitizeRoomCode("ABCD"))
        assertEquals("4XYZ", sanitizeRoomCode("4XYZ"))
    }

    @Test
    fun testSanitizeRoomCode_lowercaseAndWhitespace() {
        assertEquals("ABCD", sanitizeRoomCode("  abcd  "))
        assertEquals("3K9M", sanitizeRoomCode("\t3k9m\n"))
    }

    @Test
    fun testSanitizeRoomCode_withStronPrefix() {
        assertEquals("ABCD", sanitizeRoomCode("STRON-ABCD"))
        assertEquals("ABCD", sanitizeRoomCode("stron-abcd"))
        assertEquals("WXYZ", sanitizeRoomCode("  STRON-WXYZ  "))
    }

    @Test
    fun testSanitizeRoomCode_filtersInvalidChars() {
        // 'I', 'O', '0', '1' are excluded from valid pool
        assertEquals("ABCD", sanitizeRoomCode("A-B-C-D"))
        assertEquals("ABCD", sanitizeRoomCode("A0B1C!D"))
    }

    @Test
    fun testSanitizeRoomCode_truncatesToFour() {
        assertEquals("ABCD", sanitizeRoomCode("ABCDEFGH"))
    }

    @Test
    fun testSanitizeRoomCode_emptyAndInvalid() {
        assertEquals("", sanitizeRoomCode(""))
        assertEquals("", sanitizeRoomCode("   "))
        assertEquals("", sanitizeRoomCode("01IO"))
    }

    @Test
    fun testFormat1Dec() {
        assertEquals("0.0", format1Dec(0.0f))
        assertEquals("3.0", format1Dec(3.0f))
        assertEquals("1.2", format1Dec(1.234f))
        assertEquals("1.3", format1Dec(1.26f))
        assertEquals("-0.5", format1Dec(-0.48f))
        assertEquals("0.0", format1Dec(-0.01f))
        assertEquals("180.0", format1Dec(180.0f))
    }

    @Test
    fun testMpInitialState_oneHostOneBot() {
        val state = mpInitialState(numPlayers = 2, aiCount = 1)
        assertEquals(2, state.players.size)
        assertEquals(false, state.players[0].isBot)
        assertEquals(true, state.players[1].isBot)
    }

    @Test
    fun testMpInitialState_oneHostTwoBots() {
        val state = mpInitialState(numPlayers = 3, aiCount = 2)
        assertEquals(3, state.players.size)
        assertEquals(false, state.players[0].isBot)
        assertEquals(true, state.players[1].isBot)
        assertEquals(true, state.players[2].isBot)
    }

    @Test
    fun testMpInitialState_oneHostThreeBots() {
        val state = mpInitialState(numPlayers = 4, aiCount = 3)
        assertEquals(4, state.players.size)
        assertEquals(false, state.players[0].isBot)
        assertEquals(true, state.players[1].isBot)
        assertEquals(true, state.players[2].isBot)
        assertEquals(true, state.players[3].isBot)
    }

    @Test
    fun testMpInitialState_threePlayersAllHuman() {
        val state = mpInitialState(numPlayers = 3, aiCount = 0)
        assertEquals(3, state.players.size)
        assertEquals(false, state.players[0].isBot)
        assertEquals(false, state.players[1].isBot)
        assertEquals(false, state.players[2].isBot)
    }

    @Test
    fun testMpInitialState_fourPlayersAllHuman() {
        val state = mpInitialState(numPlayers = 4, aiCount = 0)
        assertEquals(4, state.players.size)
        assertEquals(false, state.players[0].isBot)
        assertEquals(false, state.players[1].isBot)
        assertEquals(false, state.players[2].isBot)
        assertEquals(false, state.players[3].isBot)
    }

    @Test
    fun testStepMultiplayer_declaresWinnerWhenOthersDead() {
        val initial = mpInitialState(numPlayers = 3, aiCount = 0)
        val playersWithDead = initial.players.mapIndexed { i, p ->
            if (i == 0) p else p.copy(isDead = true)
        }
        val stepped = stepMultiplayer(initial.copy(players = playersWithDead))
        assertEquals(PlayerId.Player1, stepped.winner)
    }

    @Test
    fun testIntersectionT_basicCrossings() {
        // Perpendicular crossing at t = 0.5
        val p1 = Point(0f, 10f)
        val p2 = Point(20f, 10f)
        val q1 = Point(10f, 0f)
        val q2 = Point(10f, 20f)
        val t = intersectionT(p1, p2, q1, q2)
        assertEquals(0.5f, t!!, 0.001f)

        // Non-intersecting parallel lines
        val nonT = intersectionT(Point(0f, 0f), Point(10f, 0f), Point(0f, 5f), Point(10f, 5f))
        assertEquals(null, nonT)

        // Non-intersecting disjoint lines
        val disjointT = intersectionT(Point(0f, 0f), Point(5f, 0f), Point(10f, 0f), Point(15f, 0f))
        assertEquals(null, disjointT)
    }

    @Test
    fun testStepPlayer_clampsToOpponentTrailWithoutOvershoot() {
        // Player at (100f, 200f) moving right with SPEED (3f) towards an opponent trail at x = 101.5f
        val player = GameState(
            position = Point(100f, 200f),
            angle = 0f,
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = false,
        )
        val opponentTrail = listOf(
            LineSegment(Point(101.5f, 150f), Point(101.5f, 250f))
        )

        val result = stepPlayer(player, allTrails = listOf(player.trail, opponentTrail))

        kotlin.test.assertTrue(result.isDead, "Player must be marked dead after hitting opponent line")
        // Without clamping, newPos would have been 103f (overshooting 101.5f by 1.5f)
        kotlin.test.assertTrue(
            result.position.x <= 101.5f,
            "Player position must NOT overshoot opponent line at 101.5f, but was ${result.position.x}"
        )
        kotlin.test.assertTrue(
            result.trail.last().end.x <= 101.5f,
            "Final trail segment must NOT overshoot opponent line at 101.5f, but was ${result.trail.last().end.x}"
        )
    }

    @Test
    fun testStepPlayer_clampsToWallWithoutOvershoot() {
        // Player at (GAME_WIDTH - 1f, 500f) moving right with SPEED (3f) towards right wall
        val player = GameState(
            position = Point(GAME_WIDTH - 1f, 500f),
            angle = 0f,
            angularVelocity = 0f,
            trail = mutableListOf(),
            isDead = false,
        )

        val result = stepPlayer(player, allTrails = listOf(player.trail))

        kotlin.test.assertTrue(result.isDead, "Player must be marked dead after hitting wall")
        kotlin.test.assertTrue(
            result.position.x <= GAME_WIDTH,
            "Player position must NOT overshoot GAME_WIDTH ($GAME_WIDTH), but was ${result.position.x}"
        )
        kotlin.test.assertTrue(
            result.trail.last().end.x <= GAME_WIDTH,
            "Final trail segment must NOT overshoot GAME_WIDTH ($GAME_WIDTH), but was ${result.trail.last().end.x}"
        )
    }
}