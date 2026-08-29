package com.onyx.vector

import java.security.MessageDigest

/** Deterministically hashes logical vector features into fingerprint words. */
object VectorFeatureHasher {

    private val FEATURE_DOMAIN: ByteArray = "com.onyx.vector.logical-feature".toByteArray(Charsets.UTF_8)
    private const val FEATURE_VERSION: Int = 1

    /**
     * Creates a deterministic fingerprint for [logicalFeature].
     *
     * Each 64-bit word comes from a separate SHA-256 invocation containing a
     * fixed domain, format version, and word ordinal. The feature is encoded as
     * length-delimited UTF-8, so neither platform encodings nor concatenation
     * ambiguities can affect the result.
     */
    fun fingerprint(logicalFeature: String, entropy: VectorEntropy): FeatureFingerprint {
        val featureBytes = logicalFeature.toByteArray(Charsets.UTF_8)
        val words = LongArray(entropy.wordCount)
        val digest = MessageDigest.getInstance("SHA-256")

        for (wordIndex in words.indices) {
            digest.reset()
            digest.update(FEATURE_DOMAIN)
            updateInt(digest, FEATURE_VERSION)
            updateInt(digest, wordIndex)
            updateInt(digest, featureBytes.size)
            digest.update(featureBytes)
            words[wordIndex] = readLong(digest.digest())
        }

        return FeatureFingerprint(words)
    }

    private fun updateInt(digest: MessageDigest, value: Int) {
        digest.update((value ushr 24).toByte())
        digest.update((value ushr 16).toByte())
        digest.update((value ushr 8).toByte())
        digest.update(value.toByte())
    }

    private fun readLong(bytes: ByteArray): Long {
        var value = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            value = (value shl 8) or (bytes[index].toLong() and 0xffL)
        }
        return value
    }
}
