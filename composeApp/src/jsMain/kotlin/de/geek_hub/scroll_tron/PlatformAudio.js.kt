package de.geek_hub.scroll_tron

actual object PlatformAudio {
    actual fun play(sound: SoundEffect) {
        try {
            jsPlayAudio(sound.ordinal)
        } catch (_: Throwable) {}
    }
}

private fun jsPlayAudio(soundId: Int) {
    js("""
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
    """)
}
