package com.junglerun.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.min

enum class MusicMode { MENU, RUN, SILENT }

/**
 * Real-time music mixer on a streaming [AudioTrack]. Layers (menu, run groove, danger drums,
 * power-up arpeggio) are cross-faded according to what is happening in the game, the groove
 * speeds up with the runner, and one-shot stingers (game over, new best) are mixed on top.
 */
class MusicEngine {
    @Volatile var set: MusicSet? = null
    @Volatile var mode = MusicMode.MENU
    /** 0..1 — how close the chaser is. */
    @Volatile var danger = 0f
    @Volatile var power = false
    /** Playback rate of the run groove (1.0 … ~1.15 as the run gets faster). */
    @Volatile var tempo = 1f
    @Volatile var volume = 0.6f
    @Volatile var enabled = true

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false

    @Volatile private var stinger: FloatArray? = null
    private var stingerPos = 0

    fun playStinger(s: FloatArray?) {
        if (s == null) return
        stingerPos = 0
        stinger = s
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "JungleRun-Music").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frames = 1024
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, frames * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }
        track = t
        t.play()
        val out = ShortArray(frames)
        var menuPos = 0.0
        var runPos = 0.0
        // Smoothed gains
        var gMenu = 0f
        var gRun = 0f
        var gDanger = 0f
        var gPower = 0f
        var master = 0f
        val smooth = 0.0012f

        while (running) {
            if (paused) {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                }
                continue
            }
            val s = set
            val m = mode
            val targetMaster = if (enabled && s != null && m != MusicMode.SILENT) volume else 0f
            val tMenu = if (m == MusicMode.MENU) 1f else 0f
            val tRun = if (m == MusicMode.RUN) 1f else 0f
            val tDanger = if (m == MusicMode.RUN) ((danger - 0.35f) / 0.5f).coerceIn(0f, 1f) else 0f
            val tPower = if (m == MusicMode.RUN && power) 0.8f else 0f
            val rate = tempo.toDouble()
            for (i in 0 until frames) {
                gMenu += (tMenu - gMenu) * smooth
                gRun += (tRun - gRun) * smooth
                gDanger += (tDanger - gDanger) * smooth
                gPower += (tPower - gPower) * smooth
                master += (targetMaster - master) * smooth
                var v = 0f
                if (s != null) {
                    if (gMenu > 0.001f) {
                        v += s.menu[menuPos.toInt()] * gMenu
                    }
                    if (gRun > 0.001f || gDanger > 0.001f || gPower > 0.001f) {
                        val n = s.run.size
                        val i0 = runPos.toInt()
                        val i1 = (i0 + 1) % n
                        val f = (runPos - i0).toFloat()
                        fun at(a: FloatArray) = a[i0] + (a[i1] - a[i0]) * f
                        v += at(s.run) * gRun * 0.85f
                        if (gDanger > 0.001f) v += at(s.danger) * gDanger * 0.6f
                        if (gPower > 0.001f) v += at(s.power) * gPower * 0.45f
                    }
                    menuPos += 1.0
                    if (menuPos >= s.menu.size) menuPos -= s.menu.size
                    runPos += rate
                    if (runPos >= s.run.size) runPos -= s.run.size
                }
                v *= master
                val st = stinger
                if (st != null) {
                    if (stingerPos < st.size) {
                        v += st[stingerPos++] * volume * 0.9f
                    } else {
                        stinger = null
                    }
                }
                val clipped = v / (1f + kotlin.math.abs(v) * 0.3f)
                out[i] = (min(1f, maxOf(-1f, clipped)) * 32000).toInt().toShort()
            }
            t.write(out, 0, frames)
        }
        try {
            t.stop()
        } catch (_: IllegalStateException) {
        }
        t.release()
        track = null
    }
}
