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

private class ChamberlinFilter(private val sampleRate: Float) {
    var low = 0.0
    var band = 0.0

    fun lp(input: Double, cutoff: Double, q: Double): Double {
        val f = (2.0 * sin(PI * (cutoff.coerceIn(20.0, sampleRate * 0.4) / sampleRate))).coerceIn(0.005, 0.95)
        val damp = (1.0 / q.coerceAtLeast(0.5)).coerceIn(0.05, 1.8)
        val high = input - low - damp * band
        band += f * high
        low += f * band
        return low
    }

    fun bp(input: Double, cutoff: Double, q: Double): Double {
        val f = (2.0 * sin(PI * (cutoff.coerceIn(20.0, sampleRate * 0.4) / sampleRate))).coerceIn(0.005, 0.95)
        val damp = (1.0 / q.coerceAtLeast(0.5)).coerceIn(0.05, 1.8)
        val high = input - low - damp * band
        band += f * high
        low += f * band
        return band
    }
}

// 1. STEER: Lightcycle Grid Plasma Skid / Laser Whip
internal fun generateSteerSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.04f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)
    var phase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val oscFreq = 550.0 + (200.0 - 550.0) * t
        phase += 2.0 * PI * oscFreq / sampleRate
        val rawOsc = sawtoothWave(phase) * 0.85 + Random.nextDouble(-0.15, 0.15)
        val cutoff = 2400.0 + (450.0 - 2400.0) * t
        val filtered = filter.bp(rawOsc, cutoff, 3.0)
        val env = exp(-6.0 * t)
        val attack = (i.toDouble() / (sampleRate * 0.002)).coerceAtMost(1.0)
        samples[i] = (filtered * env * attack * 0.35).toFloat()
    }
    return encodePcm16(samples)
}

// 2. CRASH: Tron De-Rez Disintegration (Sub Boom + Glassy Crystalline Shatter + Laser Zap)
internal fun generateCrashSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.45f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val shatterFilter = ChamberlinFilter(sampleRate)

    var subPhase = 0.0
    var laserPhase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Sub impact boom
        val subFreq = 90.0 + (25.0 - 90.0) * t
        subPhase += 2.0 * PI * subFreq / sampleRate
        val subBoom = sin(subPhase) * exp(-4.5 * t) * 0.4

        // De-rez crystalline noise shatter
        val rawNoise = Random.nextDouble(-1.0, 1.0)
        val shatterCutoff = 4200.0 + (280.0 - 4200.0) * t
        val shatter = shatterFilter.bp(rawNoise, shatterCutoff, 4.0) * exp(-3.5 * t) * 0.45

        // Laser disintegration zap
        val laserFreq = 1400.0 * exp(-8.0 * t) + 50.0
        laserPhase += 2.0 * PI * laserFreq / sampleRate
        val laserZap = sawtoothWave(laserPhase) * exp(-6.0 * t) * 0.3

        val mixed = subBoom + shatter + laserZap
        samples[i] = mixed.coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 3. GAME_START: Lightcycle Grid Ignition / Engine Spool (Chorused Reese Engine + Rising Resonant Filter)
internal fun generateStartSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.45f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)

    var oscPhase1 = 0.0
    var oscPhase2 = 0.0
    var pingPhase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Detuned engine roar sweeping up
        val freq1 = 70.0 + (180.0 - 70.0) * t
        val freq2 = 73.0 + (183.0 - 73.0) * t
        oscPhase1 += 2.0 * PI * freq1 / sampleRate
        oscPhase2 += 2.0 * PI * freq2 / sampleRate
        val rawEngine = 0.5 * sawtoothWave(oscPhase1) + 0.5 * sawtoothWave(oscPhase2)

        // Resonant filter sweep opening wide
        val cutoff = 120.0 + (2800.0 - 120.0) * t
        val filteredEngine = filter.lp(rawEngine, cutoff, 3.5)

        // Envelope: smooth surge, then sustain and gentle decay
        val env = (sin(PI * t.coerceIn(0.0, 1.0) * 0.85)).coerceIn(0.0, 1.0)

        // High grid-lock laser ping at peak engagement (around t = 0.22)
        var ping = 0.0
        if (t >= 0.22) {
            val tPing = t - 0.22
            pingPhase += 2.0 * PI * 1200.0 / sampleRate
            ping = sin(pingPhase) * exp(-12.0 * tPing) * 0.15
        }

        val mixed = filteredEngine * env * 0.4 + ping
        samples[i] = mixed.coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 4. GAME_OVER: Grid Blackout / Void Powerdown (Dark Detuned Saws Choked by Resonant Lowpass)
internal fun generateGameOverSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.65f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)

    var phase1 = 0.0
    var phase2 = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        phase1 += 2.0 * PI * 110.0 / sampleRate
        phase2 += 2.0 * PI * 108.0 / sampleRate
        val rawDrone = 0.5 * sawtoothWave(phase1) + 0.5 * sawtoothWave(phase2)

        // Resonant filter dropping deep into sub-void
        val cutoff = 2200.0 * exp(-5.0 * t) + 35.0
        val filtered = filter.lp(rawDrone, cutoff, 5.0)
        val env = exp(-3.0 * t)
        val attack = (i.toDouble() / (sampleRate * 0.005)).coerceAtMost(1.0)

        samples[i] = (filtered * env * attack * 0.45).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 5. VICTORY: Tron Legacy Cyber Synthwave Polyphonic Chord Swell (D minor triad + 7th pad)
internal fun generateVictorySound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.75f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)

    // D minor poly-chord: D3, A3, D4, F4
    val freqs = doubleArrayOf(146.83, 220.00, 293.66, 349.23)
    val phases = DoubleArray(freqs.size)

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Sum 4 chord voices simultaneously
        var chord = 0.0
        for (v in freqs.indices) {
            phases[v] += 2.0 * PI * freqs[v] / sampleRate
            chord += 0.6 * sawtoothWave(phases[v]) + 0.4 * triangleWave(phases[v])
        }
        chord /= freqs.size

        // Resonant filter swell: opens up then gently settles
        val filterEnv = if (t < 0.2) t / 0.2 else exp(-2.0 * (t - 0.2))
        val cutoff = 500.0 + 2700.0 * filterEnv
        val filtered = filter.lp(chord, cutoff, 2.5)

        // Master amplitude envelope: warm swell and shimmering release
        val ampEnv = if (t < 0.1) t / 0.1 else exp(-1.8 * (t - 0.1))
        samples[i] = (filtered * ampEnv * 0.4).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 6. UI_CLICK: Holographic Touchscreen Tap (Clean High-Tech Micro-Transient)
internal fun generateClickSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.02f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    var phase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        val freq = 1800.0 + (900.0 - 1800.0) * t
        phase += 2.0 * PI * freq / sampleRate
        val wave = sin(phase)
        val envelope = exp(-8.0 * t)
        val attack = (i.toDouble() / (sampleRate * 0.001)).coerceAtMost(1.0)
        samples[i] = (wave * envelope * attack * 0.3).toFloat()
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
