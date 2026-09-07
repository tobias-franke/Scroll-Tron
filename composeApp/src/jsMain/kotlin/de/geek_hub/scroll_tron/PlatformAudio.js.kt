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

        if (soundId === 0) { // STEER
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.type = 'triangle';
            osc.frequency.setValueAtTime(580, t);
            osc.frequency.exponentialRampToValueAtTime(320, t + 0.035);
            gain.gain.setValueAtTime(0.09, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.035);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(t);
            osc.stop(t + 0.035);
        } else if (soundId === 1) { // CRASH
            var duration = 0.4;
            var bufferSize = Math.floor(ctx.sampleRate * duration);
            var noiseBuffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
            var output = noiseBuffer.getChannelData(0);
            for (var i = 0; i < bufferSize; i++) {
                output[i] = Math.random() * 2 - 1;
            }
            var whiteNoise = ctx.createBufferSource();
            whiteNoise.buffer = noiseBuffer;

            var filter = ctx.createBiquadFilter();
            filter.type = 'bandpass';
            filter.frequency.setValueAtTime(2500, t);
            filter.frequency.exponentialRampToValueAtTime(80, t + duration);
            filter.Q.setValueAtTime(1.5, t);

            var noiseGain = ctx.createGain();
            noiseGain.gain.setValueAtTime(0.35, t);
            noiseGain.gain.exponentialRampToValueAtTime(0.001, t + duration);

            whiteNoise.connect(filter);
            filter.connect(noiseGain);
            noiseGain.connect(ctx.destination);

            whiteNoise.start(t);
            whiteNoise.stop(t + duration);

            var punchOsc = ctx.createOscillator();
            var punchGain = ctx.createGain();
            punchOsc.type = 'sawtooth';
            punchOsc.frequency.setValueAtTime(160, t);
            punchOsc.frequency.exponentialRampToValueAtTime(30, t + duration);
            punchGain.gain.setValueAtTime(0.3, t);
            punchGain.gain.exponentialRampToValueAtTime(0.001, t + duration);

            punchOsc.connect(punchGain);
            punchGain.connect(ctx.destination);

            punchOsc.start(t);
            punchOsc.stop(t + duration);
        } else if (soundId === 2) { // GAME_START
            var notes = [440, 554.37, 659.25, 880];
            for (var n = 0; n < notes.length; n++) {
                var noteStart = t + n * 0.08;
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.type = 'square';
                osc.frequency.setValueAtTime(notes[n], noteStart);
                gain.gain.setValueAtTime(0.08, noteStart);
                gain.gain.exponentialRampToValueAtTime(0.001, noteStart + 0.12);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(noteStart);
                osc.stop(noteStart + 0.12);
            }
        } else if (soundId === 3) { // GAME_OVER
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.type = 'sawtooth';
            osc.frequency.setValueAtTime(320, t);
            osc.frequency.exponentialRampToValueAtTime(65, t + 0.5);
            gain.gain.setValueAtTime(0.18, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.5);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(t);
            osc.stop(t + 0.5);
        } else if (soundId === 4) { // VICTORY
            var notes = [523.25, 659.25, 783.99, 1046.50];
            for (var n = 0; n < notes.length; n++) {
                var noteStart = t + n * 0.1;
                var noteDur = (n === notes.length - 1) ? 0.4 : 0.15;
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(notes[n], noteStart);
                gain.gain.setValueAtTime(0.15, noteStart);
                gain.gain.exponentialRampToValueAtTime(0.001, noteStart + noteDur);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(noteStart);
                osc.stop(noteStart + noteDur);
            }
        } else if (soundId === 5) { // UI_CLICK
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.type = 'sine';
            osc.frequency.setValueAtTime(880, t);
            osc.frequency.exponentialRampToValueAtTime(440, t + 0.04);
            gain.gain.setValueAtTime(0.12, t);
            gain.gain.exponentialRampToValueAtTime(0.001, t + 0.045);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(t);
            osc.stop(t + 0.045);
        }
    } catch(e) {}
    """)
}
