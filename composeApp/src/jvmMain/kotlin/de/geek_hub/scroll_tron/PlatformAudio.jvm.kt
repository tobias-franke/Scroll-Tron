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

// 1. STEER: Punchy low-mid Lightcycle Thruster Impulse (deep, weighty tactile turn click)
internal fun generateSteerSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.045f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)
    var punchPhase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Low-mid punchy thruster pitch drop (140Hz -> 50Hz)
        val punchFreq = 140.0 * exp(-12.0 * t) + 50.0
        punchPhase += 2.0 * PI * punchFreq / sampleRate
        val punch = sin(punchPhase) * exp(-8.0 * t) * 0.7

        // Filtered plasma jet puff
        val rawNoise = Random.nextDouble(-1.0, 1.0)
        val cutoff = 550.0 * exp(-10.0 * t) + 80.0
        val jetPuff = filter.lp(rawNoise, cutoff, 2.0) * exp(-10.0 * t) * 0.4

        val attack = (i.toDouble() / (sampleRate * 0.001)).coerceAtMost(1.0)
        samples[i] = ((punch + jetPuff) * attack * 0.85).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 2. CRASH: Deep, Low-Frequency Tron De-Rez Explosion (heavy sub-bass rumble & dark low crunch)
internal fun generateCrashSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.5f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val rumbleFilter = ChamberlinFilter(sampleRate)

    var subPhase = 0.0
    var lowPunchPhase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Heavy seismic sub-bass impact (65Hz down to 22Hz)
        val subFreq = 65.0 + (22.0 - 65.0) * t
        subPhase += 2.0 * PI * subFreq / sampleRate
        val subBoom = sin(subPhase) * exp(-3.5 * t) * 0.65

        // Deep low-frequency noise crunch (cutoff sweeps 650Hz down to 50Hz, no high-pitched screech)
        val rawNoise = Random.nextDouble(-1.0, 1.0)
        val cutoff = 650.0 * exp(-4.0 * t) + 50.0
        val lowCrunch = rumbleFilter.lp(rawNoise, cutoff, 1.6) * exp(-3.0 * t) * 0.5

        // Low-frequency pitch dive (180Hz down to 35Hz)
        val diveFreq = 180.0 * exp(-6.0 * t) + 35.0
        lowPunchPhase += 2.0 * PI * diveFreq / sampleRate
        val lowDive = sawtoothWave(lowPunchPhase) * exp(-4.5 * t) * 0.35

        val mixed = subBoom + lowCrunch + lowDive
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
            ping = sin(pingPhase) * exp(-12.0 * tPing) * 0.2
        }

        val mixed = filteredEngine * env * 0.6 + ping
        samples[i] = mixed.coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 4. GAME_OVER: Grid Blackout / Void Powerdown (Dark Detuned Saws Choked by Resonant Lowpass)
internal fun generateGameOverSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.7f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)

    var phase1 = 0.0
    var phase2 = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples
        phase1 += 2.0 * PI * 130.0 / sampleRate
        phase2 += 2.0 * PI * 127.0 / sampleRate
        val rawDrone = 0.5 * sawtoothWave(phase1) + 0.5 * sawtoothWave(phase2)

        // Resonant filter dropping deep into sub-void
        val cutoff = 1800.0 * exp(-4.5 * t) + 35.0
        val filtered = filter.lp(rawDrone, cutoff, 4.0)
        val env = exp(-2.5 * t)
        val attack = (i.toDouble() / (sampleRate * 0.005)).coerceAtMost(1.0)

        samples[i] = (filtered * env * attack * 0.8).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 5. VICTORY: Tron Legacy Cyber Synthwave Polyphonic Chord Swell (D minor triad + 7th pad)
internal fun generateVictorySound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.85f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    val filter = ChamberlinFilter(sampleRate)

    // D minor poly-chord: D3, A3, D4, F4, A4
    val freqs = doubleArrayOf(146.83, 220.00, 293.66, 349.23, 440.00)
    val phases = DoubleArray(freqs.size)

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Sum 5 chord voices simultaneously
        var chord = 0.0
        for (v in freqs.indices) {
            phases[v] += 2.0 * PI * freqs[v] / sampleRate
            chord += 0.55 * sawtoothWave(phases[v]) + 0.45 * triangleWave(phases[v])
        }
        chord /= freqs.size

        // Resonant filter swell: opens up then gently settles
        val filterEnv = if (t < 0.2) t / 0.2 else exp(-1.5 * (t - 0.2))
        val cutoff = 400.0 + 2600.0 * filterEnv
        val filtered = filter.lp(chord, cutoff, 2.5)

        // Master amplitude envelope: warm swell and shimmering release
        val ampEnv = if (t < 0.08) t / 0.08 else exp(-1.5 * (t - 0.08))
        samples[i] = (filtered * ampEnv * 0.85).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

// 6. UI_CLICK: Crisp Holographic Touchscreen Tap (Clearly Audible Micro-Transient Pop)
internal fun generateClickSound(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 0.05f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)
    var phase1 = 0.0
    var phase2 = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / numSamples

        // Solid mid-pop (750Hz -> 350Hz)
        val freq1 = 750.0 + (350.0 - 750.0) * t
        phase1 += 2.0 * PI * freq1 / sampleRate
        val tone = sin(phase1) * exp(-14.0 * t) * 0.7

        // Crisp high-tech transient click
        phase2 += 2.0 * PI * 1400.0 / sampleRate
        val click = sin(phase2) * exp(-28.0 * t) * 0.35

        val attack = (i.toDouble() / (sampleRate * 0.001)).coerceAtMost(1.0)
        samples[i] = ((tone + click) * attack * 0.8).coerceIn(-1.0, 1.0).toFloat()
    }
    return encodePcm16(samples)
}

actual object PlatformAudio {
    private const val SAMPLE_RATE = 22050f
    private val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)

    // Multi-voice round-robin pools to prevent dropped or cut-off sounds
    private val steerClips = mutableListOf<Clip>()
    private var steerIdx = 0

    private val clickClips = mutableListOf<Clip>()
    private var clickIdx = 0

    private val startClips = mutableListOf<Clip>()
    private var startIdx = 0

    private val crashClips = mutableListOf<Clip>()
    private var crashIdx = 0

    private val gameOverClips = mutableListOf<Clip>()
    private var gameOverIdx = 0

    private val victoryClips = mutableListOf<Clip>()
    private var victoryIdx = 0

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
            repeat(3) {
                createClip(clickBytes)?.let { clickClips.add(it) }
            }

            val startBytes = generateStartSound(SAMPLE_RATE)
            repeat(2) {
                createClip(startBytes)?.let { startClips.add(it) }
            }

            val crashBytes = generateCrashSound(SAMPLE_RATE)
            repeat(2) {
                createClip(crashBytes)?.let { crashClips.add(it) }
            }

            val gameOverBytes = generateGameOverSound(SAMPLE_RATE)
            repeat(2) {
                createClip(gameOverBytes)?.let { gameOverClips.add(it) }
            }

            val victoryBytes = generateVictorySound(SAMPLE_RATE)
            repeat(2) {
                createClip(victoryBytes)?.let { victoryClips.add(it) }
            }
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

    private fun playFromPool(clips: List<Clip>, index: Int) {
        if (clips.isEmpty()) return
        try {
            val clip = clips[index % clips.size]
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
                    playFromPool(steerClips, steerIdx++)
                }
                SoundEffect.UI_CLICK -> {
                    playFromPool(clickClips, clickIdx++)
                }
                SoundEffect.GAME_START -> {
                    playFromPool(startClips, startIdx++)
                }
                SoundEffect.CRASH -> {
                    playFromPool(crashClips, crashIdx++)
                }
                SoundEffect.GAME_OVER -> {
                    playFromPool(gameOverClips, gameOverIdx++)
                }
                SoundEffect.VICTORY -> {
                    playFromPool(victoryClips, victoryIdx++)
                }
            }
        } catch (_: Throwable) {}
    }
}
