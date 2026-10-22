package com.junglerun.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.junglerun.game.game.FeedbackEvent
import com.junglerun.game.game.FeedbackSink
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * All game audio: synthesised SFX and animal voices (SoundPool), adaptive layered music
 * ([MusicEngine]) and haptics. Everything is generated on first launch and cached on disk,
 * so the APK ships with zero audio files.
 */
class SoundManager(private val context: Context) : FeedbackSink {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val sfx = ConcurrentHashMap<FeedbackEvent, Int>()
    private val voices = ConcurrentHashMap<Voice, Int>()
    private val music = MusicEngine()
    private var musicSet: MusicSet? = null
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
            music.enabled = value
        }

    /** Generates (or loads cached) audio. Call off the main thread. */
    fun prepare() {
        val dir = File(context.cacheDir, "audio_v2").apply { mkdirs() }
        fun load(event: FeedbackEvent, gen: () -> FloatArray) {
            val f = File(dir, "${event.name.lowercase()}.wav")
            if (!f.exists()) writeWav(f, Synth.normalize(gen(), 0.8f))
            sfx[event] = pool.load(f.absolutePath, 1)
        }
        load(FeedbackEvent.JUMP) { boing() }
        load(FeedbackEvent.SLIDE) { whoosh(0.28f) }
        load(FeedbackEvent.FOOD) { chime(floatArrayOf(1568f, 2093f), 0.05f) }
        load(FeedbackEvent.GOLDEN) { chime(floatArrayOf(1047f, 1319f, 1568f, 2093f), 0.06f) }
        load(FeedbackEvent.GEM) { chime(floatArrayOf(2093f, 2637f, 3136f), 0.07f) }
        load(FeedbackEvent.POWERUP) { chime(floatArrayOf(523f, 659f, 784f, 1047f, 1319f), 0.05f) }
        load(FeedbackEvent.STUMBLE) { thud() }
        load(FeedbackEvent.SMASH) { crash() }
        load(FeedbackEvent.ABILITY) { Synth.concat(whoosh(0.3f), chime(floatArrayOf(784f, 988f, 1175f, 1568f), 0.04f), gap = 0f) }
        load(FeedbackEvent.DOWN) { sadSlide() }
        load(FeedbackEvent.LAND) { softThump() }
        load(FeedbackEvent.COUNT) { chime(floatArrayOf(880f), 0.1f) }
        load(FeedbackEvent.GO) { chime(floatArrayOf(1319f, 1760f), 0.08f) }
        for (v in Voice.entries) {
            val f = File(dir, "voice_${v.name.lowercase()}.wav")
            if (!f.exists()) writeWav(f, Voices.render(v))
            voices[v] = pool.load(f.absolutePath, 1)
        }
        musicSet = loadOrRenderMusic(dir)
        music.set = musicSet
        music.start()
    }

    // ------------------------------------------------------------------ FeedbackSink

    override fun play(event: FeedbackEvent) {
        if (soundEnabled) {
            val vol = when (event) {
                FeedbackEvent.FOOD -> 0.45f
                FeedbackEvent.LAND -> 0.35f
                FeedbackEvent.SLIDE -> 0.6f
                else -> 0.85f
            }
            // Slight random pitch keeps repeated sounds from getting tiring.
            val rate = if (event == FeedbackEvent.FOOD || event == FeedbackEvent.JUMP) 0.95f + Random.nextFloat() * 0.1f else 1f
            sfx[event]?.let { pool.play(it, vol, vol, 1, 0, rate) }
        }
        when (event) {
            FeedbackEvent.DOWN -> music.playStinger(musicSet?.gameOver)
            else -> Unit
        }
        if (hapticsEnabled) {
            when (event) {
                FeedbackEvent.STUMBLE -> vibrate(45, 200)
                FeedbackEvent.DOWN -> vibrate(140, 255)
                FeedbackEvent.ABILITY, FeedbackEvent.SMASH -> vibrate(25, 140)
                FeedbackEvent.GEM, FeedbackEvent.POWERUP -> vibrate(15, 90)
                else -> Unit
            }
        }
    }

    override fun voice(v: Voice, volume: Float) {
        if (!soundEnabled) return
        voices[v]?.let { pool.play(it, volume, volume, 2, 0, 0.95f + Random.nextFloat() * 0.1f) }
    }

    override fun music(state: com.junglerun.game.game.MusicState) {
        music.mode = if (state.running) MusicMode.RUN else MusicMode.MENU
        music.danger = state.danger
        music.power = state.power
        music.tempo = 1f + state.speed01 * 0.14f
    }

    fun menuMusic() {
        music.mode = MusicMode.MENU
        music.danger = 0f
        music.power = false
        music.tempo = 1f
    }

    fun newBest() = music.playStinger(musicSet?.fanfare)

    fun pauseMusic() = music.pause()
    fun resumeMusic() = music.resume()

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

    // ------------------------------------------------------------------ sound design

    private fun boing(): FloatArray {
        val b = Synth.buffer(0.22f)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / Synth.RATE
            val f = 300f + 600f * (1f - exp(-t * 18f)) + 30f * sin(2 * PI * 30 * t).toFloat()
            ph += 2 * PI * f / Synth.RATE
            b[i] = sin(ph).toFloat() * exp(-t * 9f)
        }
        return b
    }

    private fun whoosh(len: Float): FloatArray {
        val b = Synth.buffer(len)
        val r = Random(11)
        var lp = 0f
        for (i in b.indices) {
            val t = i.toFloat() / Synth.RATE
            val k = t / len
            val cutoff = 0.05f + 0.25f * sin(PI * k).toFloat()
            lp += cutoff * ((r.nextFloat() * 2 - 1) - lp)
            b[i] = lp * sin(PI * k).toFloat() * 2f
        }
        return b
    }

    private fun chime(notes: FloatArray, gap: Float): FloatArray {
        val out = Synth.buffer(notes.size * gap + 0.35f)
        notes.forEachIndexed { i, f -> Synth.mix(out, Synth.marimba(f, 0.35f), (i * gap * Synth.RATE).toInt(), 0.7f) }
        return out
    }

    private fun thud(): FloatArray {
        val out = Synth.buffer(0.35f)
        Synth.mix(out, Synth.tom(70f), 0, 1f)
        Synth.mix(out, Synth.vowel(260f, 180f, 0.18f, 500f, 1000f, breath = 0.2f), (0.03f * Synth.RATE).toInt(), 0.5f)
        return out
    }

    private fun crash(): FloatArray {
        val out = Synth.buffer(0.4f)
        Synth.mix(out, Synth.kick(), 0, 0.8f)
        Synth.mix(out, Synth.hat(open = true, seed = 21), 0, 0.9f)
        Synth.mix(out, Synth.clap(8), 0, 0.6f)
        return out
    }

    private fun sadSlide(): FloatArray {
        val b = Synth.buffer(0.7f)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / Synth.RATE
            val f = 520f * exp(-t * 1.6f) + 20f * sin(2 * PI * 7 * t).toFloat()
            ph += 2 * PI * f / Synth.RATE
            b[i] = (sin(ph).toFloat() + 0.3f * sin(ph * 2).toFloat()) * (1f - t / 0.7f)
        }
        return b
    }

    private fun softThump(): FloatArray {
        val out = Synth.buffer(0.15f)
        Synth.mix(out, Synth.conga(110f), 0, 0.8f)
        return out
    }

    // ------------------------------------------------------------------ caching

    private fun loadOrRenderMusic(dir: File): MusicSet {
        val f = File(dir, "music.pcm")
        if (f.exists()) {
            try {
                DataInputStream(f.inputStream().buffered()).use { inp ->
                    fun read(): FloatArray {
                        val n = inp.readInt()
                        val bytes = ByteArray(n * 2)
                        inp.readFully(bytes)
                        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        return FloatArray(n) { bb.short / 32767f }
                    }
                    return MusicSet(read(), read(), read(), read(), read(), read())
                }
            } catch (_: Exception) {
                f.delete()
            }
        }
        val set = Music.render()
        try {
            DataOutputStream(FileOutputStream(File(dir, "music.tmp")).buffered()).use { out ->
                for (a in listOf(set.menu, set.run, set.danger, set.power, set.gameOver, set.fanfare)) {
                    out.writeInt(a.size)
                    val bb = ByteBuffer.allocate(a.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (v in a) bb.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
                    out.write(bb.array())
                }
            }
            File(dir, "music.tmp").renameTo(f)
        } catch (_: Exception) {
        }
        return set
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        val rate = Synth.RATE
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        // Write then rename, so a reader never sees a half-written file.
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use {
            it.write(header.array())
            it.write(data.array())
        }
        tmp.renameTo(file)
    }

    fun release() {
        music.stop()
        pool.release()
    }
}
