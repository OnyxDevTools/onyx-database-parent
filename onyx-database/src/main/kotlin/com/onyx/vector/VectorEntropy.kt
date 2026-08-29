package com.onyx.vector

/**
 * Scales an entity's requested entropy to the supported fingerprint size.
 *
 * Entropy is stored in whole 64-bit words. Requests are rounded up to
 * 64, 128, 192, or 256 bits and capped at 256 bits.
 */
data class VectorEntropy @JvmOverloads constructor(val entropy: Int = DEFAULT_ENTROPY) {

    /** Number of usable fingerprint bits after word rounding and bounds. */
    val bitCount: Int

    /** Number of 64-bit words in a fingerprint. */
    val wordCount: Int

    init {
        require(entropy > 0) { "entropy must be greater than zero" }
        bitCount = roundAndBoundBits(entropy)
        wordCount = bitCount / BITS_PER_WORD
    }

    companion object {
        const val DEFAULT_ENTROPY: Int = 128
        const val BITS_PER_WORD: Int = 64
        const val MIN_BITS: Int = 64
        const val MAX_BITS: Int = 256

        private fun roundAndBoundBits(requestedBits: Int): Int = when {
            requestedBits <= MIN_BITS -> MIN_BITS
            requestedBits <= 128 -> 128
            requestedBits <= 192 -> 192
            else -> MAX_BITS
        }
    }
}
