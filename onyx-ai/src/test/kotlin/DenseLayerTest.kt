import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.compute.*
import Activation
import com.onyxdevtools.ai.layer.impl.DenseLayer
import org.junit.Assert.*
import kotlin.test.Test

/**
 * Test suite for the migrated DenseLayer with compute abstraction
 */
class DenseLayerTest {
    
    @Test
    fun testDenseLayerWithComputeBackend() {
        val computeContext = DefaultComputeContext()
        val layer = DenseLayer(2, 3, Activation.RELU, 0.0f, computeContext)
        
        // Test basic forward pass
        val input = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 1.0f
                r == 0 && c == 1 -> 2.0f
                r == 1 && c == 0 -> 3.0f
                r == 1 && c == 1 -> 4.0f
                else -> 0.0f
            }
        }
        
        val output = layer.forward(input, isTraining = false, nextLayer = null)
        
        // Verify output dimensions
        assertEquals(2, output.rows)
        assertEquals(3, output.cols)
        
        // All values should be non-negative due to ReLU activation
        for (i in 0 until output.rows) {
            for (j in 0 until output.cols) {
                assertTrue("ReLU output should be non-negative", output[i, j] >= 0.0f)
            }
        }
    }
    
    @Test
    fun testDenseLayerDefaultConstructor() {
        // Test with default constructor (should work with lazy initialization)
        val layer = DenseLayer(2, 3, Activation.TANH, 0.1f)
        
        val input = createTensor(1, 2) { r: Int, c: Int ->
            when (c) {
                0 -> 0.5f
                1 -> -0.5f
                else -> 0.0f
            }
        }
        
        val output = layer.forward(input, isTraining = true, nextLayer = null)
        
        // Verify output dimensions
        assertEquals(1, output.rows)
        assertEquals(3, output.cols)
        
        // Tanh outputs should be between -1 and 1
        for (i in 0 until output.rows) {
            for (j in 0 until output.cols) {
                assertTrue("Tanh output should be between -1 and 1", 
                    output[i, j] >= -1.0f && output[i, j] <= 1.0f)
            }
        }
    }
    
    @Test
    fun testLayerSerialization() {
        val layer = DenseLayer(2, 2, Activation.LINEAR, 0.0f)
        
        // This should not throw an exception
        val cloned = layer.clone()
        
        assertNotNull(cloned)
        assertEquals(layer.activation, cloned.activation)
    }
}
