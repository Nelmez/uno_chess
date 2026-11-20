package com.example.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Pure-Kotlin procedural DSP synthesizer that generates crisp, tactile 16-bit PCM WAV buffers
 * for every [SoundEffect] with zero external audio asset dependencies.
 */
object SoundSynthesizer {

    const val SAMPLE_RATE = 44_100
    private const val TWO_PI = 2.0 * PI

    fun generatePcmSamples(effect: SoundEffect): ShortArray {
        val raw = when (effect) {
            SoundEffect.PIECE_MOVE -> synthWoodMove()
            SoundEffect.PIECE_CAPTURE -> synthWoodCapture()
            SoundEffect.CASTLE -> synthCastle()
            SoundEffect.CHECK -> synthCheckAlert()
            SoundEffect.CHECKMATE -> synthCheckmateFanfare()
            SoundEffect.FORFEIT -> synthForfeitVictory()
            SoundEffect.GAME_END_DRAW_OR_LOSS -> synthDrawOrLossCadence()
            SoundEffect.CARD_DRAW -> synthCardDraw()
            SoundEffect.SPECIAL_FREEZE -> synthSpecialFreeze()
            SoundEffect.SPECIAL_SHIELD -> synthSpecialShield()
            SoundEffect.SPECIAL_REVIVE -> synthSpecialRevive()
            SoundEffect.SPECIAL_HIJACK -> synthSpecialHijack()
            SoundEffect.SPECIAL_MERGE -> synthSpecialMerge()
            SoundEffect.SPECIAL_SACRIFICE -> synthSpecialSacrifice()
            SoundEffect.SPECIAL_PROMOTE -> synthSpecialPromote()
            SoundEffect.SPECIAL_SWAP -> synthSpecialSwap()
            SoundEffect.SPECIAL_CLONE -> synthSpecialClone()
            SoundEffect.SPECIAL_WILD -> synthSpecialWild()
            SoundEffect.SPECIAL_SUICIDE -> synthSpecialSuicide()
        }
        return normalizeAndConvert(raw)
    }

    fun generateWavBytes(effect: SoundEffect): ByteArray {
        val samples = generatePcmSamples(effect)
        return encodeWav(samples, SAMPLE_RATE)
    }

    /**
     * Wraps 16-bit signed mono PCM samples in a standard 44-byte RIFF/WAVE header.
     */
    fun encodeWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val byteRate = sampleRate * 2
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeAscii(s: String) {
            s.forEach { out.write(it.code) }
        }

        fun writeIntLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }

        fun writeShortLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }

        writeAscii("RIFF")
        writeIntLE(36 + dataSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16) // PCM subchunk size
        writeShortLE(1) // PCM audio format
        writeShortLE(1) // Mono
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(2) // Block align (1 ch * 16 bits / 8)
        writeShortLE(16) // Bits per sample
        writeAscii("data")
        writeIntLE(dataSize)

        for (s in samples) {
            writeShortLE(s.toInt())
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------------------
    // Tactile Chess Sounds
    // -------------------------------------------------------------------------

    /** Soft damped wooden chess piece thock (~95ms). */
    private fun synthWoodMove(): DoubleArray {
        val n = samplesForMs(95)
        val buf = DoubleArray(n)
        addWoodTap(buf, startSec = 0.0, baseFreq = 195.0, endFreq = 112.0, bodyFreq = 440.0, gain = 0.92, seed = 101)
        return buf
    }

    /** Crisp piece-on-piece clack followed by a heavier wooden settle (~175ms). */
    private fun synthWoodCapture(): DoubleArray {
        val n = samplesForMs(175)
        val buf = DoubleArray(n)
        val rng = Random(202)
        // Sharp high wooden collision transient
        var prevNoise = 0.0
        for (i in 0 until samplesForMs(32)) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = attackDecay(t, attack = 0.001, decay = 0.0075)
            val white = rng.nextDouble(-1.0, 1.0)
            // High-pass emphasis for crisp clack
            val hp = white - 0.65 * prevNoise
            prevNoise = white
            val clack = sin(TWO_PI * 960.0 * t) * 0.55 + sin(TWO_PI * 1680.0 * t) * 0.30 + hp * 0.55
            buf[i] += clack * env * 0.95
        }
        // Heavy board settle 20ms later
        addWoodTap(buf, startSec = 0.020, baseFreq = 165.0, endFreq = 78.0, bodyFreq = 355.0, gain = 1.05, seed = 203)
        return buf
    }

    /** Twin wooden taps for castling (King + Rook, ~220ms). */
    private fun synthCastle(): DoubleArray {
        val n = samplesForMs(220)
        val buf = DoubleArray(n)
        addWoodTap(buf, startSec = 0.0, baseFreq = 185.0, endFreq = 108.0, bodyFreq = 420.0, gain = 0.88, seed = 301)
        addWoodTap(buf, startSec = 0.095, baseFreq = 225.0, endFreq = 132.0, bodyFreq = 510.0, gain = 0.84, seed = 302)
        return buf
    }

    /** Crisp tense metallic bell dyad alerting Check (~290ms). */
    private fun synthCheckAlert(): DoubleArray {
        val n = samplesForMs(290)
        val buf = DoubleArray(n)
        addBellTone(buf, startSec = 0.0, freq = 587.33, durationSec = 0.27, gain = 0.65, brightness = 0.55) // D5
        addBellTone(buf, startSec = 0.038, freq = 880.00, durationSec = 0.24, gain = 0.72, brightness = 0.65) // A5
        return buf
    }

    /** Grand triumphant 4-note royal fanfare for Checkmate (~980ms). */
    private fun synthCheckmateFanfare(): DoubleArray {
        val n = samplesForMs(980)
        val buf = DoubleArray(n)
        // Warm ascending major fanfare: C5 -> E5 -> G5 -> C6 + bass C4 support
        addFanfareNote(buf, startSec = 0.00, freq = 523.25, durationSec = 0.26, gain = 0.55) // C5
        addFanfareNote(buf, startSec = 0.11, freq = 659.25, durationSec = 0.26, gain = 0.58) // E5
        addFanfareNote(buf, startSec = 0.22, freq = 783.99, durationSec = 0.30, gain = 0.62) // G5
        // Final triumphant chord bloom
        addFanfareNote(buf, startSec = 0.34, freq = 1046.50, durationSec = 0.62, gain = 0.72) // C6
        addFanfareNote(buf, startSec = 0.34, freq = 783.99, durationSec = 0.58, gain = 0.42) // G5
        addFanfareNote(buf, startSec = 0.34, freq = 523.25, durationSec = 0.56, gain = 0.40) // C5
        addFanfareNote(buf, startSec = 0.34, freq = 261.63, durationSec = 0.54, gain = 0.32) // C4 warmth
        addBellTone(buf, startSec = 0.36, freq = 2093.00, durationSec = 0.42, gain = 0.22, brightness = 0.4) // C7 sparkle
        return buf
    }

    /** Decisive victory bell resolution for opponent Forfeit (~560ms). */
    private fun synthForfeitVictory(): DoubleArray {
        val n = samplesForMs(560)
        val buf = DoubleArray(n)
        addFanfareNote(buf, startSec = 0.00, freq = 392.00, durationSec = 0.22, gain = 0.55) // G4
        addFanfareNote(buf, startSec = 0.11, freq = 523.25, durationSec = 0.25, gain = 0.62) // C5
        addFanfareNote(buf, startSec = 0.22, freq = 659.25, durationSec = 0.32, gain = 0.68) // E5
        addBellTone(buf, startSec = 0.22, freq = 1046.50, durationSec = 0.32, gain = 0.35, brightness = 0.5)
        return buf
    }

    /** Soft descending resolution for Stalemate (~580ms). */
    private fun synthDrawOrLossCadence(): DoubleArray {
        val n = samplesForMs(580)
        val buf = DoubleArray(n)
        addBellTone(buf, startSec = 0.00, freq = 392.00, durationSec = 0.24, gain = 0.55, brightness = 0.25) // G4
        addBellTone(buf, startSec = 0.15, freq = 349.23, durationSec = 0.24, gain = 0.52, brightness = 0.22) // F4
        addBellTone(buf, startSec = 0.30, freq = 329.63, durationSec = 0.27, gain = 0.55, brightness = 0.20) // E4
        return buf
    }

    // -------------------------------------------------------------------------
    // UNO Card & Special Effect Sounds
    // -------------------------------------------------------------------------

    /** Tactile crisp paper card slide & flick off the deck (~115ms). */
    private fun synthCardDraw(): DoubleArray {
        val n = samplesForMs(115)
        val buf = DoubleArray(n)
        val rng = Random(404)
        var lp = 0.0
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / n
            val white = rng.nextDouble(-1.0, 1.0)
            // Swept bandpass feel: brighter toward the flick tip
            val alpha = 0.18 + 0.45 * progress
            lp += alpha * (white - lp)
            val hp = lp - 0.72 * prev
            prev = lp

            // Rising slide envelope + sharp snap near 70ms
            val slideEnv = sin(PI * progress) * exp(-2.5 * progress)
            val snapT = t - 0.068
            val snapEnv = if (snapT >= 0.0) attackDecay(snapT, attack = 0.001, decay = 0.008) else 0.0
            val snapTone = sin(TWO_PI * 1250.0 * t) * 0.35 + hp * 0.75

            buf[i] = hp * slideEnv * 0.75 + snapTone * snapEnv * 0.70
        }
        return buf
    }

    /** Freeze: shimmering crystalline ice chime & frosty crackle (~360ms). */
    private fun synthSpecialFreeze(): DoubleArray {
        val n = samplesForMs(360)
        val buf = DoubleArray(n)
        addBellTone(buf, startSec = 0.00, freq = 1760.0, durationSec = 0.34, gain = 0.45, brightness = 0.8)
        addBellTone(buf, startSec = 0.025, freq = 2637.0, durationSec = 0.32, gain = 0.40, brightness = 0.85)
        addBellTone(buf, startSec = 0.055, freq = 3520.0, durationSec = 0.28, gain = 0.32, brightness = 0.7)
        // High frosty sparkle burst
        val rng = Random(505)
        var prev = 0.0
        for (i in 0 until samplesForMs(130)) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = attackDecay(t, attack = 0.003, decay = 0.032)
            val white = rng.nextDouble(-1.0, 1.0)
            val sparkle = (white - 0.88 * prev)
            prev = white
            buf[i] += sparkle * env * 0.32
        }
        return buf
    }

    /** Shield: resonant bronze/steel aegis ring with rising protective harmonic (~340ms). */
    private fun synthSpecialShield(): DoubleArray {
        val n = samplesForMs(340)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.0, durationSec = 0.14, startFreq = 260.0, endFreq = 523.25, gain = 0.45)
        addBellTone(buf, startSec = 0.03, freq = 392.0, durationSec = 0.30, gain = 0.55, brightness = 0.65)
        addBellTone(buf, startSec = 0.04, freq = 587.33, durationSec = 0.29, gain = 0.48, brightness = 0.70)
        addBellTone(buf, startSec = 0.05, freq = 1174.66, durationSec = 0.26, gain = 0.28, brightness = 0.75)
        return buf
    }

    /** Revive: ethereal ascending harp/chime arpeggio (~420ms). */
    private fun synthSpecialRevive(): DoubleArray {
        val n = samplesForMs(420)
        val buf = DoubleArray(n)
        addBellTone(buf, startSec = 0.00, freq = 659.25, durationSec = 0.24, gain = 0.45, brightness = 0.4) // E5
        addBellTone(buf, startSec = 0.07, freq = 830.61, durationSec = 0.24, gain = 0.48, brightness = 0.45) // G#5
        addBellTone(buf, startSec = 0.14, freq = 987.77, durationSec = 0.25, gain = 0.52, brightness = 0.5) // B5
        addBellTone(buf, startSec = 0.21, freq = 1318.51, durationSec = 0.20, gain = 0.58, brightness = 0.55) // E6
        return buf
    }

    /** Hijack: mischievous downward-dip then upward-snap pitch bend (~310ms). */
    private fun synthSpecialHijack(): DoubleArray {
        val n = samplesForMs(310)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.0, durationSec = 0.12, startFreq = 680.0, endFreq = 340.0, gain = 0.55)
        addPitchSweep(buf, startSec = 0.11, durationSec = 0.18, startFreq = 360.0, endFreq = 920.0, gain = 0.62)
        addBellTone(buf, startSec = 0.18, freq = 920.0, durationSec = 0.12, gain = 0.30, brightness = 0.7)
        return buf
    }

    /** Merge: heroic rising forge power-up chord (~390ms). */
    private fun synthSpecialMerge(): DoubleArray {
        val n = samplesForMs(390)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.0, durationSec = 0.15, startFreq = 293.66, endFreq = 587.33, gain = 0.42)
        addFanfareNote(buf, startSec = 0.08, freq = 392.00, durationSec = 0.29, gain = 0.45) // G4
        addFanfareNote(buf, startSec = 0.13, freq = 587.33, durationSec = 0.25, gain = 0.52) // D5
        addFanfareNote(buf, startSec = 0.18, freq = 783.99, durationSec = 0.20, gain = 0.58) // G5
        return buf
    }

    /** Sacrifice: deep ceremonial resonance followed by twin wooden split taps (~360ms). */
    private fun synthSpecialSacrifice(): DoubleArray {
        val n = samplesForMs(360)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.0, durationSec = 0.22, startFreq = 160.0, endFreq = 68.0, gain = 0.85)
        addWoodTap(buf, startSec = 0.115, baseFreq = 250.0, endFreq = 145.0, bodyFreq = 560.0, gain = 0.72, seed = 601)
        addWoodTap(buf, startSec = 0.195, baseFreq = 290.0, endFreq = 165.0, bodyFreq = 640.0, gain = 0.76, seed = 602)
        return buf
    }

    /** Promote: bright royal coronation triad (~410ms). */
    private fun synthSpecialPromote(): DoubleArray {
        val n = samplesForMs(410)
        val buf = DoubleArray(n)
        addFanfareNote(buf, startSec = 0.00, freq = 698.46, durationSec = 0.20, gain = 0.52) // F5
        addFanfareNote(buf, startSec = 0.09, freq = 880.00, durationSec = 0.22, gain = 0.56) // A5
        addFanfareNote(buf, startSec = 0.18, freq = 1046.50, durationSec = 0.22, gain = 0.65) // C6
        addBellTone(buf, startSec = 0.18, freq = 2093.00, durationSec = 0.21, gain = 0.25, brightness = 0.5)
        return buf
    }

    /** Swap Positions: playful crossing dual-pitch glide (~270ms). */
    private fun synthSpecialSwap(): DoubleArray {
        val n = samplesForMs(270)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.0, durationSec = 0.23, startFreq = 440.0, endFreq = 784.0, gain = 0.48)
        addPitchSweep(buf, startSec = 0.02, durationSec = 0.23, startFreq = 784.0, endFreq = 440.0, gain = 0.45)
        return buf
    }

    /** Clone: crisp resonant pop followed by a twin harmonic echo (~320ms). */
    private fun synthSpecialClone(): DoubleArray {
        val n = samplesForMs(320)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.00, durationSec = 0.11, startFreq = 480.0, endFreq = 659.25, gain = 0.55)
        addBellTone(buf, startSec = 0.02, freq = 659.25, durationSec = 0.14, gain = 0.42, brightness = 0.45)
        // Twin echo 100ms later
        addPitchSweep(buf, startSec = 0.105, durationSec = 0.13, startFreq = 520.0, endFreq = 783.99, gain = 0.55)
        addBellTone(buf, startSec = 0.12, freq = 987.77, durationSec = 0.18, gain = 0.45, brightness = 0.55)
        return buf
    }

    /** Wild Card: magical prismatic 5-note major-9th sparkle cascade (~480ms). */
    private fun synthSpecialWild(): DoubleArray {
        val n = samplesForMs(480)
        val buf = DoubleArray(n)
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 987.77, 1174.66) // C5 E5 G5 B5 D6
        notes.forEachIndexed { idx, freq ->
            addBellTone(
                buf = buf,
                startSec = idx * 0.055,
                freq = freq,
                durationSec = 0.24,
                gain = 0.42 + idx * 0.03,
                brightness = 0.65
            )
        }
        return buf
    }

    /** Suicide: ominous descending tritone drop & low sub thud (~400ms). */
    private fun synthSpecialSuicide(): DoubleArray {
        val n = samplesForMs(400)
        val buf = DoubleArray(n)
        addPitchSweep(buf, startSec = 0.00, durationSec = 0.18, startFreq = 246.94, endFreq = 174.61, gain = 0.65) // B3 -> F3 tritone
        addPitchSweep(buf, startSec = 0.14, durationSec = 0.25, startFreq = 174.61, endFreq = 82.41, gain = 0.80) // F3 -> E2
        return buf
    }

    // -------------------------------------------------------------------------
    // DSP Building Blocks
    // -------------------------------------------------------------------------

    private fun samplesForMs(ms: Int): Int = ((SAMPLE_RATE * ms) / 1000.0).toInt().coerceAtLeast(1)

    private fun attackDecay(t: Double, attack: Double, decay: Double): Double {
        if (t < 0.0) return 0.0
        val atk = if (attack <= 0.0 || t >= attack) 1.0 else t / attack
        return atk * exp(-t / decay)
    }

    private fun addWoodTap(
        buf: DoubleArray,
        startSec: Double,
        baseFreq: Double,
        endFreq: Double,
        bodyFreq: Double,
        gain: Double,
        seed: Int
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt()
        val length = samplesForMs(85)
        val rng = Random(seed)
        var phaseLow = 0.0
        var phaseBody = 0.0
        var lpNoise = 0.0

        for (i in 0 until length) {
            val idx = startIdx + i
            if (idx !in buf.indices) continue
            val t = i.toDouble() / SAMPLE_RATE
            val freq = endFreq + (baseFreq - endFreq) * exp(-t / 0.012)
            phaseLow += TWO_PI * freq / SAMPLE_RATE
            phaseBody += TWO_PI * bodyFreq / SAMPLE_RATE

            val envLow = attackDecay(t, attack = 0.0018, decay = 0.017)
            val envBody = attackDecay(t, attack = 0.0012, decay = 0.009)
            val envClick = attackDecay(t, attack = 0.0005, decay = 0.0032)

            val white = rng.nextDouble(-1.0, 1.0)
            lpNoise += 0.35 * (white - lpNoise)

            val sample = (
                sin(phaseLow) * envLow * 0.72 +
                    sin(phaseBody) * envBody * 0.38 +
                    lpNoise * envClick * 0.45
                ) * gain
            buf[idx] += sample
        }
    }

    private fun addBellTone(
        buf: DoubleArray,
        startSec: Double,
        freq: Double,
        durationSec: Double,
        gain: Double,
        brightness: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt()
        val length = (durationSec * SAMPLE_RATE).toInt()
        val decay = durationSec * 0.34
        for (i in 0 until length) {
            val idx = startIdx + i
            if (idx !in buf.indices) continue
            val t = i.toDouble() / SAMPLE_RATE
            val fadeOut = if (i > length - 400) (length - i) / 400.0 else 1.0
            val env1 = attackDecay(t, attack = 0.004, decay = decay) * fadeOut
            val env2 = attackDecay(t, attack = 0.003, decay = decay * 0.55) * fadeOut
            val env3 = attackDecay(t, attack = 0.002, decay = decay * 0.32) * fadeOut

            val fundamental = sin(TWO_PI * freq * t) * env1
            val harmonic2 = sin(TWO_PI * (freq * 2.0) * t) * env2 * (0.35 * brightness)
            val harmonic3 = sin(TWO_PI * (freq * 2.756) * t) * env3 * (0.20 * brightness) // subtle bell overtone
            buf[idx] += (fundamental + harmonic2 + harmonic3) * gain
        }
    }

    private fun addFanfareNote(
        buf: DoubleArray,
        startSec: Double,
        freq: Double,
        durationSec: Double,
        gain: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt()
        val length = (durationSec * SAMPLE_RATE).toInt()
        val decay = durationSec * 0.52
        for (i in 0 until length) {
            val idx = startIdx + i
            if (idx !in buf.indices) continue
            val t = i.toDouble() / SAMPLE_RATE
            val fadeOut = if (i > length - 600) (length - i) / 600.0 else 1.0
            val env = attackDecay(t, attack = 0.008, decay = decay) * fadeOut
            val h1 = sin(TWO_PI * freq * t)
            val h2 = sin(TWO_PI * freq * 2.0 * t) * 0.42
            val h3 = sin(TWO_PI * freq * 3.0 * t) * 0.18
            val h4 = sin(TWO_PI * freq * 4.0 * t) * 0.07
            buf[idx] += (h1 + h2 + h3 + h4) * env * gain * 0.65
        }
    }

    private fun addPitchSweep(
        buf: DoubleArray,
        startSec: Double,
        durationSec: Double,
        startFreq: Double,
        endFreq: Double,
        gain: Double
    ) {
        val startIdx = (startSec * SAMPLE_RATE).toInt()
        val length = (durationSec * SAMPLE_RATE).toInt()
        var phase = 0.0
        for (i in 0 until length) {
            val idx = startIdx + i
            if (idx !in buf.indices) continue
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / length.coerceAtLeast(1)
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += TWO_PI * freq / SAMPLE_RATE

            val fadeOut = if (i > length - 450) (length - i) / 450.0 else 1.0
            val env = attackDecay(t, attack = 0.005, decay = durationSec * 0.45) * fadeOut
            val wave = sin(phase) * 0.78 + sin(phase * 2.0) * 0.22
            buf[idx] += wave * env * gain
        }
    }

    private fun normalizeAndConvert(raw: DoubleArray): ShortArray {
        val peak = raw.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
        val scale = if (peak > 0.0001) 0.86 / peak else 1.0
        return ShortArray(raw.size) { i ->
            val shaped = tanh(raw[i] * scale * 1.15) / tanh(1.15)
            (shaped * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
