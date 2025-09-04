import Activation
import dev.onyx.ai.Tensor
import dev.onyx.ai.layer.impl.CachedMultiHeadAttentionLayer
import dev.onyx.ai.compute.DefaultComputeContext
import org.junit.Assert.*
import kotlin.test.Test

/**
 * Test suite for the migrated CachedMultiHeadAttentionLayer with compute abstraction
 */
class CachedMultiHeadAttentionTest {

    /** Convenience: convert Array<FloatArray> -> Tensor */
    private fun t(a: Array<FloatArray>): Tensor = Tensor.from(a)

    @Test
    fun testCachedAttentionWithComputeBackend() {
        val computeContext = DefaultComputeContext()
        val layer = CachedMultiHeadAttentionLayer(
            tokensPerSample = 4,
            modelSize = 8,
            headCount = 2,
            computeContext = computeContext
        )

        // Input as Tensor
        val inputArr = arrayOf(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f),
            floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f),
            floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
            floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f)
        )
        val input = t(inputArr)

        val output = layer.forward(input, isTraining = false, nextLayer = null)

        // Verify output dimensions
        assertEquals(4, output.size)          // rows
        assertEquals(8, output[0].size)       // columns

        // Output should not contain NaN or infinite values
        for (i in output.indices) {
            for (j in output.colIndices) {
                val v = output[i, j]
                assertFalse("Output should not contain NaN", v.isNaN())
                assertFalse("Output should not contain infinite values", v.isInfinite())
            }
        }
    }

    @Test
    fun testCachedAttentionWithKVCache() {
        val layer = CachedMultiHeadAttentionLayer(
            tokensPerSample = 2,
            modelSize = 4,
            headCount = 2
        )

        // Initialize cache for inference
        layer.initializeCache(maxSequenceLength = 4, batchSize = 1)

        // First token
        val firstToken = t(arrayOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)))

        val output1 = layer.forward(firstToken, isTraining = false, nextLayer = null)
        assertEquals(1, output1.size)
        assertEquals(4, output1[0].size)

        // Second token (should use cache)
        val secondToken = t(arrayOf(floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f)))

        val output2 = layer.forward(secondToken, isTraining = false, nextLayer = null)
        assertEquals(1, output2.size)
        assertEquals(4, output2[0].size)

        // Verify no NaN or infinite values in cached computation
        for (i in output2.indices) {
            for (j in output2.colIndices) {
                val v = output2[i, j]
                assertFalse("Cached output should not contain NaN", v.isNaN())
                assertFalse("Cached output should not contain infinite values", v.isInfinite())
            }
        }
    }

    @Test
    fun testCacheOperations() {
        val layer = CachedMultiHeadAttentionLayer(
            tokensPerSample = 2,
            modelSize = 4,
            headCount = 1
        )

        // Test cache initialization
        layer.initializeCache(maxSequenceLength = 3, batchSize = 1)

        // Process some tokens
        val token1 = t(arrayOf(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)))
        layer.forward(token1, isTraining = false, nextLayer = null)

        // Clear cache
        layer.clearCache()

        // Should work after clearing
        val token2 = t(arrayOf(floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f)))
        val output = layer.forward(token2, isTraining = false, nextLayer = null)

        assertNotNull(output)
        assertEquals(1, output.size)
        assertEquals(4, output[0].size)

        // Disable cache
        layer.disableCache()

        // Should still work without cache
        val output2 = layer.forward(token2, isTraining = false, nextLayer = null)
        assertNotNull(output2)
    }

    @Test
    fun testLayerCloning() {
        val layer = CachedMultiHeadAttentionLayer(
            tokensPerSample = 2,
            modelSize = 4,
            headCount = 2
        )

        // This should not throw an exception
        val cloned = layer.clone() as CachedMultiHeadAttentionLayer

        assertNotNull(cloned)
        assertEquals(layer.activation, cloned.activation)
    }
}
