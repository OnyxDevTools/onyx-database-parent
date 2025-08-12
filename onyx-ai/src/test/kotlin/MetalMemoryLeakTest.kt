package com.onyxdevtools.ai.compute

import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Assume
import kotlin.test.Test

/**
 * Tests for GPU memory leaks in Metal compute backend.
 * This test specifically validates that Metal buffers created with newBufferWithLength:options: 
 * are properly released and not leaked when reference counting goes wrong.
 */
class MetalMemoryLeakTest {
    
    private var backend: MetalComputeBackend? = null
    
    @Before
    fun setup() {
        Assume.assumeTrue("Metal not available on this system", MetalComputeBackend.isMetalAvailable())
        backend = MetalComputeBackend()
    }
    
    @After
    fun teardown() {
        backend?.dispose()
        backend = null
    }
    
    @Test
    fun `test GPU memory leak with temporary buffer allocation and release`() {
        val backend = this.backend ?: return
        
        // Get initial GPU memory usage
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val initialMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        val maxMemory = MetalComputeBackend.getRecommendedMaxGPUMemory(backend.metalContext)
        
        println("Initial GPU memory: ${initialMemory / (1024 * 1024)} MB")
        println("Max GPU memory: ${maxMemory / (1024 * 1024)} MB")
        
        // Create and immediately release multiple temporary buffers
        // This simulates the problematic scenario mentioned in the task
        repeat(10) { iteration ->
            println("Iteration $iteration:")
            
            // Create a large temporary buffer (10MB)
            val bufferSize = 10 * 1024 * 1024 // 10MB
            val buffer = MetalComputeBackend.createGPUBuffer(backend.metalContext, bufferSize)
            assertTrue("Buffer creation should succeed", buffer != 0L)
            
            // Check memory increased
            MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
            val memoryAfterAlloc = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
            val allocated = memoryAfterAlloc - initialMemory
            println("  After allocation: ${memoryAfterAlloc / (1024 * 1024)} MB (+${allocated / (1024 * 1024)} MB)")
            
            // Release the buffer
            MetalComputeBackend.releaseGPUBuffer(backend.metalContext, buffer)
            
            // Force GPU to complete all operations and deallocations
            MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
            
            // Check memory returned to baseline (with some tolerance for Metal's internal management)
            val memoryAfterRelease = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
            val leaked = memoryAfterRelease - initialMemory
            println("  After release: ${memoryAfterRelease / (1024 * 1024)} MB (leak: ${leaked / (1024 * 1024)} MB)")
            
            // Allow some tolerance for Metal's internal memory management (e.g. caching)
            // But we shouldn't leak more than 1MB per iteration
            assertTrue("Memory leak detected: ${leaked / (1024 * 1024)} MB leaked on iteration $iteration", leaked < 1024 * 1024)
        }
        
        // Final memory check - should be close to initial
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val finalMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        val totalLeaked = finalMemory - initialMemory
        
        println("Final GPU memory: ${finalMemory / (1024 * 1024)} MB")
        println("Total leaked: ${totalLeaked / (1024 * 1024)} MB")
        
        // Final assertion - total leak should be minimal (less than 5MB for all iterations)
        assertTrue("Significant memory leak detected: ${totalLeaked / (1024 * 1024)} MB total leaked", totalLeaked < 5 * 1024 * 1024)
    }
    
    @Test
    fun `test GPU memory leak with matrix operations`() {
        val backend = this.backend ?: return
        
        // Get initial GPU memory usage
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val initialMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        
        println("Initial GPU memory: ${initialMemory / (1024 * 1024)} MB")
        
        // Perform multiple matrix operations that create temporary GPU buffers
        repeat(5) { iteration ->
            println("Matrix operation iteration $iteration:")
            
            // Create large matrices to force GPU usage
            val size = 500
            val matrixA = Array(size) { FloatArray(size) { (it * 0.01f) } }
            val matrixB = Array(size) { FloatArray(size) { (it * 0.02f) } }
            
            // Perform operations that create temporary GPU buffers
            val result1 = backend.matrixMultiply(matrixA, matrixB)
            val result2 = backend.add(result1, matrixA)
            val result3 = backend.transpose(result2)
            
            // Force GPU sync to ensure all buffers are released
            MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
            
            val currentMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
            val leaked = currentMemory - initialMemory
            println("  After operations: ${currentMemory / (1024 * 1024)} MB (leak: ${leaked / (1024 * 1024)} MB)")
            
            // Each iteration should not accumulate significant memory leaks
            assertTrue("Memory leak detected in matrix operations: ${leaked / (1024 * 1024)} MB leaked", leaked < 10 * 1024 * 1024)
            
            // Verify results are valid (non-null and have expected dimensions)
            assertNotNull("Matrix multiply result should not be null", result1)
            assertNotNull("Matrix add result should not be null", result2) 
            assertNotNull("Matrix transpose result should not be null", result3)
            assertEquals("Result1 should have correct number of rows", size, result1.size)
            assertEquals("Result1 should have correct number of columns", size, result1[0].size)
        }
        
        // Final memory check
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val finalMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        val totalLeaked = finalMemory - initialMemory
        
        println("Final GPU memory after matrix operations: ${finalMemory / (1024 * 1024)} MB")
        println("Total leaked: ${totalLeaked / (1024 * 1024)} MB")
        
        // Final assertion - should not have significant accumulated leaks
        assertTrue("Significant memory leak detected in matrix operations: ${totalLeaked / (1024 * 1024)} MB total leaked", totalLeaked < 20 * 1024 * 1024)
    }
    
    @Test
    fun `test double release scenario - the original bug`() {
        val backend = this.backend ?: return
        
        println("Testing the original double-release bug scenario...")
        
        // Get initial GPU memory
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val initialMemory = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        println("Initial GPU memory: ${initialMemory / (1024 * 1024)} MB")
        
        // Create a temporary buffer as mentioned in the bug report
        val bufferSize = 1024 * 1024 // 1MB buffer  
        val buffer = MetalComputeBackend.createGPUBuffer(backend.metalContext, bufferSize)
        assertTrue("Buffer creation should succeed", buffer != 0L)
        
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val memoryAfterCreate = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        println("After create: ${memoryAfterCreate / (1024 * 1024)} MB")
        
        // Release it once (this should work)
        MetalComputeBackend.releaseGPUBuffer(backend.metalContext, buffer)
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val memoryAfterFirstRelease = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        println("After first release: ${memoryAfterFirstRelease / (1024 * 1024)} MB")
        
        // Try to release it again (this was the problematic scenario)
        // Before the fix, this might not properly decrement reference count and could leak
        try {
            MetalComputeBackend.releaseGPUBuffer(backend.metalContext, buffer)
            println("Double release did not crash (good)")
        } catch (e: Exception) {
            fail("Double release should not throw exception: ${e.message}")
        }
        
        MetalComputeBackend.forceGPUMemorySync(backend.metalContext)
        val memoryAfterDoubleRelease = MetalComputeBackend.getCurrentGPUMemoryUsage(backend.metalContext)
        println("After double release: ${memoryAfterDoubleRelease / (1024 * 1024)} MB")
        
        // Memory should be back to baseline, proving the buffer was actually released
        val leaked = memoryAfterDoubleRelease - initialMemory
        println("Total leaked: ${leaked / (1024 * 1024)} MB")
        
        // The buffer should be properly released - allow some tolerance for Metal's internal management
        assertTrue("Buffer appears to have leaked: ${leaked / (1024 * 1024)} MB", leaked < 1024 * 1024)
    }
    
    companion object {
        // Helper to access the private metalContext for testing
        private val MetalComputeBackend.metalContext: Long
            get() = this::class.java.getDeclaredField("metalContext").apply { 
                isAccessible = true 
            }.getLong(this)
    }
}
