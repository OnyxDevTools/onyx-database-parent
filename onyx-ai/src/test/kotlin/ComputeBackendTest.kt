import com.onyxdevtools.ai.compute.*
import com.onyxdevtools.ai.Matrix
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
        val a = arrayOf(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 4.0f)
        )
        val b = arrayOf(
            floatArrayOf(5.0f, 6.0f),
            floatArrayOf(7.0f, 8.0f)
        )
        
        val result = backend.matrixMultiply(a, b)
        assertEquals(19.0f, result[0][0], 0.001f) // 1*5 + 2*7 = 19
        assertEquals(22.0f, result[0][1], 0.001f) // 1*6 + 2*8 = 22
        assertEquals(43.0f, result[1][0], 0.001f) // 3*5 + 4*7 = 43
        assertEquals(50.0f, result[1][1], 0.001f) // 3*6 + 4*8 = 50
    }
    
    @Test
    fun testCPUBackendElementWiseOperations() {
        val backend = CPUComputeBackend()
        
        val a = arrayOf(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 4.0f)
        )
        val b = arrayOf(
            floatArrayOf(1.0f, 1.0f),
            floatArrayOf(1.0f, 1.0f)
        )
        
        // Test addition
        val sum = backend.add(a, b)
        assertEquals(2.0f, sum[0][0], 0.001f)
        assertEquals(3.0f, sum[0][1], 0.001f)
        assertEquals(4.0f, sum[1][0], 0.001f)
        assertEquals(5.0f, sum[1][1], 0.001f)
        
        // Test scalar multiplication
        val scaled = backend.scalarMultiply(a, 2.0f)
        assertEquals(2.0f, scaled[0][0], 0.001f)
        assertEquals(4.0f, scaled[0][1], 0.001f)
        assertEquals(6.0f, scaled[1][0], 0.001f)
        assertEquals(8.0f, scaled[1][1], 0.001f)
        
        // Test transpose
        val transposed = backend.transpose(a)
        assertEquals(1.0f, transposed[0][0], 0.001f)
        assertEquals(3.0f, transposed[0][1], 0.001f)
        assertEquals(2.0f, transposed[1][0], 0.001f)
        assertEquals(4.0f, transposed[1][1], 0.001f)
    }
    
    @Test
    fun testComputeContextFactory() {
        val context = DefaultComputeContext()
        assertEquals(ComputeBackendType.CPU, context.backend.backendType)
        
        // Test matrix creation
        val matrix = context.createMatrix(2, 3, 1.5f)
        assertEquals(2, matrix.size)
        assertEquals(3, matrix[0].size)
        assertEquals(1.5f, matrix[0][0], 0.001f)
        assertEquals(1.5f, matrix[1][2], 0.001f)
    }
    
    @Test
    fun testBackendFactory() {
        val availableBackends = ComputeBackendFactory.getAvailableBackends()
        assertTrue(availableBackends.contains(ComputeBackendType.CPU))
        
        val bestBackend = ComputeBackendFactory.createBest()
        assertEquals(ComputeBackendType.CPU, bestBackend.backendType)
        
        val cpuBackend = ComputeBackendFactory.createCPU()
        assertEquals(ComputeBackendType.CPU, cpuBackend.backendType)
        
        // Test Android-compatible basic backend
        val basicBackend = ComputeBackendFactory.createBasicCPU()
        assertEquals(ComputeBackendType.CPU, basicBackend.backendType)
    }
    
    @Test
    fun testBasicCPUBackend() {
        val backend = BasicCPUComputeBackend()
        
        // Test matrix multiplication
        val a = arrayOf(
            floatArrayOf(2.0f, 3.0f),
            floatArrayOf(1.0f, 4.0f)
        )
        val b = arrayOf(
            floatArrayOf(1.0f, 2.0f),
            floatArrayOf(3.0f, 1.0f)
        )
        
        val result = backend.matrixMultiply(a, b)
        assertEquals(11.0f, result[0][0], 0.001f) // 2*1 + 3*3 = 11
        assertEquals(7.0f, result[0][1], 0.001f)  // 2*2 + 3*1 = 7
        assertEquals(13.0f, result[1][0], 0.001f) // 1*1 + 4*3 = 13
        assertEquals(6.0f, result[1][1], 0.001f)  // 1*2 + 4*1 = 6
        
        // Test element-wise operations
        val sum = backend.add(a, b)
        assertEquals(3.0f, sum[0][0], 0.001f) // 2+1
        assertEquals(5.0f, sum[0][1], 0.001f) // 3+2
        assertEquals(4.0f, sum[1][0], 0.001f) // 1+3
        assertEquals(5.0f, sum[1][1], 0.001f) // 4+1
        
        // Test scalar operations
        val scaled = backend.scalarMultiply(a, 0.5f)
        assertEquals(1.0f, scaled[0][0], 0.001f)
        assertEquals(1.5f, scaled[0][1], 0.001f)
        assertEquals(0.5f, scaled[1][0], 0.001f)
        assertEquals(2.0f, scaled[1][1], 0.001f)
    }
    
    @Test
    fun testSoftmaxOperation() {
        val backend = CPUComputeBackend()
        
        val input = arrayOf(
            floatArrayOf(1.0f, 2.0f, 3.0f)
        )
        
        val result = backend.softmax(input)
        
        // Softmax should sum to 1.0
        val sum = result[0].sum()
        assertEquals(1.0f, sum, 0.001f)
        
        // Values should be in ascending order (since input was ascending)
        assertTrue(result[0][0] < result[0][1])
        assertTrue(result[0][1] < result[0][2])
    }
    
    @Test
    fun testMemoryOperations() {
        val context = DefaultComputeContext()
        val matrix = context.createMatrix(100, 100)
        
        // Test copy operations (should be no-ops for CPU)
        val copied = context.copyToBackend(matrix)
        assertSame(matrix, copied) // Should be same reference for CPU backend
        
        val copiedBack = context.copyFromBackend(copied)
        assertSame(copied, copiedBack)
        
        // Test memory info
        val memInfo = context.getMemoryInfo()
        assertTrue(memInfo.totalMemory > 0)
        assertTrue(memInfo.availableMemory >= 0)
        assertTrue(memInfo.usedMemory >= 0)
        
        // Test disposal (should be no-op for CPU)
        context.releaseMatrix(matrix)
        context.dispose()
    }
}
