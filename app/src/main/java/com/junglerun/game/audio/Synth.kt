package com.junglerun.game.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tiny offline synthesiser: drums, bass, marimba/kalimba, pads and animal voices rendered into
 * float buffers at start-up. Pure Kotlin (no Android) so it can be unit-tested on the JVM.
 */
object Synth {
    const val RATE = 22050
    private const val TAU = 2.0 * PI

    fun midi(n: Int): Float = (440.0 * 2.0.pow((n - 69) / 12.0)).toFloat()

    fun buffer(seconds: Float) = FloatArray((seconds * RATE).toInt())

    fun mix(dst: FloatArray, src: FloatArray, at: Int, gain: Float = 1f) {
        val n = minOf(src.size, dst.size - at)
        for (i in 0 until n) if (at + i >= 0) dst[at + i] += src[i] * gain
    }

    /** Wraps around the end of [dst], so loops stay seamless. */
    fun mixLoop(dst: FloatArray, src: FloatArray, at: Int, gain: Float = 1f) {
        for (i in src.indices) {
            val j = (at + i) % dst.size
            dst[j] += src[i] * gain
        }
    }

    fun normalize(buf: FloatArray, peak: Float = 0.9f): FloatArray {
        var m = 0f
        for (v in buf) m = max(m, kotlin.math.abs(v))
        if (m > 0f) {
            val k = peak / m
            for (i in buf.indices) buf[i] *= k
        }
        return buf
    }

    /** Gentle saturation so layered mixes never hard-clip. */
    fun soften(buf: FloatArray): FloatArray {
        for (i in buf.indices) {
            val x = buf[i]
            buf[i] = x / (1f + kotlin.math.abs(x) * 0.6f)
        }
        return buf
    }

    private fun lowpass(buf: FloatArray, cutoff: Float): FloatArray {
        val rc = 1f / (TAU.toFloat() * cutoff)
        val a = (1f / RATE) / (rc + 1f / RATE)
        var y = 0f
        for (i in buf.indices) {
            y += a * (buf[i] - y)
            buf[i] = y
        }
        return buf
    }

    private fun highpass(buf: FloatArray, cutoff: Float): FloatArray {
        val rc = 1f / (TAU.toFloat() * cutoff)
        val a = rc / (rc + 1f / RATE)
        var prevX = 0f
        var y = 0f
        for (i in buf.indices) {
            val x = buf[i]
            y = a * (y + x - prevX)
            prevX = x
            buf[i] = y
        }
        return buf
    }

    // ------------------------------------------------------------------ drums

    fun kick(): FloatArray {
        val b = buffer(0.32f)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val f = 48f + 110f * exp(-t * 28f)
            ph += TAU * f / RATE
            b[i] = (sin(ph) * exp(-t * 9.0)).toFloat() + (if (i < 60) (Random.nextFloat() - 0.5f) * 0.3f else 0f)
        }
        return b
    }

    fun clap(seed: Int = 1): FloatArray {
        val r = Random(seed)
        val b = buffer(0.22f)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val bursts = if (t < 0.03f) (if ((t * 1000).toInt() % 10 < 4) 1f else 0.3f) else 1f
            b[i] = (r.nextFloat() * 2 - 1) * exp(-t * 22f) * bursts + sin(TAU * 190 * t).toFloat() * exp(-t * 40f) * 0.4f
        }
        return highpass(b, 900f)
    }

    fun hat(open: Boolean = false, seed: Int = 2): FloatArray {
        val r = Random(seed)
        val b = buffer(if (open) 0.18f else 0.05f)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            b[i] = (r.nextFloat() * 2 - 1) * exp(-t * (if (open) 18f else 70f))
        }
        return highpass(b, 6000f)
    }

    fun shaker(seed: Int = 3): FloatArray {
        val r = Random(seed)
        val b = buffer(0.09f)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val env = (t / 0.02f).coerceAtMost(1f) * exp(-t * 35f)
            b[i] = (r.nextFloat() * 2 - 1) * env
        }
        return highpass(b, 4000f)
    }

    /** Hand drum / conga with a little pitch drop. */
    fun conga(freq: Float): FloatArray {
        val b = buffer(0.25f)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val f = freq * (1f + 0.35f * exp(-t * 40f))
            ph += TAU * f / RATE
            b[i] = (sin(ph) * exp(-t * 14.0)).toFloat()
        }
        return b
    }

    fun tom(freq: Float): FloatArray {
        val b = buffer(0.4f)
        var ph = 0.0
        val r = Random(9)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val f = freq * (1f + 0.6f * exp(-t * 18f))
            ph += TAU * f / RATE
            b[i] = (sin(ph) * exp(-t * 7.0)).toFloat() + (r.nextFloat() - 0.5f) * exp(-t * 60f) * 0.4f
        }
        return b
    }

    // ------------------------------------------------------------------ tonal

    fun marimba(freq: Float, len: Float = 0.5f): FloatArray {
        val b = buffer(len)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val a = (t / 0.003f).coerceAtMost(1f)
            b[i] = a * (sin(TAU * freq * t).toFloat() * exp(-t * 6f) +
                0.35f * sin(TAU * freq * 4 * t).toFloat() * exp(-t * 22f) +
                0.12f * sin(TAU * freq * 10 * t).toFloat() * exp(-t * 50f))
        }
        return b
    }

    fun kalimba(freq: Float, len: Float = 0.9f): FloatArray {
        val b = buffer(len)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val a = (t / 0.002f).coerceAtMost(1f)
            b[i] = a * (sin(TAU * freq * t).toFloat() * exp(-t * 3.2f) +
                0.22f * sin(TAU * freq * 5.4 * t).toFloat() * exp(-t * 18f))
        }
        return b
    }

    fun pluck(freq: Float, len: Float = 0.25f): FloatArray {
        val b = buffer(len)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val saw = 2f * ((freq * t) % 1f) - 1f
            b[i] = saw * exp(-t * 14f) * 0.6f + sin(TAU * freq * 2 * t).toFloat() * exp(-t * 20f) * 0.3f
        }
        return lowpass(b, 3500f)
    }

    fun bass(freq: Float, len: Float): FloatArray {
        val b = buffer(len)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val env = (t / 0.005f).coerceAtMost(1f) * exp(-t * 3.5f) * (if (t > len - 0.02f) (len - t) / 0.02f else 1f)
            val saw = 2f * ((freq * t) % 1f) - 1f
            b[i] = (sin(TAU * freq * t).toFloat() * 0.8f + saw * 0.35f) * env
        }
        return lowpass(b, 900f)
    }

    fun pad(freqs: FloatArray, len: Float): FloatArray {
        val b = buffer(len)
        for (f in freqs) {
            for (d in floatArrayOf(-0.6f, 0.6f)) {
                val ff = f * (1f + d / 100f)
                for (i in b.indices) {
                    val t = i.toFloat() / RATE
                    val env = (t / 0.4f).coerceAtMost(1f) * ((len - t) / 0.4f).coerceIn(0f, 1f)
                    b[i] += (2f * ((ff * t) % 1f) - 1f) * env * 0.12f
                }
            }
        }
        return lowpass(lowpass(b, 1400f), 1800f)
    }

    fun stab(freq: Float, len: Float = 0.12f): FloatArray {
        val b = buffer(len)
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val sq = if (sin(TAU * freq * t) > 0) 1f else -1f
            b[i] = sq * exp(-t * 18f) * 0.5f
        }
        return lowpass(b, 2500f)
    }

    // ------------------------------------------------------------------ voices

    /** Additive "voice" with a vowel formant (f1/f2), pitch glide and vibrato. */
    fun vowel(
        f0From: Float, f0To: Float, len: Float, f1: Float, f2: Float,
        vibrato: Float = 0f, breath: Float = 0.05f, seed: Int = 1, attack: Float = 0.02f,
    ): FloatArray {
        val b = buffer(len)
        val r = Random(seed)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val k = t / len
            val f0 = (f0From + (f0To - f0From) * k) * (1f + vibrato * sin(TAU * 6 * t).toFloat())
            ph += TAU * f0 / RATE
            var s = 0f
            var h = 1
            while (h * f0 < 4500f && h < 30) {
                val fh = h * f0
                val w = gauss(fh, f1, 120f) + 0.6f * gauss(fh, f2, 180f) + 0.05f
                s += (sin(ph * h).toFloat() * w) / h.toFloat().coerceAtMost(3f)
                h++
            }
            val env = (t / attack).coerceAtMost(1f) * ((len - t) / 0.06f).coerceIn(0f, 1f)
            b[i] = (s + (r.nextFloat() * 2 - 1) * breath) * env
        }
        return b
    }

    private fun gauss(x: Float, mu: Float, sigma: Float): Float {
        val d = (x - mu) / sigma
        return exp(-0.5f * d * d)
    }

    fun growl(baseHz: Float, len: Float, roughness: Float = 25f, bright: Float = 900f, seed: Int = 4): FloatArray {
        val b = buffer(len)
        val r = Random(seed)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toFloat() / RATE
            val k = t / len
            val swell = sin(PI * k).toFloat().pow(0.6f)
            val f = baseHz * (1f + 0.25f * sin(PI * k).toFloat())
            ph += TAU * f / RATE
            val saw = 2f * ((ph / TAU).toFloat() % 1f) - 1f
            val am = 0.6f + 0.4f * sin(TAU * roughness * t).toFloat()
            b[i] = (saw * 0.7f + (r.nextFloat() * 2 - 1) * 0.45f) * am * swell
        }
        return lowpass(lowpass(b, bright), bright * 1.4f)
    }

    fun concat(vararg parts: FloatArray, gap: Float = 0.04f): FloatArray {
        val g = (gap * RATE).toInt()
        val out = FloatArray(parts.sumOf { it.size } + g * (parts.size - 1))
        var at = 0
        for (p in parts) {
            mix(out, p, at)
            at += p.size + g
        }
        return out
    }
}
