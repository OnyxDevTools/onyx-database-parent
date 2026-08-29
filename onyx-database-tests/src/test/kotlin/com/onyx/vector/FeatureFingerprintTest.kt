package com.onyx.vector

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FeatureFingerprintTest {

    @Test
    fun fingerprintDefensivelyCopiesConstructionAndAccess() {
        val source = longArrayOf(1L, 2L, 3L)
        val fingerprint = FeatureFingerprint(source)
        val originalRouteKey = fingerprint.routeKey

        source[0] = 99L
        val exposed = fingerprint.words
        exposed[1] = 88L
        val converted = fingerprint.toLongArray()
        converted[2] = 77L

        assertContentEquals(longArrayOf(1L, 2L, 3L), fingerprint.words)
        assertEquals(1L, fingerprint.wordAt(0))
        assertEquals(1L, fingerprint[0])
        assertEquals(originalRouteKey, fingerprint.routeKey)
    }

    @Test
    fun equalityAndHashCodeUseWordContent() {
        val first = FeatureFingerprint(longArrayOf(7L, 11L, 13L))
        val equal = FeatureFingerprint(longArrayOf(7L, 11L, 13L))
        val different = FeatureFingerprint(longArrayOf(7L, 11L, 17L))

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertEquals(first.routeKey, equal.routeKey)
        assertNotEquals(first, different)
        assertNotEquals(first.routeKey, different.routeKey)
    }

    @Test
    fun routeKeyIncludesWordsBeyondTheFirst() {
        val baseline = FeatureFingerprint(longArrayOf(42L, 1L, 2L, 3L))

        for (index in 1 until baseline.wordCount) {
            val changed = baseline.words
            changed[index] = changed[index] xor Long.MIN_VALUE
            assertNotEquals(baseline.routeKey, FeatureFingerprint(changed).routeKey, "word index $index")
        }
    }

    @Test
    fun hasherIsDeterministicAndUsesIndependentWords() {
        val entropy = VectorEntropy(256)
        val first = VectorFeatureHasher.fingerprint("price:interval:5:9", entropy)
        val second = VectorFeatureHasher.fingerprint("price:interval:5:9", entropy)
        val different = VectorFeatureHasher.fingerprint("price:interval:5:a", entropy)

        assertEquals(first, second)
        assertEquals(entropy.wordCount, first.wordCount)
        assertEquals(first.wordCount, first.words.toSet().size)
        assertNotEquals(first, different)
    }

    @Test
    fun longerEntropyExtendsTheSameDeterministicWordSequence() {
        val oneWord = VectorFeatureHasher.fingerprint(
            "symbol:QQQ",
            VectorEntropy(64),
        )
        val fourWords = VectorFeatureHasher.fingerprint(
            "symbol:QQQ",
            VectorEntropy(256),
        )

        assertEquals(oneWord[0], fourWords[0])
        assertContentEquals(oneWord.words, fourWords.words.copyOf(oneWord.wordCount))
    }

    @Test
    fun fingerprintFormatHasAStableGoldenVector() {
        val fingerprint = VectorFeatureHasher.fingerprint(
            "symbol:QQQ",
            VectorEntropy(256),
        )

        assertContentEquals(
            longArrayOf(
                unsignedLong("b0510d590b542d9e"),
                unsignedLong("e501c95f1cd3d5fb"),
                unsignedLong("033af16de59324a6"),
                unsignedLong("751d9ff5ddb3f497"),
            ),
            fingerprint.words,
        )
        assertEquals(unsignedLong("7f4a0f2f0f10964d"), fingerprint.routeKey)
    }

    @Test
    fun utf8FeaturesAreStableAndDistinct() {
        val entropy = VectorEntropy(128)

        val first = VectorFeatureHasher.fingerprint("café/🚀", entropy)
        val second = VectorFeatureHasher.fingerprint("café/🚀", entropy)
        val decomposed = VectorFeatureHasher.fingerprint("cafe\u0301/🚀", entropy)

        assertEquals(first, second)
        assertNotEquals(first, decomposed)
    }

    private fun unsignedLong(hex: String): Long = java.lang.Long.parseUnsignedLong(hex, 16)
}
