package com.junglerun.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.junglerun.game.game.FeedbackEvent
import com.junglerun.game.game.FeedbackSink
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sound effects and background music synthesised at first launch into small WAV files,
 * so the game ships with zero audio assets. Also handles haptic feedback.
 */
class SoundManager(private val context: Context) : FeedbackSink {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<FeedbackEvent, Int>()
    private var music: MediaPlayer? = null
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile var soundEnabled = true
    @Volatile var hapticsEnabled = true
    var musicEnabled = true
        set(value) {
            field = value
            if (value) startMusic() else stopMusic()
        }

    /** Generates the WAVs (cached on disk) and loads them. Call off the main thread. */
    fun prepare() {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        fun load(event: FeedbackEvent, name: String, gen: () -> FloatArray) {
            val f = File(dir, "$name.wav")
            if (!f.exists()) writeWav(f, gen())
            ids[event] = pool.load(f.absolutePath, 1)
        }
        load(FeedbackEvent.JUMP, "jump") { sweep(320f, 760f, 0.14f, 0.5f) }
        load(FeedbackEvent.SLIDE, "slide") { noise(0.18f, 0.25f) }
        load(FeedbackEvent.FOOD, "food") { tone(1320f, 0.07f, 0.35f) + tone(1760f, 0.08f, 0.3f) }
        load(FeedbackEvent.GOLDEN, "golden") { tone(1046f, 0.07f, 0.4f) + tone(1318f, 0.07f, 0.4f) + tone(1568f, 0.14f, 0.4f) }
        load(FeedbackEvent.GEM, "gem") { tone(1568f, 0.06f, 0.4f) + tone(2093f, 0.06f, 0.4f) + tone(2637f, 0.18f, 0.35f) }
        load(FeedbackEvent.POWERUP, "powerup") { sweep(400f, 1400f, 0.35f, 0.45f) }
        load(FeedbackEvent.STUMBLE, "stumble") { sweep(220f, 90f, 0.25f, 0.6f) + noise(0.08f, 0.3f) }
        load(FeedbackEvent.SMASH, "smash") { noise(0.22f, 0.55f) }
        load(FeedbackEvent.ABILITY, "ability") { sweep(200f, 900f, 0.25f, 0.5f) + sweep(900f, 1800f, 0.2f, 0.4f) }
        load(FeedbackEvent.DOWN, "down") { sweep(600f, 120f, 0.6f, 0.6f) }

        val musicFile = File(dir, "music_v1.wav")
        if (!musicFile.exists()) writeWav(musicFile, jungleLoop())
    }

    override fun play(event: FeedbackEvent) {
        if (soundEnabled) {
            ids[event]?.let { pool.play(it, 0.9f, 0.9f, 1, 0, 1f) }
        }
        if (hapticsEnabled) {
            when (event) {
                FeedbackEvent.STUMBLE -> vibrate(40, 180)
                FeedbackEvent.DOWN -> vibrate(120, 255)
                FeedbackEvent.ABILITY, FeedbackEvent.SMASH -> vibrate(25, 120)
                else -> Unit
            }
        }
    }

    private fun vibrate(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    fun startMusic() {
        if (!musicEnabled || music != null) return
        val f = File(File(context.cacheDir, "sfx"), "music_v1.wav")
        if (!f.exists()) return
        music = try {
            MediaPlayer().apply {
                setDataSource(f.absolutePath)
                isLooping = true
                setVolume(0.35f, 0.35f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun stopMusic() {
        music?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        music = null
    }

    fun pauseMusic() {
        try {
            music?.pause()
        } catch (_: IllegalStateException) {
        }
    }

    fun resumeMusic() {
        if (!musicEnabled) return
        if (music == null) startMusic() else try {
            music?.start()
        } catch (_: IllegalStateException) {
        }
    }

    // ------------------------------------------------------------------ synthesis

    private operator fun FloatArray.plus(other: FloatArray): FloatArray {
        val out = FloatArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }

    private fun tone(freq: Float, dur: Float, vol: Float): FloatArray {
        val n = (RATE * dur).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE.toFloat()
            val env = exp(-t * 18f) * (if (i < 80) i / 80f else 1f)
            (sin(2 * PI * freq * t).toFloat() + 0.3f * sin(4 * PI * freq * t).toFloat()) * env * vol
        }
    }

    private fun sweep(from: Float, to: Float, dur: Float, vol: Float): FloatArray {
        val n = (RATE * dur).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val k = i / n.toFloat()
            val f = from + (to - from) * k
            phase += 2 * PI * f / RATE
            val env = (1f - k) * (if (i < 100) i / 100f else 1f)
            (if (sin(phase) > 0) 1f else -1f) * 0.35f * env * vol + sin(phase).toFloat() * 0.65f * env * vol
        }
    }

    private fun noise(dur: Float, vol: Float): FloatArray {
        val n = (RATE * dur).toInt()
        val r = Random(7)
        var last = 0f
        return FloatArray(n) { i ->
            val env = 1f - i / n.toFloat()
            last = last * 0.6f + (r.nextFloat() * 2f - 1f) * 0.4f
            last * env * vol
        }
    }

    /** A simple 8-second tribal drum + marimba groove. */
    private fun jungleLoop(): FloatArray {
        val bpm = 120f
        val beat = 60f / bpm
        val bars = 4
        val total = (RATE * beat * 4 * bars).toInt()
        val out = FloatArray(total)
        fun add(start: Float, data: FloatArray) {
            val s0 = (start * RATE).toInt()
            for (i in data.indices) {
                val j = s0 + i
                if (j < total) out[j] += data[i]
            }
        }
        val kick = FloatArray((RATE * 0.25f).toInt()) { i ->
            val t = i / RATE.toFloat()
            sin(2 * PI * (110f - 60f * t * 4f) * t).toFloat() * exp(-t * 14f) * 0.8f
        }
        val r = Random(3)
        val shaker = FloatArray((RATE * 0.06f).toInt()) { i -> (r.nextFloat() * 2 - 1) * exp(-i / 300f) * 0.15f }
        val scale = floatArrayOf(392f, 440f, 523.25f, 587.33f, 659.25f, 783.99f)
        val melody = intArrayOf(0, 2, 3, 2, 4, 3, 2, 1, 0, 2, 3, 5, 4, 3, 2, 0)
        for (bar in 0 until bars) {
            for (b in 0 until 4) {
                val t = (bar * 4 + b) * beat
                if (b == 0 || b == 2 || (b == 3 && bar % 2 == 1)) add(t, kick)
                add(t + beat / 2f, shaker)
                add(t + beat / 4f, shaker)
            }
            for (k in 0 until 4) {
                val note = scale[melody[(bar * 4 + k) % melody.size]]
                add((bar * 4 + k) * beat + beat * 0.5f, marimba(note))
            }
        }
        return out
    }

    private fun marimba(freq: Float): FloatArray {
        val n = (RATE * 0.4f).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE.toFloat()
            (sin(2 * PI * freq * t).toFloat() + 0.25f * sin(2 * PI * freq * 4 * t).toFloat()) * exp(-t * 9f) * 0.22f
        }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        FileOutputStream(file).use {
            it.write(header.array())
            it.write(data.array())
        }
    }

    fun release() {
        stopMusic()
        pool.release()
    }

    companion object {
        private const val RATE = 22050
    }
}
