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
}