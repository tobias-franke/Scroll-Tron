package de.geek_hub.scroll_tron

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

internal fun encodePcm16(samples: FloatArray): ByteArray {
    val bytes = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
        bytes[i * 2] = (s and 0xFF).toByte()
        bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return bytes
}

private fun triangleWave(phase: Double): Double {
    val p = (phase / (2.0 * PI)) % 1.0
    val normP = if (p < 0.0) p + 1.0 else p
    return if (normP < 0.5) 4.0 * normP - 1.0 else 3.0 - 4.0 * normP
}

private fun sawtoothWave(phase: Double): Double {
    val p = (phase / (2.0 * PI)) % 1.0
    val normP = if (p < 0.0) p + 1.0 else p
    return 2.0 * normP - 1.0
}

private fun squareWave(phase: Double): Double {
    return if (sin(phase) >= 0.0) 1.0 else -1.0
}

internal fun generateSteerSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.035f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    var phase = 0.0
    val fStart = 580.0
    val fEnd = 320.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val freq = fStart + (fEnd - fStart) * t
        phase += 2.0 * PI * freq / sampleRate
        val wave = triangleWave(phase)
        val envelope = (1.0 - t) * (1.0 - t)
        val attack = (i.toDouble() / (sampleRate * 0.003)).coerceAtMost(1.0)
        samples[i] = (wave * envelope * attack * 0.25).toFloat()
    }
    return encodePcm16(samples)
}

internal fun generateClickSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.04f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    var phase = 0.0
    val fStart = 880.0
    val fEnd = 440.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val freq = fStart + (fEnd - fStart) * t
        phase += 2.0 * PI * freq / sampleRate
        val wave = sin(phase)
        val envelope = exp(-4.0 * t)
        val attack = (i.toDouble() / (sampleRate * 0.002)).coerceAtMost(1.0)
        samples[i] = (wave * envelope * attack * 0.35).toFloat()
    }
    return encodePcm16(samples)
}

internal fun generateCrashSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.4f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)

    var punchPhase = 0.0
    val punchStartFreq = 160.0
    val punchEndFreq = 30.0

    var filterState = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // 1. Low frequency punch dive
        val punchFreq = punchStartFreq + (punchEndFreq - punchStartFreq) * t
        punchPhase += 2.0 * PI * punchFreq / sampleRate
        val punchWave = sawtoothWave(punchPhase)
        val punchEnv = exp(-5.0 * t)

        // 2. Filtered noise burst
        val rawNoise = Random.nextDouble(-1.0, 1.0)
        val cutoff = 2500.0 * (1.0 - t) + 80.0
        val alpha = (2.0 * PI * cutoff / sampleRate).coerceIn(0.01, 0.9)
        filterState += alpha * (rawNoise - filterState)
        val noiseEnv = exp(-4.0 * t)

        val mixed = punchWave * punchEnv * 0.4 + filterState * noiseEnv * 0.45
        samples[i] = mixed.coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

internal fun generateStartSound(sampleRate: Float = 22050f): ByteArray {
    val notes = doubleArrayOf(440.0, 554.37, 659.25, 880.0)
    val noteDurSec = 0.08f
    val totalSec = noteDurSec * notes.size + 0.05f
    val numSamples = (sampleRate * totalSec).toInt()
    val samples = FloatArray(numSamples)

    for (n in notes.indices) {
        val startSample = (n * noteDurSec * sampleRate).toInt()
        val noteSamples = (noteDurSec * sampleRate).toInt()
        val freq = notes[n]
        var phase = 0.0

        for (i in 0 until (noteSamples + (sampleRate * 0.04f).toInt())) {
            val idx = startSample + i
            if (idx >= numSamples) break
            phase += 2.0 * PI * freq / sampleRate
            val tNote = i.toDouble() / noteSamples
            val wave = 0.7 * squareWave(phase) + 0.3 * triangleWave(phase)
            val envelope = exp(-3.5 * tNote)
            val attack = (i.toDouble() / (sampleRate * 0.003)).coerceAtMost(1.0)
            samples[idx] = (samples[idx] + (wave * envelope * attack * 0.25).toFloat()).coerceIn(-1f, 1f)
        }
    }
    return encodePcm16(samples)
}

internal fun generateGameOverSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.5f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    var phase = 0.0
    val fStart = 320.0
    val fEnd = 65.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val freq = fStart + (fEnd - fStart) * t
        phase += 2.0 * PI * freq / sampleRate
        val wave = 0.6 * sawtoothWave(phase) + 0.4 * squareWave(phase)
        val envelope = exp(-2.5 * t)
        val attack = (i.toDouble() / (sampleRate * 0.004)).coerceAtMost(1.0)
        samples[i] = (wave * envelope * attack * 0.35).toFloat()
    }
    return encodePcm16(samples)
}

internal fun generateVictorySound(sampleRate: Float = 22050f): ByteArray {
    val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
    val noteDurSec = 0.11f
    val totalSec = noteDurSec * (notes.size - 1) + 0.35f
    val numSamples = (sampleRate * totalSec).toInt()
    val samples = FloatArray(numSamples)

    for (n in notes.indices) {
        val startSample = (n * noteDurSec * sampleRate).toInt()
        val dur = if (n == notes.size - 1) 0.3f else noteDurSec
        val noteSamples = (dur * sampleRate).toInt()
        val freq = notes[n]
        var phase = 0.0

        for (i in 0 until noteSamples) {
            val idx = startSample + i
            if (idx >= numSamples) break
            phase += 2.0 * PI * freq / sampleRate
            val tNote = i.toDouble() / noteSamples
            val wave = 0.7 * triangleWave(phase) + 0.3 * sin(phase)
            val envelope = exp(-2.5 * tNote)
            val attack = (i.toDouble() / (sampleRate * 0.003)).coerceAtMost(1.0)
            samples[idx] = (samples[idx] + (wave * envelope * attack * 0.3).toFloat()).coerceIn(-1f, 1f)
        }
    }
    return encodePcm16(samples)
}

actual object PlatformAudio {
    private const val SAMPLE_RATE = 22050f
    private val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)

    private val steerClips = mutableListOf<Clip>()
    private var steerIdx = 0

    private val clickClips = mutableListOf<Clip>()
    private var clickIdx = 0

    private var startClip: Clip? = null
    private var crashClip: Clip? = null
    private var gameOverClip: Clip? = null
    private var victoryClip: Clip? = null

    private var initialized = false
    private var audioAvailable = true

    private fun initAudio() {
        if (initialized || !audioAvailable) return
        initialized = true
        try {
            val steerBytes = generateSteerSound(SAMPLE_RATE)
            repeat(3) {
                createClip(steerBytes)?.let { steerClips.add(it) }
            }

            val clickBytes = generateClickSound(SAMPLE_RATE)
            repeat(2) {
                createClip(clickBytes)?.let { clickClips.add(it) }
            }

            startClip = createClip(generateStartSound(SAMPLE_RATE))
            crashClip = createClip(generateCrashSound(SAMPLE_RATE))
            gameOverClip = createClip(generateGameOverSound(SAMPLE_RATE))
            victoryClip = createClip(generateVictorySound(SAMPLE_RATE))
        } catch (_: Throwable) {
            audioAvailable = false
        }
    }

    private fun createClip(pcmData: ByteArray): Clip? {
        return try {
            val clip = AudioSystem.getClip()
            val ais = AudioInputStream(ByteArrayInputStream(pcmData), format, pcmData.size / 2L)
            clip.open(ais)
            clip
        } catch (_: Throwable) {
            null
        }
    }

    private fun playClip(clip: Clip?) {
        if (clip == null) return
        try {
            if (clip.isRunning) {
                clip.stop()
            }
            clip.framePosition = 0
            clip.start()
        } catch (_: Throwable) {}
    }

    actual fun play(sound: SoundEffect) {
        try {
            if (!initialized) initAudio()
            if (!audioAvailable) return

            when (sound) {
                SoundEffect.STEER -> {
                    if (steerClips.isNotEmpty()) {
                        playClip(steerClips[steerIdx % steerClips.size])
                        steerIdx++
                    }
                }
                SoundEffect.UI_CLICK -> {
                    if (clickClips.isNotEmpty()) {
                        playClip(clickClips[clickIdx % clickClips.size])
                        clickIdx++
                    }
                }
                SoundEffect.GAME_START -> playClip(startClip)
                SoundEffect.CRASH -> playClip(crashClip)
                SoundEffect.GAME_OVER -> playClip(gameOverClip)
                SoundEffect.VICTORY -> playClip(victoryClip)
            }
        } catch (_: Throwable) {}
    }
}
