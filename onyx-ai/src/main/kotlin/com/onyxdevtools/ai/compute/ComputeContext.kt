package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor

/**
 * ComputeContext provides a high-level interface for managing compute operations
 * and resources across different backend implementations.
 */
interface ComputeContext {
    /**
     * The underlying compute backend
     */
    val backend: ComputeBackend
    
    /**
     * Creates a new matrix with the specified dimensions
     */
    fun createMatrix(rows: Int, cols: Int): Tensor
    
    /**
     * Creates a new matrix with the specified dimensions and initial value
     */
    fun createMatrix(rows: Int, cols: Int, initialValue: Float): Tensor
    
    /**
     * Transfers a matrix to the backend's preferred memory space (GPU for GPU backends)
     * For CPU backend, this is typically a no-op
     */
    fun copyToBackend(tensor: Tensor): Tensor
    
    /**
     * Transfers a matrix from the backend's memory space back to CPU
     * For CPU backend, this is typically a no-op
     */
    fun copyFromBackend(tensor: Tensor): Tensor
    
    /**
     * Releases any backend-specific resources associated with a matrix
     */
    fun releaseMatrix(tensor: Tensor)
    
    /**
     * Gets memory usage statistics for the backend
     */
    fun getMemoryInfo(): ComputeMemoryInfo
    
    /**
     * Disposes of the compute context and releases all resources
     */
    fun dispose()
    /**
     * Creates a row-vector tensor (1 x n) for broadcasting operations.
     */
    fun createRowVector(vector: FloatArray): Tensor
    /**
     * Creates a column-vector tensor (m x 1) for broadcasting operations.
     */
    fun createColVector(vector: FloatArray): Tensor
}

/**
 * Information about compute backend memory usage
 */
data class ComputeMemoryInfo(
    val totalMemory: Long,
    val availableMemory: Long,
    val usedMemory: Long
)

/**
 * Default compute context that automatically selects the best available backend
 */
class DefaultComputeContext : ComputeContext {
    override val backend: ComputeBackend = ComputeBackendFactory.createBest()
    
    override fun createMatrix(rows: Int, cols: Int): Tensor {
        return Tensor(rows, cols)
    }
    
    override fun createMatrix(rows: Int, cols: Int, initialValue: Float): Tensor {
        return Tensor(rows, cols) { _, _ -> initialValue }
    }
    
    override fun copyToBackend(tensor: Tensor): Tensor = tensor
    
    override fun copyFromBackend(tensor: Tensor): Tensor = tensor
    
    override fun releaseMatrix(tensor: Tensor) {
        // No-op for CPU matrices
    }
    
    override fun getMemoryInfo(): ComputeMemoryInfo {
        val runtime = Runtime.getRuntime()
        return ComputeMemoryInfo(
            totalMemory = runtime.totalMemory(),
            availableMemory = runtime.freeMemory(),
            usedMemory = runtime.totalMemory() - runtime.freeMemory()
        )
    }
    
    override fun dispose() {
        // No-op for CPU backend
    }

    override fun createRowVector(vector: FloatArray): Tensor {
        val cols = vector.size
        val t = Tensor(1, cols)
        val row = t[0]
        for (j in 0 until cols) {
            row[j] = vector[j]
        }
        return t
    }
    override fun createColVector(vector: FloatArray): Tensor {
        val rows = vector.size
        val t = Tensor(rows, 1)
        for (i in 0 until rows) {
            t[i, 0] = vector[i]
        }
        return t
    }
}
