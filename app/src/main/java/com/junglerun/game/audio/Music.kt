package com.junglerun.game.audio

import com.junglerun.game.audio.Synth.RATE
import com.junglerun.game.audio.Synth.midi

/** Pre-rendered music: loops that the [MusicEngine] layers and cross-fades in real time. */
class MusicSet(
    val menu: FloatArray,
    /** The run groove and its synced overlay layers (same length, same tempo). */
    val run: FloatArray,
    val danger: FloatArray,
    val power: FloatArray,
    val gameOver: FloatArray,
    val fanfare: FloatArray,
)

object Music {
    private const val RUN_BPM = 124f

    // C major / A minor. Progression per bar: Am F C G | Am F G G
    private val roots = intArrayOf(45, 41, 48, 43, 45, 41, 43, 43)
    private val chords = arrayOf(
        intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62),
        intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(55, 59, 62), intArrayOf(55, 59, 62),
    )

    // A catchy, bouncy marimba hook (16th-note steps, -1 = rest), 2 bars repeated with a variation.
    private val hookA = intArrayOf(
        76, -1, 72, -1, 69, -1, 72, 74, -1, 76, -1, 72, -1, 69, -1, -1,
        77, -1, 72, -1, 69, -1, 72, 77, -1, 76, -1, 74, -1, 72, -1, -1,
    )
    private val hookB = intArrayOf(
        76, -1, 72, -1, 69, -1, 72, 74, -1, 76, -1, 79, -1, 76, -1, -1,
        74, -1, 71, -1, 67, -1, 71, 74, -1, 79, -1, 77, 76, 74, -1, -1,
    )

    fun render(): MusicSet = MusicSet(menu(), run(), danger(), power(), gameOver(), fanfare())

    private fun steps(bpm: Float) = (RATE * 60f / bpm / 4f).toInt() // samples per 16th note

    fun run(): FloatArray {
        val st = steps(RUN_BPM)
        val bars = 8
        val out = FloatArray(st * 16 * bars)
        val kick = Synth.kick()
        val clap = Synth.clap()
        val hat = Synth.hat()
        val openHat = Synth.hat(open = true)
        val shaker = Synth.shaker()
        val congaHi = Synth.conga(260f)
        val congaLo = Synth.conga(190f)
        for (bar in 0 until bars) {
            val b0 = bar * 16 * st
            for (s in 0 until 16) {
                val at = b0 + s * st
                if (s == 0 || s == 8 || s == 10 && bar % 2 == 1) Synth.mixLoop(out, kick, at, 0.95f)
                if (s == 4 || s == 12) Synth.mixLoop(out, clap, at, 0.45f)
                if (s % 2 == 1) Synth.mixLoop(out, shaker, at, 0.22f)
                if (s % 4 == 2) Synth.mixLoop(out, hat, at, 0.18f)
                if (s == 14) Synth.mixLoop(out, openHat, at, 0.14f)
                if (s == 3 || s == 6 || s == 11) Synth.mixLoop(out, congaHi, at, 0.28f)
                if (s == 7 || s == 15) Synth.mixLoop(out, congaLo, at, 0.3f)
            }
            // Bass: root on 1, octave pops, passing note
            val root = roots[bar]
            val bassPat = intArrayOf(0, -1, -1, 12, -1, -1, 0, -1, 0, -1, -1, 12, -1, 7, -1, -1)
            for (s in 0 until 16) {
                val n = bassPat[s]
                if (n >= 0) Synth.mixLoop(out, Synth.bass(midi(root + n), st * 2.6f / RATE), b0 + s * st, 0.55f)
            }
            // Chord stabs on the off-beats
            for (s in intArrayOf(2, 6, 10, 14)) for (n in chords[bar]) Synth.mixLoop(out, Synth.pluck(midi(n), 0.16f), b0 + s * st, 0.07f)
            // Hook
            val hook = if (bar < 4) hookA else hookB
            for (s in 0 until 16) {
                val n = hook[(bar % 2) * 16 + s]
                if (n > 0) Synth.mixLoop(out, Synth.marimba(midi(n), 0.45f), b0 + s * st, 0.26f)
            }
        }
        return Synth.normalize(Synth.soften(out), 0.8f)
    }

    /** Extra layer while the chaser is close: tribal toms, 16th hats and a tense pulse. */
    fun danger(): FloatArray {
        val st = steps(RUN_BPM)
        val bars = 8
        val out = FloatArray(st * 16 * bars)
        val tomHi = Synth.tom(150f)
        val tomLo = Synth.tom(95f)
        val hat = Synth.hat(seed = 7)
        for (bar in 0 until bars) {
            val b0 = bar * 16 * st
            for (s in 0 until 16) {
                val at = b0 + s * st
                Synth.mixLoop(out, hat, at, if (s % 2 == 0) 0.16f else 0.1f)
                if (s in intArrayOf(0, 3, 6, 10, 12, 13, 14, 15)) Synth.mixLoop(out, if (s < 12) tomLo else tomHi, at, 0.5f)
                // Pulsing minor-second tension
                Synth.mixLoop(out, Synth.stab(midi(roots[bar] + (if (s % 4 == 3) 13 else 12)), 0.09f), at, 0.12f)
            }
        }
        return Synth.normalize(Synth.soften(out), 0.7f)
    }

    /** Sparkly arpeggio while a power-up or ability is active. */
    fun power(): FloatArray {
        val st = steps(RUN_BPM)
        val bars = 8
        val out = FloatArray(st * 16 * bars)
        for (bar in 0 until bars) {
            val b0 = bar * 16 * st
            val ch = chords[bar]
            for (s in 0 until 16) {
                val n = ch[s % 3] + 12 + (if (s / 8 == 1) 12 else 0)
                Synth.mixLoop(out, Synth.kalimba(midi(n), 0.3f), b0 + s * st, 0.22f)
            }
        }
        return Synth.normalize(out, 0.6f)
    }

    /** Calm, friendly menu loop: kalimba melody over a warm pad and soft shaker. */
    fun menu(): FloatArray {
        val bpm = 96f
        val st = steps(bpm)
        val bars = 8
        val out = FloatArray(st * 16 * bars)
        val shaker = Synth.shaker(5)
        val kick = Synth.kick()
        val melody = intArrayOf(
            72, -1, -1, 76, -1, -1, 79, -1, 76, -1, -1, -1, 74, -1, 72, -1,
            69, -1, -1, 72, -1, -1, 76, -1, 74, -1, 72, -1, 71, -1, -1, -1,
        )
        for (bar in 0 until bars) {
            val b0 = bar * 16 * st
            Synth.mixLoop(out, Synth.pad(FloatArray(3) { midi(chords[bar][it]) }, st * 16f / RATE + 0.3f), b0, 0.5f)
            Synth.mixLoop(out, Synth.bass(midi(roots[bar]), st * 7f / RATE), b0, 0.35f)
            Synth.mixLoop(out, Synth.bass(midi(roots[bar] + 7), st * 7f / RATE), b0 + 8 * st, 0.25f)
            for (s in 0 until 16) {
                val at = b0 + s * st
                if (s % 2 == 1) Synth.mixLoop(out, shaker, at, 0.12f)
                if (s == 0) Synth.mixLoop(out, kick, at, 0.35f)
                val n = melody[(bar % 2) * 16 + s]
                if (n > 0 && bar % 4 != 3) Synth.mixLoop(out, Synth.kalimba(midi(n)), at, 0.32f)
            }
        }
        return Synth.normalize(Synth.soften(out), 0.75f)
    }

    fun gameOver(): FloatArray {
        val out = Synth.buffer(2.2f)
        val notes = intArrayOf(76, 72, 69, 64)
        notes.forEachIndexed { i, n -> Synth.mix(out, Synth.marimba(midi(n), 0.9f), (i * 0.22f * RATE).toInt(), 0.6f) }
        Synth.mix(out, Synth.pad(floatArrayOf(midi(57), midi(60), midi(64)), 1.4f), (0.88f * RATE).toInt(), 0.5f)
        return Synth.normalize(out, 0.8f)
    }

    fun fanfare(): FloatArray {
        val out = Synth.buffer(2.0f)
        val notes = intArrayOf(72, 76, 79, 84, 79, 84)
        val times = floatArrayOf(0f, 0.12f, 0.24f, 0.36f, 0.6f, 0.72f)
        notes.forEachIndexed { i, n ->
            Synth.mix(out, Synth.marimba(midi(n), 0.8f), (times[i] * RATE).toInt(), 0.5f)
            Synth.mix(out, Synth.pluck(midi(n - 12), 0.3f), (times[i] * RATE).toInt(), 0.25f)
        }
        Synth.mix(out, Synth.clap(), (0.72f * RATE).toInt(), 0.4f)
        return Synth.normalize(out, 0.85f)
    }
}

/** Animal calls: what each runner and chaser "says" during a run. */
enum class Voice { MONKEY, RABBIT, PANDA, LION, BEAR, ELEPHANT, CHEETAH, GIRAFFE, TIGER, FOX, RHINO, BEES, CROC, HYENA }

object Voices {
    fun render(v: Voice): FloatArray = Synth.normalize(
        when (v) {
            // "Ooh-ooh-aah-aah!"
            Voice.MONKEY -> Synth.concat(
                Synth.vowel(380f, 520f, 0.16f, 330f, 850f, seed = 1),
                Synth.vowel(420f, 560f, 0.14f, 330f, 850f, seed = 2),
                Synth.vowel(520f, 760f, 0.2f, 760f, 1150f, vibrato = 0.02f, seed = 3),
                Synth.vowel(560f, 820f, 0.22f, 760f, 1150f, vibrato = 0.03f, seed = 4),
                gap = 0.05f,
            )
            Voice.RABBIT -> Synth.concat(
                Synth.vowel(1700f, 2300f, 0.07f, 2000f, 2800f, breath = 0.02f),
                Synth.vowel(1900f, 2500f, 0.06f, 2200f, 3000f, breath = 0.02f),
                gap = 0.06f,
            )
            Voice.PANDA -> Synth.vowel(330f, 280f, 0.45f, 520f, 1700f, vibrato = 0.03f, breath = 0.08f)
            Voice.LION, Voice.TIGER -> Synth.growl(if (v == Voice.LION) 95f else 120f, 1.1f, roughness = 28f, bright = 1100f)
            Voice.BEAR -> Synth.growl(80f, 0.8f, roughness = 18f, bright = 700f)
            Voice.ELEPHANT -> Synth.vowel(420f, 760f, 0.75f, 900f, 1800f, vibrato = 0.05f, breath = 0.12f, attack = 0.08f)
            Voice.CHEETAH -> Synth.concat(
                Synth.vowel(1100f, 1500f, 0.08f, 1300f, 2400f),
                Synth.vowel(1150f, 1550f, 0.08f, 1300f, 2400f),
                Synth.vowel(1200f, 1700f, 0.1f, 1300f, 2400f),
                gap = 0.05f,
            )
            Voice.GIRAFFE -> Synth.vowel(140f, 120f, 0.6f, 400f, 900f, breath = 0.1f, attack = 0.1f)
            Voice.FOX -> Synth.concat(
                Synth.vowel(900f, 650f, 0.12f, 1100f, 2000f, breath = 0.15f),
                Synth.vowel(950f, 700f, 0.12f, 1100f, 2000f, breath = 0.15f),
                gap = 0.1f,
            )
            Voice.RHINO -> Synth.growl(60f, 0.6f, roughness = 12f, bright = 500f)
            Voice.BEES -> buzz()
            Voice.CROC -> Synth.concat(Synth.growl(70f, 0.35f, 10f, 600f), Synth.clap(4), gap = 0.02f)
            Voice.HYENA -> Synth.concat(
                *Array(5) { Synth.vowel(700f + it * 40f, 820f + it * 40f, 0.08f, 900f, 1600f, breath = 0.1f, seed = it) },
                gap = 0.04f,
            )
        },
        0.85f,
    )

    private fun buzz(): FloatArray {
        val b = Synth.buffer(0.9f)
        for (i in b.indices) {
            val t = i.toFloat() / Synth.RATE
            val f = 220f + 30f * kotlin.math.sin(2 * Math.PI * 5 * t).toFloat()
            val saw = 2f * ((f * t) % 1f) - 1f
            val env = (t / 0.1f).coerceAtMost(1f) * ((0.9f - t) / 0.2f).coerceIn(0f, 1f)
            b[i] = saw * env * (0.6f + 0.4f * kotlin.math.sin(2 * Math.PI * 31 * t).toFloat())
        }
        return b
    }
}
