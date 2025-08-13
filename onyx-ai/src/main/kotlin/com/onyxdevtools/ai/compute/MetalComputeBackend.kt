package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix
import kotlin.math.*

/**
 * Metal compute backend that leverages Apple's Metal framework for GPU acceleration
 * on macOS and iOS platforms.
 * 
 * This backend provides:
 * - High-performance GPU matrix operations using Metal compute shaders
 * - Optimized memory transfers between CPU and GPU
 * - Support for Metal Performance Shaders (MPS) when available
 * - Automatic fallback to CPU for unsupported operations
 */
class MetalComputeBackend : CPUComputeBackend() {
    
    override val backendType: ComputeBackendType = ComputeBackendType.METAL
    
    // Native Metal interface handle
    internal var metalContext: Long = 0L
    
    // Memory management tracking - track all allocated buffers for cleanup
    private val gpuBuffers = mutableSetOf<Long>()
    private val bufferLock = Any()

    // Performance thresholds based on benchmark analysis  
    private val METAL_MIN_THRESHOLD = 50_000_000L        // Metal becomes competitive
    private val METAL_PREFERRED_THRESHOLD = 200_000_000L  // Metal clearly preferred
    private val MIN_GPU_DIMENSION = 128                   // Minimum dimension for GPU benefits
    private val SKINNY_RATIO_THRESHOLD = 8.0             // Aspect ratio threshold for skinny matrices
    private val GPU_OVERHEAD_FACTOR = 2.5                // Account for memory transfer overhead

    companion object {
        /**
         * Flag to track if the native library was loaded successfully
         */
        private var nativeLibraryLoaded = false
        
        /**
         * Load the native Metal library
         */
        init {
            try {
                System.loadLibrary("onyx-metal")
                nativeLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                nativeLibraryLoaded = false
                println("Metal native library not available: ${e.message}")
            }
        }
        
        /**
         * Check if Metal framework is available on this system
         */
        @JvmStatic
        fun isMetalAvailable(): Boolean {
            if (!nativeLibraryLoaded) return false
            return try {
                isMetalAvailableNative()
            } catch (e: Exception) {
                false
            }
        }
        
        @JvmStatic
        private external fun isMetalAvailableNative(): Boolean
        
        /**
         * Get Metal device information
         */
        @JvmStatic
        external fun getMetalDeviceInfo(): String
        
        /**
         * Get current GPU memory usage in bytes
         */
        @JvmStatic
        external fun getCurrentGPUMemoryUsage(contextHandle: Long): Long
        
        /**
         * Get recommended max GPU memory usage in bytes
         */
        @JvmStatic
        external fun getRecommendedMaxGPUMemory(contextHandle: Long): Long
        
        /**
         * Force GPU memory sync to ensure all operations are complete
         */
        @JvmStatic
        external fun forceGPUMemorySync(contextHandle: Long)
        
        /**
         * Initialize Metal compute context
         */
        @JvmStatic
        external fun initializeMetalContext(): Long
        
        /**
         * Dispose Metal compute context
         */
        @JvmStatic
        external fun disposeMetalContext(contextHandle: Long)
        
        /**
         * Create GPU buffer
         */
        @JvmStatic
        external fun createGPUBuffer(contextHandle: Long, size: Int): Long
        
        /**
         * Copy data from CPU to GPU buffer
         */
        @JvmStatic
        external fun copyToGPU(contextHandle: Long, bufferHandle: Long, data: FloatArray)
        
        /**
         * Copy data from GPU buffer to CPU
         */
        @JvmStatic
        external fun copyFromGPU(contextHandle: Long, bufferHandle: Long, size: Int): FloatArray
        
        /**
         * Release GPU buffer
         */
        @JvmStatic
        external fun releaseGPUBuffer(contextHandle: Long, bufferHandle: Long)
        
        /**
         * Perform matrix multiplication on GPU
         */
        @JvmStatic
        external fun gpuMatrixMultiply(
            contextHandle: Long,
            bufferA: Long, rowsA: Int, colsA: Int,
            bufferB: Long, rowsB: Int, colsB: Int,
            bufferResult: Long
        ): Boolean

        /**
         * Perform element-wise matrix operations on GPU
         */
        @JvmStatic
        external fun gpuElementWiseOperation(
            contextHandle: Long,
            bufferA: Long, bufferB: Long, bufferResult: Long,
            rows: Int, cols: Int, operation: Int // 0=add, 1=subtract, 2=multiply
        ): Boolean
        
        /**
         * Perform matrix transpose on GPU
         */
        @JvmStatic
        external fun gpuTranspose(
            contextHandle: Long,
            bufferInput: Long, bufferOutput: Long,
            rows: Int, cols: Int
        ): Boolean
        
        /**
         * Perform softmax on GPU
         */
        @JvmStatic
        external fun gpuSoftmax(
            contextHandle: Long,
            bufferInput: Long, bufferOutput: Long,
            rows: Int, cols: Int
        ): Boolean
    }
    
    init {
        if (!isMetalAvailable()) {
            throw RuntimeException("Metal framework is not available on this system")
        }
        
        try {
            println("Metal device: ${getMetalDeviceInfo()}")
            metalContext = initializeMetalContext()
            
            if (metalContext == 0L) {
                throw RuntimeException("Failed to initialize Metal compute context - shader compilation may have failed")
            }
            
            println("Metal compute backend initialized successfully")
        } catch (e: Exception) {
            dispose()
            throw RuntimeException("Failed to initialize Metal compute context: ${e.message}", e)
        }
    }
    
    override fun matrixMultiply(a: Matrix, b: Matrix): Matrix {
        require(a[0].size == b.size) { 
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}" 
        }
        
        val rowsA = a.size
        val colsA = a[0].size
        val rowsB = b.size
        val colsB = b[0].size

        val totalOperations = rowsA.toLong() * colsA.toLong() * colsB.toLong()

        // Intelligent decision logic for Metal vs. CPU
        if (!shouldUseMetalForMatrixMultiply(rowsA, colsA, colsB, totalOperations)) {
            return super.matrixMultiply(a, b)
        }
        
        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            // Create GPU buffers
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rowsA * colsB
            bufferResult = createTrackedBuffer(resultSize * 4) // 4 bytes per float
            
            // Perform GPU matrix multiplication
            val success = gpuMatrixMultiply(
                metalContext,
                bufferA, rowsA, colsA,
                bufferB, rowsB, colsB,
                bufferResult
            )
            
            if (!success) {
                // Fallback to CPU
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                return super.matrixMultiply(a, b)
            }
            
            // Copy result back from GPU
            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val result = Array(rowsA) { row ->
                FloatArray(colsB) { col ->
                    resultData[row * colsB + col]
                }
            }
            
            // Cleanup
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            
            result
        } catch (e: Exception) {
            println("Metal matrix multiplication failed, falling back to CPU: ${e.message}")
            // Ensure cleanup on exception
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            super.matrixMultiply(a, b)
        }
    }
    
    /**
     * Determine whether to use Metal or CPU for matrix multiplication based on 
     * matrix characteristics and performance benchmarks
     */
    private fun shouldUseMetalForMatrixMultiply(
        rowsA: Int, colsA: Int, colsB: Int, totalOps: Long
    ): Boolean {
        val maxDim = maxOf(rowsA, colsA, colsB)
        val minDim = minOf(rowsA, colsA, colsB)
        
        // Metal is generally not worth it for very small matrices due to overhead
        if (maxDim < MIN_GPU_DIMENSION || totalOps < METAL_MIN_THRESHOLD) {
            return false
        }
        
        // Calculate aspect ratio to detect skinny matrices
        val aspectRatio = maxDim.toDouble() / minDim.toDouble()
        val isSkinny = aspectRatio >= SKINNY_RATIO_THRESHOLD
        
        return when {
            // Large matrices: Metal almost always wins
            totalOps >= METAL_PREFERRED_THRESHOLD -> true
            
            // Medium matrices: consider shape and overhead
            totalOps >= METAL_MIN_THRESHOLD -> {
                if (isSkinny) {
                    // Skinny matrices often perform poorly on GPU due to low parallelism
                    // Only use Metal if operations count is high enough to offset overhead
                    totalOps >= METAL_MIN_THRESHOLD * GPU_OVERHEAD_FACTOR.toLong()
                } else {
                    // Square-ish matrices: Metal is generally good at medium sizes
                    true
                }
            }
            
            // Small matrices: stick with CPU
            else -> false
        }
    }
    
    // Helper methods

    private fun createMatrixBuffer(matrix: Matrix): Long {
        val size = matrix.size * matrix[0].size
        val buffer = createGPUBuffer(metalContext, size * 4)
        synchronized(bufferLock) {
            gpuBuffers.add(buffer)
        }
        val flatData = flatten(matrix)
        copyToGPU(metalContext, buffer, flatData)
        return buffer
    }
    
    private fun createTrackedBuffer(size: Int): Long {
        val buffer = createGPUBuffer(metalContext, size)
        synchronized(bufferLock) {
            gpuBuffers.add(buffer)
        }
        return buffer
    }

    private fun releaseTempBuffers(vararg buffers: Long) {
        buffers.forEach { buffer ->
            if (buffer != 0L) {
                try {
                    releaseGPUBuffer(metalContext, buffer)
                    synchronized(bufferLock) {
                        gpuBuffers.remove(buffer)
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to release temp buffer $buffer: ${e.message}")
                }
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun dispose() {
        try {
            // Clean up any tracked GPU buffers first
            synchronized(bufferLock) {
                gpuBuffers.forEach { bufferHandle ->
                    try {
                        releaseGPUBuffer(metalContext, bufferHandle)
                    } catch (e: Exception) {
                        println("Warning: Failed to release GPU buffer $bufferHandle: ${e.message}")
                    }
                }
                gpuBuffers.clear()
            }
            
            // Dispose Metal context
            if (metalContext != 0L) {
                disposeMetalContext(metalContext)
                metalContext = 0L
            }
        } catch (e: Exception) {
            println("Warning: Error during MetalComputeBackend disposal: ${e.message}")
        }
    }
    
    @Throws(Throwable::class)
    protected fun finalize() {
        dispose()
    }
}
