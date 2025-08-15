import com.onyxdevtools.ai.compute.*
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import org.junit.Assert.*
import kotlin.test.Test

/**
 * Test suite for compute backend abstraction layer
 */
class ComputeBackendTest {
    
    @Test
    fun testCPUBackendBasicOperations() {
        val backend = CPUComputeBackend()
        
        // Test matrix multiplication
        val a = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 1.0f
                r == 0 && c == 1 -> 2.0f
                r == 1 && c == 0 -> 3.0f
                r == 1 && c == 1 -> 4.0f
                else -> 0.0f
            }
        }
        val b = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 5.0f
                r == 0 && c == 1 -> 6.0f
                r == 1 && c == 0 -> 7.0f
                r == 1 && c == 1 -> 8.0f
                else -> 0.0f
            }
        }
        
        val result = backend.matrixMultiply(a, b)
        assertEquals(19.0f, result[0, 0], 0.001f) // 1*5 + 2*7 = 19
        assertEquals(22.0f, result[0, 1], 0.001f) // 1*6 + 2*8 = 22
        assertEquals(43.0f, result[1, 0], 0.001f) // 3*5 + 4*7 = 43
        assertEquals(50.0f, result[1, 1], 0.001f) // 3*6 + 4*8 = 50
    }
    
    @Test
    fun testCPUBackendElementWiseOperations() {
        val backend = CPUComputeBackend()
        
        val a = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 1.0f
                r == 0 && c == 1 -> 2.0f
                r == 1 && c == 0 -> 3.0f
                r == 1 && c == 1 -> 4.0f
                else -> 0.0f
            }
        }
        val b = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 1.0f
                r == 0 && c == 1 -> 1.0f
                r == 1 && c == 0 -> 1.0f
                r == 1 && c == 1 -> 1.0f
                else -> 0.0f
            }
        }
        
        // Test addition
        val sum = backend.add(a, b)
        assertEquals(2.0f, sum[0, 0], 0.001f)
        assertEquals(3.0f, sum[0, 1], 0.001f)
        assertEquals(4.0f, sum[1, 0], 0.001f)
        assertEquals(5.0f, sum[1, 1], 0.001f)
        
        // Test scalar multiplication
        val scaled = backend.scalarMultiply(a, 2.0f)
        assertEquals(2.0f, scaled[0, 0], 0.001f)
        assertEquals(4.0f, scaled[0, 1], 0.001f)
        assertEquals(6.0f, scaled[1, 0], 0.001f)
        assertEquals(8.0f, scaled[1, 1], 0.001f)
        
        // Test transpose
        val transposed = backend.transpose(a)
        assertEquals(1.0f, transposed[0, 0], 0.001f)
        assertEquals(3.0f, transposed[0, 1], 0.001f)
        assertEquals(2.0f, transposed[1, 0], 0.001f)
        assertEquals(4.0f, transposed[1, 1], 0.001f)
    }
    
    @Test
    fun testBasicCPUBackend() {
        val backend = BasicCPUComputeBackend()
        
        // Test matrix multiplication
        val a = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 2.0f
                r == 0 && c == 1 -> 3.0f
                r == 1 && c == 0 -> 1.0f
                r == 1 && c == 1 -> 4.0f
                else -> 0.0f
            }
        }
        val b = createTensor(2, 2) { r: Int, c: Int ->
            when {
                r == 0 && c == 0 -> 1.0f
                r == 0 && c == 1 -> 2.0f
                r == 1 && c == 0 -> 3.0f
                r == 1 && c == 1 -> 1.0f
                else -> 0.0f
            }
        }
        
        val result = backend.matrixMultiply(a, b)
        assertEquals(11.0f, result[0, 0], 0.001f) // 2*1 + 3*3 = 11
        assertEquals(7.0f, result[0, 1], 0.001f)  // 2*2 + 3*1 = 7
        assertEquals(13.0f, result[1, 0], 0.001f) // 1*1 + 4*3 = 13
        assertEquals(6.0f, result[1, 1], 0.001f)  // 1*2 + 4*1 = 6
        
        // Test element-wise operations
        val sum = backend.add(a, b)
        assertEquals(3.0f, sum[0, 0], 0.001f) // 2+1
        assertEquals(5.0f, sum[0, 1], 0.001f) // 3+2
        assertEquals(4.0f, sum[1, 0], 0.001f) // 1+3
        assertEquals(5.0f, sum[1, 1], 0.001f) // 4+1
        
        // Test scalar operations
        val scaled = backend.scalarMultiply(a, 0.5f)
        assertEquals(1.0f, scaled[0, 0], 0.001f)
        assertEquals(1.5f, scaled[0, 1], 0.001f)
        assertEquals(0.5f, scaled[1, 0], 0.001f)
        assertEquals(2.0f, scaled[1, 1], 0.001f)
    }
    
    @Test
    fun testSoftmaxOperation() {
        val backend = CPUComputeBackend()
        
        val input = createTensor(1, 3) { r: Int, c: Int ->
            when (c) {
                0 -> 1.0f
                1 -> 2.0f
                2 -> 3.0f
                else -> 0.0f
            }
        }
        
        val result = backend.softmax(input)
        
        // Softmax should sum to approximately 1.0
        val sum = result[0, 0] + result[0, 1] + result[0, 2]
        assertEquals(1.0f, sum, 0.001f)
        
        // Values should be in ascending order (since input was ascending)
        assertTrue(result[0, 0] < result[0, 1])
        assertTrue(result[0, 1] < result[0, 2])
    }
}
