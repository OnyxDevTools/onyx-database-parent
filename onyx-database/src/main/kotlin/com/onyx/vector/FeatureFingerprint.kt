package com.onyx.vector

import java.security.MessageDigest

/**
 * An immutable fingerprint made of one or more 64-bit words.
 *
 * Both construction and access use defensive copies. Equality and hash codes
 * are therefore based on stable content rather than [LongArray] identity.
 */
class FeatureFingerprint(words: LongArray) {

    private val content: LongArray = words.copyOf()

    /** A stable routing key derived from every word in this fingerprint. */
    val routeKey: Long = deriveRouteKey(content)

    /** Number of 64-bit words in this fingerprint. */
    val wordCount: Int
        get() = content.size

    /** Returns a defensive copy of the fingerprint words. */
    val words: LongArray
        get() = content.copyOf()

    fun wordAt(index: Int): Long = content[index]

    operator fun get(index: Int): Long = wordAt(index)

    fun toLongArray(): LongArray = content.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || other is FeatureFingerprint && content.contentEquals(other.content)

    override fun hashCode(): Int = content.contentHashCode()

    override fun toString(): String = content.joinToString(
        prefix = "FeatureFingerprint(",
        postfix = ")",
    ) { java.lang.Long.toUnsignedString(it, 16).padStart(16, '0') }

    private companion object {
        val ROUTE_DOMAIN: ByteArray = "com.onyx.vector.route-key".toByteArray(Charsets.UTF_8)
        const val ROUTE_VERSION: Int = 1

        fun deriveRouteKey(words: LongArray): Long {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(ROUTE_DOMAIN)
            updateInt(digest, ROUTE_VERSION)
            updateInt(digest, words.size)
            words.forEach { updateLong(digest, it) }
            return readLong(digest.digest())
        }

        fun updateInt(digest: MessageDigest, value: Int) {
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }

        fun updateLong(digest: MessageDigest, value: Long) {
            digest.update((value ushr 56).toByte())
            digest.update((value ushr 48).toByte())
            digest.update((value ushr 40).toByte())
            digest.update((value ushr 32).toByte())
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }

        fun readLong(bytes: ByteArray): Long {
            var value = 0L
            for (index in 0 until Long.SIZE_BYTES) {
                value = (value shl 8) or (bytes[index].toLong() and 0xffL)
            }
            return value
        }
    }
}
