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
class MetalComputeBackend : ComputeBackend {
    
    override val backendType: ComputeBackendType = ComputeBackendType.METAL
    
    // Native Metal interface handle
    private var metalContext: Long = 0L
    
    // Memory management tracking - track all allocated buffers for cleanup
    private val gpuBuffers = mutableSetOf<Long>()
    private val bufferLock = Any()
    
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
         * Perform matrix addition on GPU
         */
        @JvmStatic
        external fun gpuMatrixAdd(
            contextHandle: Long,
            bufferA: Long, bufferB: Long, bufferResult: Long,
            rows: Int, cols: Int
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
        
        // For small matrices, use CPU to avoid GPU transfer overhead
        if (rowsA * colsA * colsB < 10000) {
            return matrixMultiplyCPU(a, b)
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
                return matrixMultiplyCPU(a, b)
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
            matrixMultiplyCPU(a, b)
        }
    }
    
    override fun add(a: Matrix, b: Matrix): Matrix {
        require(a.size == b.size && a[0].size == b[0].size) {
            "Matrix dimensions must match for addition"
        }
        
        val rows = a.size
        val cols = a[0].size
        
        // For small matrices, use CPU
        if (rows * cols < 1000) {
            return addCPU(a, b)
        }
        
        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rows * cols
            bufferResult = createTrackedBuffer(resultSize * 4)
            
            val success = gpuElementWiseOperation(
                metalContext, bufferA, bufferB, bufferResult, rows, cols, 0 // 0 = add
            )
            
            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                return addCPU(a, b)
            }
            
            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val result = Array(rows) { row ->
                FloatArray(cols) { col ->
                    resultData[row * cols + col]
                }
            }
            
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            result
        } catch (e: Exception) {
            println("Metal matrix addition failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            addCPU(a, b)
        }
    }
    
    override fun subtract(a: Matrix, b: Matrix): Matrix {
        require(a.size == b.size && a[0].size == b[0].size) {
            "Matrix dimensions must match for subtraction"
        }
        
        val rows = a.size
        val cols = a[0].size
        
        // For small matrices, use CPU
        if (rows * cols < 1000) {
            return subtractCPU(a, b)
        }
        
        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rows * cols
            bufferResult = createTrackedBuffer(resultSize * 4)
            
            val success = gpuElementWiseOperation(
                metalContext, bufferA, bufferB, bufferResult, rows, cols, 1 // 1 = subtract
            )
            
            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                return subtractCPU(a, b)
            }
            
            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val result = Array(rows) { row ->
                FloatArray(cols) { col ->
                    resultData[row * cols + col]
                }
            }
            
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            result
        } catch (e: Exception) {
            println("Metal matrix subtraction failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            subtractCPU(a, b)
        }
    }
    
    override fun elementWiseMultiply(a: Matrix, b: Matrix): Matrix {
        require(a.size == b.size && a[0].size == b[0].size) {
            "Matrix dimensions must match for element-wise multiplication"
        }
        
        val rows = a.size
        val cols = a[0].size
        
        // For small matrices, use CPU
        if (rows * cols < 1000) {
            return elementWiseMultiplyCPU(a, b)
        }
        
        var bufferA = 0L
        var bufferB = 0L
        var bufferResult = 0L
        return try {
            bufferA = createMatrixBuffer(a)
            bufferB = createMatrixBuffer(b)
            val resultSize = rows * cols
            bufferResult = createTrackedBuffer(resultSize * 4)
            
            val success = gpuElementWiseOperation(
                metalContext, bufferA, bufferB, bufferResult, rows, cols, 2 // 2 = multiply
            )
            
            if (!success) {
                releaseTempBuffers(bufferA, bufferB, bufferResult)
                return elementWiseMultiplyCPU(a, b)
            }
            
            val resultData = copyFromGPU(metalContext, bufferResult, resultSize)
            val result = Array(rows) { row ->
                FloatArray(cols) { col ->
                    resultData[row * cols + col]
                }
            }
            
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            result
        } catch (e: Exception) {
            println("Metal element-wise multiplication failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferA, bufferB, bufferResult)
            elementWiseMultiplyCPU(a, b)
        }
    }
    
    override fun transpose(matrix: Matrix): Matrix {
        if (matrix.isEmpty()) return arrayOf()
        
        val rows = matrix.size
        val cols = matrix[0].size
        
        // For small matrices, use CPU
        if (rows * cols < 1000) {
            return transposeCPU(matrix)
        }
        
        var bufferInput = 0L
        var bufferOutput = 0L
        return try {
            bufferInput = createMatrixBuffer(matrix)
            bufferOutput = createTrackedBuffer(rows * cols * 4)
            
            val success = gpuTranspose(metalContext, bufferInput, bufferOutput, rows, cols)
            
            if (!success) {
                releaseTempBuffers(bufferInput, bufferOutput)
                return transposeCPU(matrix)
            }
            
            val resultData = copyFromGPU(metalContext, bufferOutput, rows * cols)
            val result = Array(cols) { row ->
                FloatArray(rows) { col ->
                    resultData[row * rows + col]
                }
            }
            
            releaseTempBuffers(bufferInput, bufferOutput)
            result
        } catch (e: Exception) {
            println("Metal matrix transpose failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferInput, bufferOutput)
            transposeCPU(matrix)
        }
    }
    
    override fun scalarMultiply(matrix: Matrix, scalar: Float): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> row[colIndex] * scalar } 
        }.toTypedArray()
    }
    
    override fun addVectorToRows(matrix: Matrix, vector: FloatArray): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> row[colIndex] + vector[colIndex] } 
        }.toTypedArray()
    }
    
    override fun applyElementWise(matrix: Matrix, transform: (Float) -> Float): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> transform(row[colIndex]) } 
        }.toTypedArray()
    }
    
    override fun sumColumns(matrix: Matrix): FloatArray {
        return FloatArray(if (matrix.isEmpty()) 0 else matrix[0].size).also { columnSums ->
            for (row in matrix) {
                for (colIndex in row.indices) {
                    columnSums[colIndex] += row[colIndex]
                }
            }
        }
    }
    
    override fun softmax(matrix: Matrix): Matrix {
        val rows = matrix.size
        val cols = if (rows > 0) matrix[0].size else 0
        
        if (rows * cols < 1000) {
            return softmaxCPU(matrix)
        }
        
        var bufferInput = 0L
        var bufferOutput = 0L
        return try {
            bufferInput = createMatrixBuffer(matrix)
            bufferOutput = createTrackedBuffer(rows * cols * 4)
            
            val success = gpuSoftmax(metalContext, bufferInput, bufferOutput, rows, cols)
            
            if (!success) {
                releaseTempBuffers(bufferInput, bufferOutput)
                return softmaxCPU(matrix)
            }
            
            val resultData = copyFromGPU(metalContext, bufferOutput, rows * cols)
            val result = Array(rows) { row ->
                FloatArray(cols) { col ->
                    resultData[row * cols + col]
                }
            }
            
            releaseTempBuffers(bufferInput, bufferOutput)
            result
        } catch (e: Exception) {
            println("Metal softmax failed, falling back to CPU: ${e.message}")
            releaseTempBuffers(bufferInput, bufferOutput)
            softmaxCPU(matrix)
        }
    }
    
    override fun meanStandardError(predicted: Matrix, actual: Matrix): Float {
        var sum = 0.0f
        var total = 0

        val rows = minOf(predicted.size, actual.size)
        for (i in 0 until rows) {
            val cols = minOf(predicted[i].size, actual[i].size)
            for (j in 0 until cols) {
                sum += (predicted[i][j] - actual[i][j]).pow(2)
                total++
            }
        }
        return if (total > 0) sum / total else 0.0f
    }
    
    override fun deepCopy(matrix: Matrix): Matrix {
        return matrix.map { it.copyOf() }.toTypedArray()
    }
    
    override fun flatten(matrix: Matrix): FloatArray {
        return buildList { 
            for (row in matrix) {
                addAll(row.asList())
            }
        }.toFloatArray()
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
    
    // CPU fallback methods
    
    private fun matrixMultiplyCPU(a: Matrix, b: Matrix): Matrix {
        val numRows = a.size
        val sharedDim = a[0].size
        val numCols = b[0].size
        val result = Array(numRows) { FloatArray(numCols) }

        for (row in 0 until numRows) {
            val resultRow = result[row]
            for (sharedIndex in 0 until sharedDim) {
                val valueA = a[row][sharedIndex]
                val rowB = b[sharedIndex]
                for (col in 0 until numCols) {
                    resultRow[col] += valueA * rowB[col]
                }
            }
        }
        return result
    }
    
    private fun addCPU(a: Matrix, b: Matrix): Matrix {
        return a.mapIndexed { rowIndex, row ->
            FloatArray(row.size) { colIndex -> row[colIndex] + b[rowIndex][colIndex] }
        }.toTypedArray()
    }
    
    private fun subtractCPU(a: Matrix, b: Matrix): Matrix {
        return a.mapIndexed { rowIndex, row ->
            FloatArray(row.size) { colIndex -> row[colIndex] - b[rowIndex][colIndex] }
        }.toTypedArray()
    }
    
    private fun elementWiseMultiplyCPU(a: Matrix, b: Matrix): Matrix {
        return a.mapIndexed { rowIndex, row ->
            FloatArray(row.size) { colIndex -> row[colIndex] * b[rowIndex][colIndex] }
        }.toTypedArray()
    }
    
    private fun transposeCPU(matrix: Matrix): Matrix {
        return Array(matrix[0].size) { colIndex ->
            FloatArray(matrix.size) { rowIndex -> matrix[rowIndex][colIndex] }
        }
    }
    
    private fun softmaxCPU(matrix: Matrix): Matrix {
        return matrix.map { logits ->
            val max = logits.maxOrNull() ?: 0.0f
            val expLogits = logits.map { exp(it - max) }
            val sumExp = expLogits.sum()
            expLogits.map { it / sumExp }.toFloatArray()
        }.toTypedArray()
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
