import Activation
import com.onyxdevtools.ai.layer.impl.CachedMultiHeadAttentionLayer
import com.onyxdevtools.ai.compute.DefaultComputeContext
import com.onyxdevtools.ai.toTensor
import org.junit.Assert.*
import kotlin.test.Test

/**
 * Test suite for the migrated CachedMultiHeadAttentionLayer with compute abstraction
 */
class CachedMultiHeadAttentionTest {
    
    @Test
    fun testCachedAttentionWithComputeBackend() {
        val layer = CachedMultiHeadAttentionLayer(
            tokensPerSample = 4,
            modelSize = 8,
            headCount = 2,
        )
        
        // Test basic forward pass without cache
        val input = arrayOf(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f),
            floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f),
            floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f),
            floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f)
        )
        
        val output = layer.forward(input.toTensor(), isTraining = false, nextLayer = null)
        
        // Verify output dimensions
        assertEquals(4, output.size)
        assertEquals(8, output[0].size)
        
        // Output should not contain NaN or infinite values
        for (i in output.indices) {
            for (j in output[i].indices) {
                assertFalse("Output should not contain NaN", output[i][j].isNaN())
                assertFalse("Output should not contain infinite values", output[i][j].isInfinite())
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
        val firstToken = arrayOf(
            floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        )
        
        val output1 = layer.forward(firstToken.toTensor(), isTraining = false, nextLayer = null)
        assertEquals(1, output1.size)
        assertEquals(4, output1[0].size)
        
        // Second token (should use cache)
        val secondToken = arrayOf(
            floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f)
        )
        
        val output2 = layer.forward(secondToken.toTensor(), isTraining = false, nextLayer = null)
        assertEquals(1, output2.size)
        assertEquals(4, output2[0].size)
        
        // Verify no NaN or infinite values in cached computation
        for (i in output2.indices) {
            for (j in output2[i].indices) {
                assertFalse("Cached output should not contain NaN", output2[i][j].isNaN())
                assertFalse("Cached output should not contain infinite values", output2[i][j].isInfinite())
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
        val token1 = arrayOf(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f))
        layer.forward(token1.toTensor(), isTraining = false, nextLayer = null)
        
        // Clear cache
        layer.clearCache()
        
        // Should work after clearing
        val token2 = arrayOf(floatArrayOf(0.5f, 1.5f, 2.5f, 3.5f))
        val output = layer.forward(token2.toTensor(), isTraining = false, nextLayer = null)
        
        assertNotNull(output)
        assertEquals(1, output.size)
        assertEquals(4, output[0].size)
        
        // Disable cache
        layer.disableCache()
        
        // Should still work without cache
        val output2 = layer.forward(token2.toTensor(), isTraining = false, nextLayer = null)
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
