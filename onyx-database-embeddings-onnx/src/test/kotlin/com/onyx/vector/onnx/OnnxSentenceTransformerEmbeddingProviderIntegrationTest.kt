package com.onyx.vector.onnx

import com.onyx.exception.SearchEmbeddingUnavailableException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnnxSentenceTransformerEmbeddingProviderIntegrationTest {

    @Test
    fun `external MiniLM checkpoint matches OnyxGemma embedding behavior`() {
        val modelDirectory = (
            System.getenv("ONYX_TEST_SENTENCE_TRANSFORMER_DOWNLOAD_DIRECTORY")
                ?: System.getenv("ONYX_TEST_SENTENCE_TRANSFORMER_MODEL")
            )
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: return
        val provider = OnnxSentenceTransformerEmbeddingProvider.allMiniLmL6V2(modelDirectory)
        try {
            val hello = provider.embed("hello world", String::class.java).vector
            assertEquals(384, provider.embeddingDimension)
            assertEquals(256, provider.maxSequenceLength)
            assertEquals(384, hello.size)
            assertEquals(-890722426721187298L, provider.calibrationId)
            assertTrue(abs(l2Norm(hello) - 1.0) < 0.00001)

            val expectedPrefix = floatArrayOf(
                -0.034477297f,
                0.031023208f,
                0.006734961f,
                0.026109034f,
                -0.039362021f,
                -0.160302505f,
                0.066924028f,
                -0.006441541f,
            )
            expectedPrefix.indices.forEach { index ->
                assertTrue(
                    abs(hello[index] - expectedPrefix[index]) < 0.0001f,
                    "embedding[$index] was ${hello[index]}, expected ${expectedPrefix[index]}",
                )
            }

            val maximumContent = List(254) { "horse" }.joinToString(" ")
            val truncatedContent = List(300) { "horse" }.joinToString(" ")
            assertContentEquals(
                provider.embed(maximumContent, Any::class.java).vector,
                provider.embed(truncatedContent, Any::class.java).vector,
                "The SentenceTransformer 256-token limit was not applied",
            )

            val executor = Executors.newFixedThreadPool(4)
            try {
                val calls = List(8) {
                    Callable { provider.embed("the quick brown fox", Any::class.java).vector }
                }
                val results = executor.invokeAll(calls).map { it.get() }
                results.drop(1).forEach { assertContentEquals(results.first(), it) }
            } finally {
                executor.shutdownNow()
            }
        } finally {
            provider.close()
        }

        assertFailsWith<SearchEmbeddingUnavailableException> {
            provider.embed("closed", Any::class.java)
        }
        provider.close()
    }

    private fun l2Norm(vector: FloatArray): Double =
        sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
}
