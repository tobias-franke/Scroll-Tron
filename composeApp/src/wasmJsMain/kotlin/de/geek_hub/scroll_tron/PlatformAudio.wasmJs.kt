@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package de.geek_hub.scroll_tron

actual object PlatformAudio {
    actual fun play(sound: SoundEffect) {
        jsPlayAudio(sound.ordinal)
    }

    actual fun startMusic() {
        jsStartMusic()
    }

    actual fun stopMusic() {
        jsStopMusic()
    }

    actual fun setMusicMuted(muted: Boolean) {
        jsSetMusicMuted(muted)
    }

    actual fun duckMusic(durationMs: Long) {
        jsDuckMusic(durationMs.toDouble())
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function(soundId) {
    if (typeof window === 'undefined') return;
    try {
        if (!window._stAudioCtx) {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) {
                window._stAudioCtx = new AudioContextClass();
            }
        }
        var ctx = window._stAudioCtx;
        if (!ctx) return;
        if (ctx.state === 'suspended') {
            ctx.resume().catch(function() {});
        }
        var t = ctx.currentTime;

        if (soundId === 0) { // GAME_START: Lightcycle Grid Ignition (Chorused Reese Engine + Rising Resonant Filter)
            var dur = 0.45;
            var filter = ctx.createBiquadFilter();
            filter.type = 'lowpass';
            filter.frequency.setValueAtTime(120, t);
            filter.frequency.exponentialRampToValueAtTime(2800, t + 0.35);
            filter.Q.setValueAtTime(3.5, t);

            var masterGain = ctx.createGain();
            masterGain.gain.setValueAtTime(0.01, t);
            masterGain.gain.linearRampToValueAtTime(0.3, t + 0.15);
            masterGain.gain.exponentialRampToValueAtTime(0.001, t + dur);

            filter.connect(masterGain);
            masterGain.connect(ctx.destination);

            // Detuned dual saws for analog chorused roar
            var osc1 = ctx.createOscillator();
            osc1.type = 'sawtooth';
            osc1.frequency.setValueAtTime(70, t);
            osc1.frequency.exponentialRampToValueAtTime(180, t + dur);
            osc1.connect(filter);
            osc1.start(t);
            osc1.stop(t + dur);

            var osc2 = ctx.createOscillator();
            osc2.type = 'sawtooth';
            osc2.frequency.setValueAtTime(73, t);
            osc2.frequency.exponentialRampToValueAtTime(183, t + dur);
            osc2.connect(filter);
            osc2.start(t);
            osc2.stop(t + dur);

            // Grid lock high ping
            var pingOsc = ctx.createOscillator();
            var pingGain = ctx.createGain();
            pingOsc.type = 'sine';
            pingOsc.frequency.setValueAtTime(1200, t + 0.22);
            pingGain.gain.setValueAtTime(0.001, t);
            pingGain.gain.setValueAtTime(0.12, t + 0.22);
            pingGain.gain.exponentialRampToValueAtTime(0.001, t + 0.38);
            pingOsc.connect(pingGain);
            pingGain.connect(ctx.destination);
            pingOsc.start(t + 0.22);
            pingOsc.stop(t + 0.38);
        } else if (soundId === 1) { // CRASH: Tron De-Rez Disintegration (Sub Boom + Glassy Shatter + Laser Zap)
            var dur = 0.45;

            // 1. Sub boom
            var subOsc = ctx.createOscillator();
            var subGain = ctx.createGain();
            subOsc.type = 'sine';
            subOsc.frequency.setValueAtTime(90, t);
            subOsc.frequency.exponentialRampToValueAtTime(25, t + dur);
            subGain.gain.setValueAtTime(0.4, t);
            subGain.gain.exponentialRampToValueAtTime(0.001, t + dur);
            subOsc.connect(subGain);
            subGain.connect(ctx.destination);
            subOsc.start(t);
            subOsc.stop(t + dur);

            // 2. Crystalline de-rez noise shatter
            var bufSize = Math.floor(ctx.sampleRate * dur);
            var noiseBuf = ctx.createBuffer(1, bufSize, ctx.sampleRate);
            var out = noiseBuf.getChannelData(0);
            for (var i = 0; i < bufSize; i++) {
                out[i] = Math.random() * 2 - 1;
            }
            var noise = ctx.createBufferSource();
            noise.buffer = noiseBuf;

            var shatterFilter = ctx.createBiquadFilter();
            shatterFilter.type = 'bandpass';
            shatterFilter.frequency.setValueAtTime(4200, t);
            shatterFilter.frequency.exponentialRampToValueAtTime(280, t + dur);
            shatterFilter.Q.setValueAtTime(4.0, t);

            var shatterGain = ctx.createGain();
            shatterGain.gain.setValueAtTime(0.35, t);
            shatterGain.gain.exponentialRampToValueAtTime(0.001, t + dur);

            noise.connect(shatterFilter);
            shatterFilter.connect(shatterGain);
            shatterGain.connect(ctx.destination);
            noise.start(t);
            noise.stop(t + dur);

            // 3. Laser de-rez zap
            var laserOsc = ctx.createOscillator();
            var laserGain = ctx.createGain();
            laserOsc.type = 'sawtooth';
            laserOsc.frequency.setValueAtTime(1400, t);
            laserOsc.frequency.exponentialRampToValueAtTime(50, t + 0.35);
            laserGain.gain.setValueAtTime(0.25, t);
            laserGain.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
            laserOsc.connect(laserGain);
            laserGain.connect(ctx.destination);
            laserOsc.start(t);
            laserOsc.stop(t + 0.35);
        }
    } catch(e) {}
}
""")
private external fun jsPlayAudio(soundId: Int)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function() {
    if (typeof window === 'undefined') return;
    try {
        if (!window._stAudioCtx) {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) {
                window._stAudioCtx = new AudioContextClass();
            }
        }
        var ctx = window._stAudioCtx;
        if (!ctx) return;
        if (ctx.state === 'suspended') {
            ctx.resume().catch(function() {});
        }

        if (!window._stAutoplayHooked) {
            window._stAutoplayHooked = true;
            var unlock = function() {
                if (window._stAudioCtx && window._stAudioCtx.state === 'suspended') {
                    window._stAudioCtx.resume().catch(function() {});
                }
            };
            window.addEventListener('click', unlock, { passive: true });
            window.addEventListener('keydown', unlock, { passive: true });
            window.addEventListener('touchstart', unlock, { passive: true });
        }

        if (window._stBgmStarted) return;
        window._stBgmStarted = true;

        var sr = 22050;
        var dur = 16.0;
        var numSamples = Math.floor(sr * dur);
        var audioBuf = ctx.createBuffer(1, numSamples, sr);
        var out = audioBuf.getChannelData(0);

        var chordData = [
            { root: 73.42, pad: [146.83, 174.61, 220.00], arp: [293.66, 349.23, 440.00, 587.33] },
            { root: 73.42, pad: [146.83, 174.61, 220.00], arp: [293.66, 349.23, 440.00, 587.33] },
            { root: 58.27, pad: [116.54, 146.83, 174.61], arp: [233.08, 293.66, 349.23, 466.16] },
            { root: 58.27, pad: [116.54, 146.83, 174.61], arp: [233.08, 293.66, 349.23, 466.16] },
            { root: 65.41, pad: [130.81, 164.81, 196.00], arp: [261.63, 329.63, 392.00, 523.25] },
            { root: 65.41, pad: [130.81, 164.81, 196.00], arp: [261.63, 329.63, 392.00, 523.25] },
            { root: 43.65, pad: [174.61, 220.00, 261.63], arp: [349.23, 440.00, 523.25, 698.46] },
            { root: 55.00, pad: [138.59, 164.81, 220.00], arp: [277.18, 329.63, 440.00, 554.37] }
        ];

        var kickPhase = 0;
        var bassPhase1 = 0;
        var bassPhase2 = 0;
        var arpPhase = 0;
        var low = 0;
        var band = 0;

        for (var i = 0; i < numSamples; i++) {
            var t = i / sr;
            var bar = Math.min(7, Math.max(0, Math.floor(t / 2.0)));
            var chord = chordData[bar];

            // 1. Kick
            var tBeat = t % 0.5;
            var kick = 0;
            if (tBeat < 0.2) {
                var kFreq = 42.0 + 85.0 * Math.exp(-25.0 * tBeat);
                kickPhase += 2.0 * Math.PI * kFreq / sr;
                kick = Math.sin(kickPhase) * Math.exp(-9.0 * tBeat) * 0.42;
            }

            // 2. Hi-hat
            var t16th = t % 0.125;
            var sixteenthIdx = Math.floor((t % 0.5) / 0.125);
            var hatGain = (sixteenthIdx === 1 || sixteenthIdx === 3) ? 0.12 : 0.05;
            var hat = (Math.random() * 2 - 1) * Math.exp(-65.0 * t16th) * hatGain;

            // 3. Bass
            var bassMult = (sixteenthIdx === 2) ? 2.0 : 1.0;
            var curBassFreq = chord.root * bassMult;
            bassPhase1 += 2.0 * Math.PI * curBassFreq / sr;
            bassPhase2 += 2.0 * Math.PI * (curBassFreq + 1.2) / sr;
            var p1 = (bassPhase1 / (2.0 * Math.PI)) % 1.0;
            var normP1 = (p1 < 0) ? p1 + 1.0 : p1;
            var saw1 = 2.0 * normP1 - 1.0;
            var p2 = (bassPhase2 / (2.0 * Math.PI)) % 1.0;
            var normP2 = (p2 < 0) ? p2 + 1.0 : p2;
            var saw2 = 2.0 * normP2 - 1.0;
            var rawBass = 0.5 * saw1 + 0.5 * saw2;

            var barPos = t / 16.0;
            var lfoCutoff = 400.0 + 1100.0 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * barPos - Math.PI / 2));
            var f = Math.max(0.005, Math.min(0.95, 2.0 * Math.sin(Math.PI * Math.min(lfoCutoff, sr * 0.4) / sr)));
            var high = rawBass - low - 0.45 * band;
            band += f * high;
            low += f * band;
            var bassEnv = Math.exp(-14.0 * t16th);
            var filteredBass = low * bassEnv * 0.32;

            // 4. Arp
            var arpIdx = Math.floor(t / 0.125) % 4;
            var arpNote = chord.arp[arpIdx];
            arpPhase += 2.0 * Math.PI * arpNote / sr;
            var arpEnv = Math.exp(-22.0 * t16th);
            var arp = Math.sin(arpPhase) * arpEnv * 0.16;

            // 5. Pad
            var pad = (Math.sin(2.0 * Math.PI * chord.pad[0] * t) +
                       Math.sin(2.0 * Math.PI * chord.pad[1] * t) +
                       Math.sin(2.0 * Math.PI * chord.pad[2] * t)) / 3.0 * 0.10;

            out[i] = kick + hat + filteredBass + arp + pad;
        }

        var fadeLen = Math.floor(sr * 0.01);
        for (var j = 0; j < fadeLen; j++) {
            var frac = j / fadeLen;
            out[j] = out[j] * frac + out[numSamples - fadeLen + j] * (1.0 - frac);
        }

        var maxVal = 0;
        for (var k = 0; k < numSamples; k++) {
            var a = Math.abs(out[k]);
            if (a > maxVal) maxVal = a;
        }
        var scale = (maxVal > 0.001) ? (1.0 / Math.max(1.0, maxVal * 1.1)) : 1.0;
        for (var m = 0; m < numSamples; m++) {
            out[m] = Math.max(-1.0, Math.min(1.0, out[m] * scale));
        }

        var source = ctx.createBufferSource();
        source.buffer = audioBuf;
        source.loop = true;

        var gainNode = ctx.createGain();
        var initialVol = window._stBgmIsMuted ? 0 : 0.28;
        gainNode.gain.setValueAtTime(initialVol, ctx.currentTime);

        source.connect(gainNode);
        gainNode.connect(ctx.destination);
        source.start(0);

        window._stBgmSource = source;
        window._stBgmGain = gainNode;
    } catch(e) {}
}
""")
private external fun jsStartMusic()

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function() {
    if (typeof window === 'undefined') return;
    try {
        if (window._stBgmSource) {
            window._stBgmSource.stop();
            window._stBgmSource.disconnect();
            window._stBgmSource = null;
        }
        window._stBgmStarted = false;
    } catch(e) {}
}
""")
private external fun jsStopMusic()

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function(muted) {
    if (typeof window === 'undefined') return;
    try {
        window._stBgmIsMuted = muted;
        if (window._stBgmGain && window._stAudioCtx) {
            var t = window._stAudioCtx.currentTime;
            window._stBgmGain.gain.cancelScheduledValues(t);
            window._stBgmGain.gain.setValueAtTime(window._stBgmGain.gain.value, t);
            window._stBgmGain.gain.linearRampToValueAtTime(muted ? 0.0001 : 0.28, t + 0.05);
        }
    } catch(e) {}
}
""")
private external fun jsSetMusicMuted(muted: Boolean)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""
function(durMs) {
    if (typeof window === 'undefined') return;
    try {
        if (window._stBgmIsMuted || !window._stBgmGain || !window._stAudioCtx) return;
        var t = window._stAudioCtx.currentTime;
        var durSec = durMs / 1000.0;
        window._stBgmGain.gain.cancelScheduledValues(t);
        window._stBgmGain.gain.setValueAtTime(window._stBgmGain.gain.value, t);
        window._stBgmGain.gain.linearRampToValueAtTime(0.07, t + 0.02);
        var holdEnd = t + durSec * 0.7;
        window._stBgmGain.gain.setValueAtTime(0.07, holdEnd);
        window._stBgmGain.gain.linearRampToValueAtTime(0.28, t + durSec);
    } catch(e) {}
}
""")
private external fun jsDuckMusic(durMs: Double)

