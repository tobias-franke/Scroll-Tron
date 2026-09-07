package de.geek_hub.scroll_tron

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmAudioTest {

    @Test
    fun testGenerateSoundWaveforms() {
        val steer = generateSteerSound()
        assertTrue(steer.isNotEmpty())
        assertTrue(steer.any { it != 0.toByte() })

        val click = generateClickSound()
        assertTrue(click.isNotEmpty())
        assertTrue(click.any { it != 0.toByte() })

        val crash = generateCrashSound()
        assertTrue(crash.isNotEmpty())
        assertTrue(crash.any { it != 0.toByte() })

        val start = generateStartSound()
        assertTrue(start.isNotEmpty())
        assertTrue(start.any { it != 0.toByte() })

        val gameOver = generateGameOverSound()
        assertTrue(gameOver.isNotEmpty())
        assertTrue(gameOver.any { it != 0.toByte() })

        val victory = generateVictorySound()
        assertTrue(victory.isNotEmpty())
        assertTrue(victory.any { it != 0.toByte() })
    }

    @Test
    fun testPlatformAudioPlayDoesNotThrow() {
        // Safe to call even in headless / environment without audio card
        for (effect in SoundEffect.entries) {
            PlatformAudio.play(effect)
        }
    }
}
