import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.impl.DenseLayer
import com.onyxdevtools.ai.compute.DefaultComputeContext
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
        val input = Tensor.from(arrayOf(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 4.0f)
        ))
        
        val output = layer.forward(input, isTraining = false, nextLayer = null)
        
        // Verify output dimensions
        assertEquals(2, output.size)
        assertEquals(3, output[0].size)
        
        // All values should be non-negative due to ReLU activation
        for (i in output.indices) {
            for (j in output[i].indices) {
                assertTrue("ReLU output should be non-negative", output[i][j] >= 0.0f)
            }
        }
    }
    
    @Test
    fun testDenseLayerDefaultConstructor() {
        // Test with default constructor (should work with lazy initialization)
        val layer = DenseLayer(2, 3, Activation.TANH, 0.1f)
        
        val input = Tensor.from(arrayOf(
            floatArrayOf(0.5f, -0.5f)
        ))
        
        val output = layer.forward(input, isTraining = true, nextLayer = null)
        
        // Verify output dimensions
        assertEquals(1, output.size)
        assertEquals(3, output[0].size)
        
        // Tanh outputs should be between -1 and 1
        for (i in output.indices) {
            for (j in output[i].indices) {
                assertTrue("Tanh output should be between -1 and 1", 
                    output[i][j] >= -1.0f && output[i][j] <= 1.0f)
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
