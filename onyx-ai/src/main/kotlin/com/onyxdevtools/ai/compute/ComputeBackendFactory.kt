package com.onyxdevtools.ai.compute

/**
 * Factory for creating compute backend instances.
 * This factory automatically detects available backends and selects the best one
 * based on performance characteristics and system capabilities.
 */
object ComputeBackendFactory {
    
    /**
     * Creates the best available compute backend for the current system.
     * Selection priority:
     * 1. Metal (macOS GPU)
     * 2. CUDA (NVIDIA GPU) 
     * 3. OpenCL (AMD/Intel GPU)
     * 4. CPU (fallback)
     */
    fun createBest(): ComputeBackend {
        return when {
            isMetalAvailable() -> createMetal()
            isCudaAvailable() -> createCuda() 
            isOpenCLAvailable() -> createOpenCL()
            else -> createCPU()
        }
    }
    
    /**
     * Creates a CPU compute backend with platform-specific optimizations.
     * Uses CPUComputeBackend (with SIMD) if Java Vector API is available,
     * otherwise falls back to BasicCPUComputeBackend (Android compatible).
     */
    fun createCPU(): ComputeBackend {
        return try {
            // Try to create optimized CPU backend
            CPUComputeBackend()
        } catch (e: Throwable) {
            // Fall back to basic CPU backend for Android/unsupported platforms
            BasicCPUComputeBackend()
        }
    }
    
    /**
     * Creates a basic CPU compute backend that works on all platforms including Android.
     * This implementation doesn't use Java Vector API and is compatible with all JVMs.
     */
    fun createBasicCPU(): ComputeBackend {
        return BasicCPUComputeBackend()
    }
    
    /**
     * Creates a Metal compute backend (macOS only)
     */
    fun createMetal(): ComputeBackend {
        // TODO: Implement MetalComputeBackend
        throw UnsupportedOperationException("Metal backend not yet implemented")
    }
    
    /**
     * Creates a CUDA compute backend (NVIDIA GPUs)
     */
    fun createCuda(): ComputeBackend {
        // TODO: Implement CudaComputeBackend
        throw UnsupportedOperationException("CUDA backend not yet implemented")
    }
    
    /**
     * Creates an OpenCL compute backend (AMD/Intel GPUs)
     */
    fun createOpenCL(): ComputeBackend {
        // TODO: Implement OpenCLComputeBackend
        throw UnsupportedOperationException("OpenCL backend not yet implemented")
    }
    
    /**
     * Checks if Metal framework is available (macOS only)
     */
    private fun isMetalAvailable(): Boolean {
        // TODO: Implement Metal detection
        return false
    }
    
    /**
     * Checks if CUDA runtime is available
     */
    private fun isCudaAvailable(): Boolean {
        // TODO: Implement CUDA detection
        return false
    }
    
    /**
     * Checks if OpenCL runtime is available
     */
    private fun isOpenCLAvailable(): Boolean {
        // TODO: Implement OpenCL detection
        return false
    }
    
    /**
     * Gets information about all available backends on the current system
     */
    fun getAvailableBackends(): List<ComputeBackendType> {
        val available = mutableListOf<ComputeBackendType>()
        
        // CPU is always available
        available.add(ComputeBackendType.CPU)
        
        if (isMetalAvailable()) {
            available.add(ComputeBackendType.METAL)
        }
        
        if (isCudaAvailable()) {
            available.add(ComputeBackendType.CUDA)
        }
        
        if (isOpenCLAvailable()) {
            available.add(ComputeBackendType.OPENCL)
        }
        
        return available
    }
}
