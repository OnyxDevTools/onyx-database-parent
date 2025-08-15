package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.MetalTensor
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
         * Perform matrix multiplication on GPU where the right-hand matrix is
         * logically transposed and the result is scaled by the provided factor.
         */
        @JvmStatic
        external fun gpuMatrixMultiplyTransposeScale(
            contextHandle: Long,
            bufferA: Long, rowsA: Int, colsA: Int,
            bufferB: Long, rowsB: Int, colsB: Int,
            scale: Float,
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

    override fun matrixMultiply(a: Tensor, b: Tensor): Tensor {
        require(a.cols == b.rows) {
            "Matrix dimensions don't match for multiplication: ${a.rows}x${a.cols} * ${b.rows}x${b.cols}"
        }

        val rowsA = a.rows
        val colsA = a.cols
        val rowsB = b.rows
        val colsB = b.cols

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
            val buf = Tensor.allocateDirectBuffer(resultSize)
            for (i in 0 until resultSize) {
                buf.put(i, resultData[i])
            }
            val result = MetalTensor(buf, rowsA, colsB, this, bufferResult, true)
            synchronized(bufferLock) { gpuBuffers.remove(bufferResult) }
            releaseTempBuffers(bufferA, bufferB)
            result
        } catch (e: Exception) {
            println("Metal matrix multiplication failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            super.matrixMultiply(a, b)
        }
    }

    /**
     * Computes `scale * a * b^T` on the GPU without explicitly transposing `b`.
     * Falls back to CPU implementation if the GPU path fails.
     */
    fun matrixMultiplyTransposeScale(a: Tensor, b: Tensor, scale: Float): Tensor {
        require(a.cols == b.cols) {
            "Matrix dimensions don't match for A * B^T: ${a.rows}x${a.cols} * ${b.rows}x${b.cols}"
        }

        val rowsA = a.rows
        val colsA = a.cols
        val rowsB = b.rows
        val colsB = b.cols

        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rowsA * rowsB
            bufferResult = createTrackedBuffer(resultSize * 4)

            val success = gpuMatrixMultiplyTransposeScale(
                metalContext,
                bufferA, rowsA, colsA,
                bufferB, rowsB, colsB,
                scale,
                bufferResult
            )

            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                val cpuTransposed = super.transpose(b)
                val cpuResult = super.matrixMultiply(a, cpuTransposed)
                val cpuScaled = super.scalarMultiply(cpuResult, scale)
                cpuTransposed.dispose()
                cpuResult.dispose()
                return cpuScaled
            }

            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val buf = Tensor.allocateDirectBuffer(resultSize)
            for (i in 0 until resultSize) {
                buf.put(i, resultData[i])
            }
            val result = MetalTensor(buf, rowsA, rowsB, this, bufferResult, true)
            synchronized(bufferLock) { gpuBuffers.remove(bufferResult) }
            releaseTempBuffers(bufferA, bufferB)
            result
        } catch (e: Exception) {
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            val cpuTransposed = super.transpose(b)
            val cpuResult = super.matrixMultiply(a, cpuTransposed)
            val cpuScaled = super.scalarMultiply(cpuResult, scale)
            cpuTransposed.dispose()
            cpuResult.dispose()
            cpuScaled
        }
    }

    override fun add(a: Tensor, b: Tensor): Tensor {
        gpuElementWise(a, b, 0)?.let { return it }
        return super.add(a, b)
    }

    override fun subtract(a: Tensor, b: Tensor): Tensor {
        gpuElementWise(a, b, 1)?.let { return it }
        return super.subtract(a, b)
    }

    override fun elementWiseMultiply(a: Tensor, b: Tensor): Tensor {
        gpuElementWise(a, b, 2)?.let { return it }
        return super.elementWiseMultiply(a, b)
    }

    override fun transpose(tensor: Tensor): Tensor {
        var inputBuffer = 0L
        var outputBuffer = 0L
        val rows = tensor.rows
        val cols = tensor.cols
        return try {
            inputBuffer = createMatrixBuffer(tensor)
            outputBuffer = createTrackedBuffer(rows * cols * 4)
            val success = gpuTranspose(metalContext, inputBuffer, outputBuffer, rows, cols)
            if (!success) {
                releaseTempBuffers(inputBuffer, outputBuffer)
                return super.transpose(tensor)
            }
            val buf = Tensor.allocateDirectBuffer(rows * cols)
            val result = MetalTensor(buf, cols, rows, this, outputBuffer, true, false)
            synchronized(bufferLock) { gpuBuffers.remove(outputBuffer) }
            releaseTempBuffers(inputBuffer)
            result
        } catch (e: Exception) {
            releaseTempBuffers(inputBuffer, outputBuffer)
            super.transpose(tensor)
        }
    }

    override fun softmax(tensor: Tensor): Tensor {
        var inputBuffer = 0L
        var outputBuffer = 0L
        val rows = tensor.rows
        val cols = tensor.cols
        return try {
            inputBuffer = createMatrixBuffer(tensor)
            outputBuffer = createTrackedBuffer(rows * cols * 4)
            val success = gpuSoftmax(metalContext, inputBuffer, outputBuffer, rows, cols)
            if (!success) {
                releaseTempBuffers(inputBuffer, outputBuffer)
                return super.softmax(tensor)
            }
            val resultData = copyFromGPU(metalContext, outputBuffer, rows * cols)
            val buf = Tensor.allocateDirectBuffer(rows * cols)
            for (i in 0 until rows * cols) {
                buf.put(i, resultData[i])
            }
            val result = MetalTensor(buf, rows, cols, this, outputBuffer, true)
            synchronized(bufferLock) { gpuBuffers.remove(outputBuffer) }
            releaseTempBuffers(inputBuffer)
            result
        } catch (e: Exception) {
            releaseTempBuffers(inputBuffer, outputBuffer)
            super.softmax(tensor)
        }
    }

    private fun gpuElementWise(a: Tensor, b: Tensor, op: Int): Tensor? {
        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        val rows = a.rows
        val cols = a.cols
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            bufferResult = createTrackedBuffer(rows * cols * 4)
            val success = gpuElementWiseOperation(metalContext, bufferA, bufferB, bufferResult, rows, cols, op)
            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                null
            } else {
                val buf = Tensor.allocateDirectBuffer(rows * cols)
                val result = MetalTensor(buf, rows, cols, this, bufferResult, true, false)
                synchronized(bufferLock) { gpuBuffers.remove(bufferResult) }
                releaseTempBuffers(bufferA, bufferB)
                result
            }
        } catch (e: Exception) {
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            null
        }
    }
    // Helper methods

    private fun createMatrixBuffer(tensor: Tensor): Long {
        if (tensor is MetalTensor && tensor.metal === this) {
            return tensor.ensureGpuBuffer()
        }
        val size = tensor.rows * tensor.cols
        val buffer = createGPUBuffer(metalContext, size * 4)
        synchronized(bufferLock) {
            gpuBuffers.add(buffer)
        }
        val flatData = flatten(tensor)
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
                var shouldRelease = false
                synchronized(bufferLock) {
                    if (gpuBuffers.remove(buffer)) {
                        shouldRelease = true
                    }
                }
                if (shouldRelease) {
                    try {
                        releaseGPUBuffer(metalContext, buffer)
                    } catch (e: Exception) {
                        println("Warning: Failed to release temp buffer $buffer: ${e.message}")
                    }
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

val metalCompute by lazy { MetalComputeBackend() }
