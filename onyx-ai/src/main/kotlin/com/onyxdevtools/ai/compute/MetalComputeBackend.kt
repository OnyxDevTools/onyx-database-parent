package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix
import java.util.*
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

    // Define threshold based on granular benchmark analysis: Metal consistently wins for very large matrices.
    private val METAL_HIGH_OPS_THRESHOLD = 10_000_000L // Metal consistently wins for total operations >= 1 billion
    // Add near top of class (replace old METAL_HIGH_OPS_THRESHOLD)
    private val GEMM_CUTOVER_OPS = 6_000_000L     // ops = rowsA * colsA * colsB
    private val GEMV_CUTOVER_WORK = 2_000_000L    // work ≈ colsA * max(rowsA, colsB) when one dim == 1

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
                // First try to load from standard library path
                System.loadLibrary("onyx-metal")
                nativeLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                try {
                    // Fallback: try to load from resources (for IDE environments)
                    loadLibraryFromResources()
                    nativeLibraryLoaded = true
                } catch (resourceException: Exception) {
                    nativeLibraryLoaded = false
                    println("Metal native library not available: ${e.message}")
                    println("Resource loading also failed: ${resourceException.message}")
                }
            }
        }
        
        /**
         * Load native library from embedded resources
         */
        private fun loadLibraryFromResources() {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            val libraryName = when {
                osName.contains("mac") -> "libonyx-metal.dylib"
                osName.contains("linux") -> "libonyx-metal.so"
                osName.contains("windows") -> "onyx-metal.dll"
                else -> throw UnsupportedOperationException("Unsupported OS: $osName")
            }
            
            val resourcePath = "/native/$libraryName"
            val inputStream = MetalComputeBackend::class.java.getResourceAsStream(resourcePath)
                ?: throw RuntimeException("Native library not found in resources: $resourcePath")
            
            // Create temporary file
            val tempFile = java.io.File.createTempFile("onyx-metal", when {
                osName.contains("mac") -> ".dylib"
                osName.contains("linux") -> ".so"
                osName.contains("windows") -> ".dll"
                else -> ".lib"
            })
            tempFile.deleteOnExit()
            
            // Copy library to temp file
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Load the temporary library
            System.load(tempFile.absolutePath)
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

        // Shape-aware GPU decision
        val isGemvLike = (rowsA == 1 || colsB == 1)
        val totalOps = rowsA.toLong() * colsA.toLong() * colsB.toLong()

        val useGPU = if (isGemvLike) {
            // For GEMV-like shapes, use a lower cutover based on effective work (K * output_len)
            val work = colsA.toLong() * max(rowsA, colsB).toLong()
            work >= GEMV_CUTOVER_WORK
        } else {
            totalOps >= GEMM_CUTOVER_OPS
        }

        if (!useGPU) {
            return super.matrixMultiply(a, b)
        }

        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rowsA * colsB
            bufferResult = createTrackedBuffer(resultSize * 4)

            val success = gpuMatrixMultiply(
                metalContext,
                bufferA, rowsA, colsA,
                bufferB, rowsB, colsB,
                bufferResult
            )

            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                return super.matrixMultiply(a, b)
            }

            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val result = Array(rowsA) { row ->
                FloatArray(colsB) { col -> resultData[row * colsB + col] }
            }

            releaseTempBuffers(bufferA, bufferB, bufferResult)
            result
        } catch (e: Exception) {
            println("Metal matrix multiplication failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            super.matrixMultiply(a, b)
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
