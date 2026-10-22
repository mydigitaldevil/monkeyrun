package com.junglerun.game.core

/**
 * Small deterministic PRNG (xorshift64*). Both players in a multiplayer race build their
 * world from the same seed, so obstacles and food appear at identical distances for everyone.
 */
class Rng(seed: Long) {
    private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    /** Uniform float in [0, 1). */
    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())

    fun range(min: Float, max: Float): Float = min + (max - min) * nextFloat()

    /** Uniform int in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 33) % bound).toInt()
    }

    fun chance(p: Float): Boolean = nextFloat() < p

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    /** Picks an index according to the given (non-negative) weights. */
    fun weighted(weights: FloatArray): Int {
        val total = weights.sum()
        var r = nextFloat() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r < 0f) return i
        }
        return weights.lastIndex
    }
}
