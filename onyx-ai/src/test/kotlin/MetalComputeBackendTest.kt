import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.compute.MetalComputeBackend
import com.onyxdevtools.ai.compute.ComputeBackendFactory
import com.onyxdevtools.ai.compute.ComputeBackendType
import org.junit.Assert.*
import kotlin.test.Test

class MetalComputeBackendTest {

    @Test
    fun `test Metal availability check`() {
        val isAvailable = try {
            MetalComputeBackend.isMetalAvailable()
        } catch (e: UnsatisfiedLinkError) {
            false // Native library not available
        }
        
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            // On macOS, Metal should be available (unless native lib missing)
            println("Metal availability on macOS: $isAvailable")
        } else {
            // On other platforms, Metal should not be available
            assertFalse("Metal should not be available on non-macOS platforms", isAvailable)
        }
    }

    @Test
    fun `test Metal device info`() {
        try {
            val deviceInfo = MetalComputeBackend.getMetalDeviceInfo()
            assertNotNull(deviceInfo)
            assertTrue(deviceInfo.isNotEmpty())
            println("Metal device info: $deviceInfo")
        } catch (e: UnsatisfiedLinkError) {
            // Expected if native library is not available
            println("Metal native library not available for device info test")
        }
    }

    @Test
    fun `test Metal backend creation`() {
        try {
            val backend = ComputeBackendFactory.createMetal()
            assertNotNull(backend)
            
            // If Metal fails gracefully, factory may return CPU backend instead
            assertTrue(
                "Backend should be METAL or CPU (fallback)", 
                backend.backendType == ComputeBackendType.METAL || backend.backendType == ComputeBackendType.CPU
            )
            
            // Cleanup
            if (backend is MetalComputeBackend) {
                backend.dispose()
            }
        } catch (e: RuntimeException) {
            // Expected if Metal is not available at all
            assertTrue(e.message?.contains("Metal") == true)
        }
    }

    @Test
    fun `test Metal matrix multiplication`() {
        try {
            val backend = MetalComputeBackend()
            
            // Test small matrices (should fall back to CPU)
            val a = arrayOf(
                floatArrayOf(1.0f, 2.0f),
                floatArrayOf(3.0f, 4.0f)
            )
            val b = arrayOf(
                floatArrayOf(5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f)
            )
            
            val result = backend.matrixMultiply(a, b)
            
            assertEquals(2, result.size)
            assertEquals(2, result[0].size)
            assertEquals(19.0f, result[0][0], 0.001f) // 1*5 + 2*7 = 19
            assertEquals(22.0f, result[0][1], 0.001f) // 1*6 + 2*8 = 22
            assertEquals(43.0f, result[1][0], 0.001f) // 3*5 + 4*7 = 43
            assertEquals(50.0f, result[1][1], 0.001f) // 3*6 + 4*8 = 50
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal large matrix multiplication`() {
        try {
            val backend = MetalComputeBackend()
            
            // Create larger matrices that should use GPU
            val size = 64
            val a = Array(size) { i ->
                FloatArray(size) { j -> (i + j).toFloat() }
            }
            val b = Array(size) { i ->
                FloatArray(size) { j -> (i * j + 1).toFloat() }
            }
            
            val result = backend.matrixMultiply(a, b)
            
            assertEquals(size, result.size)
            assertEquals(size, result[0].size)
            
            // Verify a few elements
            assertTrue(result[0][0] > 0)
            assertTrue(result[size-1][size-1] > 0)
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal matrix addition`() {
        try {
            val backend = MetalComputeBackend()
            
            val a = arrayOf(
                floatArrayOf(1.0f, 2.0f),
                floatArrayOf(3.0f, 4.0f)
            )
            val b = arrayOf(
                floatArrayOf(5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f)
            )
            
            val result = backend.add(a, b)
            
            assertEquals(2, result.size)
            assertEquals(2, result[0].size)
            assertEquals(6.0f, result[0][0], 0.001f) // 1 + 5 = 6
            assertEquals(8.0f, result[0][1], 0.001f) // 2 + 6 = 8
            assertEquals(10.0f, result[1][0], 0.001f) // 3 + 7 = 10
            assertEquals(12.0f, result[1][1], 0.001f) // 4 + 8 = 12
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal matrix subtraction`() {
        try {
            val backend = MetalComputeBackend()
            
            val a = arrayOf(
                floatArrayOf(5.0f, 6.0f),
                floatArrayOf(7.0f, 8.0f)
            )
            val b = arrayOf(
                floatArrayOf(1.0f, 2.0f),
                floatArrayOf(3.0f, 4.0f)
            )
            
            val result = backend.subtract(a, b)
            
            assertEquals(2, result.size)
            assertEquals(2, result[0].size)
            assertEquals(4.0f, result[0][0], 0.001f) // 5 - 1 = 4
            assertEquals(4.0f, result[0][1], 0.001f) // 6 - 2 = 4
            assertEquals(4.0f, result[1][0], 0.001f) // 7 - 3 = 4
            assertEquals(4.0f, result[1][1], 0.001f) // 8 - 4 = 4
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal element-wise multiplication`() {
        try {
            val backend = MetalComputeBackend()
            
            val a = arrayOf(
                floatArrayOf(2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f)
            )
            val b = arrayOf(
                floatArrayOf(6.0f, 7.0f),
                floatArrayOf(8.0f, 9.0f)
            )
            
            val result = backend.elementWiseMultiply(a, b)
            
            assertEquals(2, result.size)
            assertEquals(2, result[0].size)
            assertEquals(12.0f, result[0][0], 0.001f) // 2 * 6 = 12
            assertEquals(21.0f, result[0][1], 0.001f) // 3 * 7 = 21
            assertEquals(32.0f, result[1][0], 0.001f) // 4 * 8 = 32
            assertEquals(45.0f, result[1][1], 0.001f) // 5 * 9 = 45
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal matrix transpose`() {
        try {
            val backend = MetalComputeBackend()
            
            val matrix = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f)
            )
            
            val result = backend.transpose(matrix)
            
            assertEquals(3, result.size)
            assertEquals(2, result[0].size)
            assertEquals(1.0f, result[0][0], 0.001f)
            assertEquals(4.0f, result[0][1], 0.001f)
            assertEquals(2.0f, result[1][0], 0.001f)
            assertEquals(5.0f, result[1][1], 0.001f)
            assertEquals(3.0f, result[2][0], 0.001f)
            assertEquals(6.0f, result[2][1], 0.001f)
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal softmax`() {
        try {
            val backend = MetalComputeBackend()
            
            val matrix = arrayOf(
                floatArrayOf(1.0f, 2.0f, 3.0f),
                floatArrayOf(4.0f, 5.0f, 6.0f)
            )
            
            val result = backend.softmax(matrix)
            
            assertEquals(2, result.size)
            assertEquals(3, result[0].size)
            
            // Check that each row sums to approximately 1.0
            val row0Sum = result[0][0] + result[0][1] + result[0][2]
            val row1Sum = result[1][0] + result[1][1] + result[1][2]
            
            assertEquals(1.0f, row0Sum, 0.001f)
            assertEquals(1.0f, row1Sum, 0.001f)
            
            // Check that values are positive and in ascending order within each row
            assertTrue(result[0][0] > 0)
            assertTrue(result[0][1] > result[0][0])
            assertTrue(result[0][2] > result[0][1])
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }

    @Test
    fun `test Metal backend factory selection`() {
        val availableBackends = ComputeBackendFactory.getAvailableBackends()
        println("Available backends: $availableBackends")
        
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("mac")) {
            try {
                if (MetalComputeBackend.isMetalAvailable()) {
                    assertTrue(availableBackends.contains(ComputeBackendType.METAL))
                    
                    val bestBackend = ComputeBackendFactory.createBest()
                    // Factory may return CPU if Metal initialization fails
                    assertTrue(
                        "Best backend should be METAL or CPU (fallback)",
                        bestBackend.backendType == ComputeBackendType.METAL || bestBackend.backendType == ComputeBackendType.CPU
                    )
                    
                    if (bestBackend is MetalComputeBackend) {
                        bestBackend.dispose()
                    }
                }
            } catch (e: UnsatisfiedLinkError) {
                // Native library not available, Metal won't be in available backends
                println("Metal native library not available: ${e.message}")
            }
        } else {
            // On non-macOS platforms, Metal should not be available
            assertTrue(!availableBackends.contains(ComputeBackendType.METAL))
        }
    }

    @Test
    fun `test Metal CPU fallback behavior`() {
        try {
            val backend = MetalComputeBackend()
            
            // Test operations that should work even if GPU operations fail
            val scalar = 2.5f
            val matrix = arrayOf(
                floatArrayOf(1.0f, 2.0f),
                floatArrayOf(3.0f, 4.0f)
            )
            
            val result = backend.scalarMultiply(matrix, scalar)
            
            assertEquals(2, result.size)
            assertEquals(2, result[0].size)
            assertEquals(2.5f, result[0][0], 0.001f)
            assertEquals(5.0f, result[0][1], 0.001f)
            assertEquals(7.5f, result[1][0], 0.001f)
            assertEquals(10.0f, result[1][1], 0.001f)
            
            backend.dispose()
        } catch (e: RuntimeException) {
            // Expected if Metal is not available
            assertTrue(e.message?.contains("Metal") == true || e.message?.contains("native") == true)
        }
    }
}
