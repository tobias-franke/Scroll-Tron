package de.geek_hub.scroll_tron

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
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
}

// 1. CRASH: Deep, Low-Frequency Tron De-Rez Explosion (heavy sub-bass rumble & dark low crunch)
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

// 2. GAME_START: Lightcycle Grid Ignition / Engine Spool (Chorused Reese Engine + Rising Resonant Filter)
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

// 3. BACKGROUND_MUSIC: 8-bar Cyberpunk synthwave loop in D minor (120 BPM, 16 seconds)
internal fun generateBackgroundMusic(sampleRate: Float = 22050f): ByteArray {
    val durationSec = 16.0f
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = FloatArray(numSamples)

    // Root freq, 3 pad freqs, 4 arp freqs
    data class BarChord(val root: Double, val pad: DoubleArray, val arp: DoubleArray)
    val chordData = arrayOf(
        BarChord(73.42, doubleArrayOf(146.83, 174.61, 220.00), doubleArrayOf(293.66, 349.23, 440.00, 587.33)), // Bar 0: Dm
        BarChord(73.42, doubleArrayOf(146.83, 174.61, 220.00), doubleArrayOf(293.66, 349.23, 440.00, 587.33)), // Bar 1: Dm
        BarChord(58.27, doubleArrayOf(116.54, 146.83, 174.61), doubleArrayOf(233.08, 293.66, 349.23, 466.16)), // Bar 2: Bb
        BarChord(58.27, doubleArrayOf(116.54, 146.83, 174.61), doubleArrayOf(233.08, 293.66, 349.23, 466.16)), // Bar 3: Bb
        BarChord(65.41, doubleArrayOf(130.81, 164.81, 196.00), doubleArrayOf(261.63, 329.63, 392.00, 523.25)), // Bar 4: C
        BarChord(65.41, doubleArrayOf(130.81, 164.81, 196.00), doubleArrayOf(261.63, 329.63, 392.00, 523.25)), // Bar 5: C
        BarChord(43.65, doubleArrayOf(174.61, 220.00, 261.63), doubleArrayOf(349.23, 440.00, 523.25, 698.46)), // Bar 6: F
        BarChord(55.00, doubleArrayOf(138.59, 164.81, 220.00), doubleArrayOf(277.18, 329.63, 440.00, 554.37)), // Bar 7: A
    )

    val bassFilter = ChamberlinFilter(sampleRate)
    var kickPhase = 0.0
    var bassPhase1 = 0.0
    var bassPhase2 = 0.0
    var arpPhase = 0.0

    for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val bar = ((t / 2.0).toInt()).coerceIn(0, 7)
        val chord = chordData[bar]

        // 1. Cyber kick (quarter notes, 0.5s period)
        val tBeat = t % 0.5
        val kick = if (tBeat < 0.2) {
            val kFreq = 42.0 + 85.0 * exp(-25.0 * tBeat)
            kickPhase += 2.0 * PI * kFreq / sampleRate
            sin(kickPhase) * exp(-9.0 * tBeat) * 0.42
        } else {
            0.0
        }

        // 2. Crisp hi-hat tick (16th notes, off-beats)
        val t16th = t % 0.125
        val sixteenthIdx = ((t % 0.5) / 0.125).toInt()
        val hatGain = if (sixteenthIdx == 1 || sixteenthIdx == 3) 0.12 else 0.05
        val hat = Random.nextDouble(-1.0, 1.0) * exp(-65.0 * t16th) * hatGain

        // 3. Driving analog bass (16th notes with octave jumps)
        val bassMult = if (sixteenthIdx == 2) 2.0 else 1.0
        val curBassFreq = chord.root * bassMult
        bassPhase1 += 2.0 * PI * curBassFreq / sampleRate
        bassPhase2 += 2.0 * PI * (curBassFreq + 1.2) / sampleRate
        val rawBass = 0.5 * sawtoothWave(bassPhase1) + 0.5 * sawtoothWave(bassPhase2)

        val barPos = t / 16.0
        val lfoCutoff = 400.0 + 1100.0 * (0.5 + 0.5 * sin(2.0 * PI * barPos - PI / 2))
        val bassEnv = exp(-14.0 * t16th)
        val filteredBass = bassFilter.lp(rawBass, lfoCutoff, 2.2) * bassEnv * 0.32

        // 4. Cyber Arp
        val arpNote = chord.arp[((t / 0.125).toInt()) % 4]
        arpPhase += 2.0 * PI * arpNote / sampleRate
        val arpEnv = exp(-22.0 * t16th)
        val arp = sin(arpPhase) * arpEnv * 0.16

        // 5. Ambient synth pad
        val pad = (sin(2.0 * PI * chord.pad[0] * t) +
                   sin(2.0 * PI * chord.pad[1] * t) +
                   sin(2.0 * PI * chord.pad[2] * t)) / 3.0 * 0.10

        samples[i] = (kick + hat + filteredBass + arp + pad).toFloat()
    }

    // Micro-crossfade over 10ms for 100% clickless, seamless looping
    val fadeLen = (sampleRate * 0.01f).toInt()
    for (j in 0 until fadeLen) {
        val frac = j.toFloat() / fadeLen
        samples[j] = samples[j] * frac + samples[numSamples - fadeLen + j] * (1.0f - frac)
    }

    var maxVal = 0f
    for (s in samples) {
        val a = abs(s)
        if (a > maxVal) maxVal = a
    }
    val scale = if (maxVal > 0.001f) 1.0f / maxOf(1.0f, maxVal * 1.1f) else 1.0f
    for (i in samples.indices) {
        samples[i] = (samples[i] * scale).coerceIn(-1.0f, 1.0f)
    }

    return encodePcm16(samples)
}

actual object PlatformAudio {
    private const val SAMPLE_RATE = 22050f
    private val format = AudioFormat(SAMPLE_RATE, 16, 1, true, false)

    // Multi-voice round-robin pools to prevent dropped or cut-off sounds
    private val startClips = mutableListOf<Clip>()
    private var startIdx = 0

    private val crashClips = mutableListOf<Clip>()
    private var crashIdx = 0

    private var initialized = false
    private var audioAvailable = true

    // Background music streaming via SourceDataLine (real-time sub-25ms volume ducking)
    private var bgmLine: SourceDataLine? = null
    private var bgmThread: Thread? = null
    @Volatile private var isMusicRunning = false
    @Volatile private var isMusicMuted = false
    @Volatile private var duckEndTime = 0L
    private val musicLock = Any()

    private const val NORMAL_VOLUME = 0.45f
    private const val DUCKED_VOLUME = 0.08f

    private fun initAudio() {
        if (initialized || !audioAvailable) return
        initialized = true
        try {
            val startBytes = generateStartSound(SAMPLE_RATE)
            repeat(2) {
                createClip(startBytes)?.let { startClips.add(it) }
            }

            val crashBytes = generateCrashSound(SAMPLE_RATE)
            repeat(2) {
                createClip(crashBytes)?.let { crashClips.add(it) }
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
                SoundEffect.GAME_START -> {
                    playFromPool(startClips, startIdx++)
                }
                SoundEffect.CRASH -> {
                    playFromPool(crashClips, crashIdx++)
                }
            }
        } catch (_: Throwable) {}
    }

    actual fun startMusic() {
        synchronized(musicLock) {
            if (isMusicRunning) return
            isMusicRunning = true
        }

        val thread = Thread({
            try {
                val pcmBytes = generateBackgroundMusic(SAMPLE_RATE)
                val shorts = ShortArray(pcmBytes.size / 2)
                for (i in shorts.indices) {
                    val b1 = pcmBytes[i * 2].toInt() and 0xFF
                    val b2 = pcmBytes[i * 2 + 1].toInt()
                    shorts[i] = ((b2 shl 8) or b1).toShort()
                }

                val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(lineInfo)) return@Thread

                val line = AudioSystem.getLine(lineInfo) as SourceDataLine
                line.open(format, 2048) // 2048 bytes buffer = ~46ms latency
                line.start()
                bgmLine = line

                val chunkBytes = ByteArray(512) // 256 samples per write (~11.6ms chunks)
                var sampleIdx = 0
                var currentVol = if (isMusicMuted) 0f else NORMAL_VOLUME

                while (isMusicRunning) {
                    val now = System.currentTimeMillis()
                    val targetVol = when {
                        isMusicMuted -> 0f
                        now < duckEndTime -> DUCKED_VOLUME
                        else -> NORMAL_VOLUME
                    }

                    for (i in 0 until 256) {
                        currentVol += (targetVol - currentVol) * 0.08f
                        val raw = shorts[sampleIdx]
                        sampleIdx = (sampleIdx + 1) % shorts.size

                        val s = (raw * currentVol).toInt().coerceIn(-32768, 32767)
                        chunkBytes[i * 2] = (s and 0xFF).toByte()
                        chunkBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    line.write(chunkBytes, 0, chunkBytes.size)
                }

                try {
                    line.stop()
                    line.close()
                } catch (_: Throwable) {}
            } catch (_: Throwable) {
                isMusicRunning = false
            }
        }, "Tron-BGM-Stream")

        thread.isDaemon = true
        bgmThread = thread
        thread.start()
    }

    actual fun stopMusic() {
        isMusicRunning = false
        try {
            bgmLine?.stop()
            bgmLine?.close()
        } catch (_: Throwable) {}
        bgmLine = null
    }

    actual fun setMusicMuted(muted: Boolean) {
        isMusicMuted = muted
    }

    actual fun duckMusic(durationMs: Long) {
        if (isMusicMuted) return
        duckEndTime = System.currentTimeMillis() + durationMs
    }
}
