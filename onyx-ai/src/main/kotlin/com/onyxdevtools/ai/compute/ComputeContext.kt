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

    override fun copyToBackend(tensor: Tensor): Tensor = tensor
    
    override fun copyFromBackend(tensor: Tensor): Tensor = tensor
    
    override fun releaseMatrix(tensor: Tensor) {
        // Ensure any underlying resources (e.g. GPU buffers) are released.
        // For heap-backed tensors this is effectively a no-op.
        tensor.dispose()
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
