package de.geek_hub.scroll_tron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundManagerTest {

    @Test
    fun testInitialMuteState() {
        SoundManager.isMuted = false
        assertFalse(SoundManager.isMuted)
    }

    @Test
    fun testToggleMute() {
        SoundManager.isMuted = false
        SoundManager.toggleMute()
        assertTrue(SoundManager.isMuted)
        SoundManager.toggleMute()
        assertFalse(SoundManager.isMuted)
    }

    @Test
    fun testPlaySoundsDoNotThrow() {
        SoundManager.isMuted = false
        SoundManager.playStart()
        SoundManager.playCrash()

        // When muted
        SoundManager.isMuted = true
        SoundManager.playStart()
        SoundManager.playCrash()
        SoundManager.isMuted = false
    }

    @Test
    fun testRapidPlaybackDoesNotThrow() {
        SoundManager.isMuted = false
        repeat(10) {
            SoundManager.playStart()
            SoundManager.playCrash()
        }
    }

    @Test
    fun testMusicLifecycleDoesNotThrow() {
        SoundManager.startMusic()
        SoundManager.toggleMute()
        SoundManager.toggleMute()
        SoundManager.stopMusic()
    }
}
