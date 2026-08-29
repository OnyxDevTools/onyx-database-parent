package com.onyx.vector

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorEntropyTest {

    @Test
    fun defaultEntropyUsesTwoWords() {
        val entropy = VectorEntropy()

        assertEquals(128, entropy.bitCount)
        assertEquals(2, entropy.wordCount)
        assertEquals(0, entropy.bitCount % Long.SIZE_BITS)
    }

    @Test
    fun entropyIsRoundedUpAndClampedToSupportedWords() {
        val expectations = mapOf(
            1 to 64,
            64 to 64,
            65 to 128,
            128 to 128,
            129 to 192,
            192 to 192,
            193 to 256,
            256 to 256,
            257 to 256,
            Int.MAX_VALUE to 256,
        )

        expectations.forEach { (requested, expected) ->
            val entropy = VectorEntropy(requested)
            assertEquals(expected, entropy.bitCount, "entropy=$requested")
            assertEquals(expected / Long.SIZE_BITS, entropy.wordCount, "entropy=$requested")
        }
    }

    @Test
    fun entropyRejectsNonPositiveValues() {
        listOf(0, -1, Int.MIN_VALUE).forEach { entropy ->
            assertFailsWith<IllegalArgumentException> {
                VectorEntropy(entropy)
            }
        }
    }
}
