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
        SoundManager.playClick()
        SoundManager.playSteer()
        SoundManager.playStart()
        SoundManager.playCrash()
        SoundManager.playGameOver()
        SoundManager.playVictory()

        // When muted
        SoundManager.isMuted = true
        SoundManager.playClick()
        SoundManager.playSteer()
        SoundManager.playStart()
        SoundManager.playCrash()
        SoundManager.playGameOver()
        SoundManager.playVictory()
        SoundManager.isMuted = false
    }

    @Test
    fun testSteerThrottling() {
        SoundManager.isMuted = false
        // Rapid calls to playSteer should not throw or cause failure
        repeat(10) {
            SoundManager.playSteer()
        }
    }
}
