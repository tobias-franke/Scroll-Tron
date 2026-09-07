package de.geek_hub.scroll_tron

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmAudioTest {

    private fun analyzeSound(name: String, bytes: ByteArray) {
        var maxAmp = 0
        var nonZeroCount = 0
        var sumSquares = 0.0
        val numSamples = bytes.size / 2
        for (i in 0 until numSamples) {
            val b1 = bytes[i * 2].toInt() and 0xFF
            val b2 = bytes[i * 2 + 1].toInt()
            val sample = (b2 shl 8) or b1
            val absSample = kotlin.math.abs(sample)
            if (absSample > maxAmp) maxAmp = absSample
            if (absSample > 50) nonZeroCount++
            sumSquares += (sample.toDouble() / 32768.0) * (sample.toDouble() / 32768.0)
        }
        val rms = kotlin.math.sqrt(sumSquares / numSamples)
        println("AUDIO [$name]: duration=${numSamples / 22050f * 1000}ms, maxAmp=$maxAmp / 32767 (${(maxAmp * 100) / 32767}%), rms=$rms, activeRatio=${nonZeroCount.toDouble() / numSamples}")
    }

    @Test
    fun testGenerateSoundWaveforms() {
        val steer = generateSteerSound()
        analyzeSound("STEER", steer)
        assertTrue(steer.isNotEmpty())

        val click = generateClickSound()
        analyzeSound("CLICK", click)
        assertTrue(click.isNotEmpty())

        val crash = generateCrashSound()
        analyzeSound("CRASH", crash)
        assertTrue(crash.isNotEmpty())

        val start = generateStartSound()
        analyzeSound("START", start)
        assertTrue(start.isNotEmpty())

        val gameOver = generateGameOverSound()
        analyzeSound("GAME_OVER", gameOver)
        assertTrue(gameOver.isNotEmpty())

        val victory = generateVictorySound()
        analyzeSound("VICTORY", victory)
        assertTrue(victory.isNotEmpty())
    }

    @Test
    fun testPlatformAudioPlayDoesNotThrow() {
        // Safe to call even in headless / environment without audio card
        for (effect in SoundEffect.entries) {
            PlatformAudio.play(effect)
        }
    }
}
