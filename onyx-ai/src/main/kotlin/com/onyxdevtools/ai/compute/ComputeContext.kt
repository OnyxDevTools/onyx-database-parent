package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix

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
    fun createMatrix(rows: Int, cols: Int): Matrix
    
    /**
     * Creates a new matrix with the specified dimensions and initial value
     */
    fun createMatrix(rows: Int, cols: Int, initialValue: Float): Matrix
    
    /**
     * Transfers a matrix to the backend's preferred memory space (GPU for GPU backends)
     * For CPU backend, this is typically a no-op
     */
    fun copyToBackend(matrix: Matrix): Matrix
    
    /**
     * Transfers a matrix from the backend's memory space back to CPU
     * For CPU backend, this is typically a no-op
     */
    fun copyFromBackend(matrix: Matrix): Matrix
    
    /**
     * Releases any backend-specific resources associated with a matrix
     */
    fun releaseMatrix(matrix: Matrix)
    
    /**
     * Gets memory usage statistics for the backend
     */
    fun getMemoryInfo(): ComputeMemoryInfo
    
    /**
     * Disposes of the compute context and releases all resources
     */
    fun dispose()
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
    
    override fun createMatrix(rows: Int, cols: Int): Matrix {
        return Array(rows) { FloatArray(cols) }
    }
    
    override fun createMatrix(rows: Int, cols: Int, initialValue: Float): Matrix {
        return Array(rows) { FloatArray(cols) { initialValue } }
    }
    
    override fun copyToBackend(matrix: Matrix): Matrix = matrix
    
    override fun copyFromBackend(matrix: Matrix): Matrix = matrix
    
    override fun releaseMatrix(matrix: Matrix) {
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
}
