package dev.onyx.ai.compute

import dev.onyx.ai.Tensor
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

    private val METAL_HIGH_OPS_THRESHOLD = 250_000
    private val METAL_DIMENS_THRESHOLD = 16

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
            val tempFile = java.io.File.createTempFile(
                "onyx-metal", when {
                    osName.contains("mac") -> ".dylib"
                    osName.contains("linux") -> ".so"
                    osName.contains("windows") -> ".dll"
                    else -> ".lib"
                }
            )
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

        @JvmStatic
        external fun gpuMultiHeadAttention(
            contextHandle: Long,
            bufferQ: Long, bufferK: Long, bufferV: Long,
            seqLen: Int, headCount: Int, headSize: Int,
            scale: Float, causal: Int,
            bufferOut: Long
        ): Boolean

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

        @JvmStatic
        external fun copyFromGPUInto(contextHandle: Long, bufferHandle: Long, destination: FloatArray, size: Int)

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

    override fun multiHeadAttentionAllHeads(
        q: Tensor, k: Tensor, v: Tensor,
        headCount: Int, headSize: Int,
        causal: Boolean, scale: Float
    ): Tensor {
        val seqLen = q.size
        val model = headCount * headSize
        require(q[0].size == model && k[0].size == model && v[0].size == model)

        // Heuristic: big enough to benefit from GPU
        val work = 1L * seqLen * seqLen * headSize * headCount
        val wantGPU = (metalContext != 0L) && work >= 250_000L

        if (!wantGPU) {
            if (metalContext == 0L) println("Metal: context=0 (library not loaded or init failed) -> CPU MHA")
            return super.multiHeadAttentionAllHeads(q, k, v, headCount, headSize, causal, scale)
        }

        var qBuf = 0L; var kBuf = 0L; var vBuf = 0L; var outBuf = 0L
        return try {
            qBuf = createMatrixBuffer(q)
            kBuf = createMatrixBuffer(k)
            vBuf = createMatrixBuffer(v)
            outBuf = createTrackedBuffer(seqLen * model * 4)

            val ok = gpuMultiHeadAttention(
                metalContext, qBuf, kBuf, vBuf,
                seqLen, headCount, headSize, scale, if (causal) 1 else 0, outBuf
            )
            if (!ok) {
                println("Metal MHA: gpuMultiHeadAttention returned false -> CPU fallback")
                releaseTempBuffers(qBuf, kBuf, vBuf, outBuf)
                return super.multiHeadAttentionAllHeads(q, k, v, headCount, headSize, causal, scale)
            }

            val out = FloatArray(seqLen * model)
            copyFromGPUInto(metalContext, outBuf, out, out.size)
            releaseTempBuffers(qBuf, kBuf, vBuf, outBuf)
            Tensor(seqLen, model, out)
        } catch (e: Exception) {
            println("Metal MHA: exception '${e.message}' -> CPU fallback")
            releaseTempBuffers(qBuf, kBuf, vBuf, outBuf)
            super.multiHeadAttentionAllHeads(q, k, v, headCount, headSize, causal, scale)
        }
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
        require(a[0].size == b.size) {
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}"
        }

        val rowsA = a.size
        val colsA = a[0].size
        val rowsB = b.size
        val colsB = b[0].size

        val operations = rowsA.toLong() * colsA.toLong() * colsB.toLong()
        if (operations <= METAL_HIGH_OPS_THRESHOLD || minOf(
                rowsA,
                colsA,
                colsB
            ) < METAL_DIMENS_THRESHOLD
        ) return super.matrixMultiply(a, b)

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

            val resultData = FloatArray(resultSize)
            copyFromGPUInto(metalContext, bufferResult, resultData, resultSize)
            val resultTensor = Tensor(rows = rowsA, cols = colsB, data = resultData)
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            resultTensor
        } catch (e: Exception) {
            println("Metal matrix multiplication failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            super.matrixMultiply(a, b)
        }
    }
    // Helper methods

    private fun createMatrixBuffer(tensor: Tensor): Long {
        val size = tensor.size * tensor[0].size
        val buffer = createGPUBuffer(metalContext, size * 4)
        synchronized(bufferLock) {
            gpuBuffers.add(buffer)
        }
        copyToGPU(metalContext, buffer, tensor.data)
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
