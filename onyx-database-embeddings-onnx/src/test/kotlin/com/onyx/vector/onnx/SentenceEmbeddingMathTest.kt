package com.onyx.vector.onnx

import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SentenceEmbeddingMathTest {

    @Test
    fun `mean pooling excludes masked tokens and normalizes the result`() {
        val result = SentenceEmbeddingMath.meanPoolAndNormalize(
            hiddenState = floatArrayOf(
                3f, 4f,
                6f, 8f,
                100f, 100f,
            ),
            sequenceLength = 3,
            embeddingDimension = 2,
            attentionMask = longArrayOf(1, 1, 0),
        )

        assertTrue(abs(result[0] - 0.6f) < 0.000001f)
        assertTrue(abs(result[1] - 0.8f) < 0.000001f)
    }

    @Test
    fun `pooling rejects an empty attention mask`() {
        assertFailsWith<IllegalArgumentException> {
            SentenceEmbeddingMath.meanPoolAndNormalize(
                hiddenState = floatArrayOf(1f, 2f),
                sequenceLength = 1,
                embeddingDimension = 2,
                attentionMask = longArrayOf(0),
            )
        }
    }

    @Test
    fun `calibration is content based stable and nonzero`() {
        val firstDirectory = Files.createTempDirectory("onyx-embedding-calibration-a")
        val secondDirectory = Files.createTempDirectory("onyx-embedding-calibration-b")
        try {
            val first = firstDirectory.resolve("artifact")
            val second = secondDirectory.resolve("artifact")
            Files.writeString(first, "same model bytes")
            Files.writeString(second, "same model bytes")

            val firstId = calibrationIdForArtifacts(listOf("model" to first))
            val secondId = calibrationIdForArtifacts(listOf("model" to second))
            assertEquals(firstId, secondId)
            assertNotEquals(0L, firstId)

            Files.writeString(second, "changed model bytes")
            assertNotEquals(firstId, calibrationIdForArtifacts(listOf("model" to second)))
        } finally {
            firstDirectory.toFile().deleteRecursively()
            secondDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing model directory fails with its path`() {
        val missing = Files.createTempDirectory("onyx-missing-model").resolve("not-there")

        val failure = assertFailsWith<IllegalArgumentException> {
            OnnxSentenceTransformerEmbeddingProvider(missing)
        }

        assertTrue(failure.message.orEmpty().contains(missing.toAbsolutePath().toString()))
    }
}
