package com.onyxdevtools.ai

/**
 * Configuration for matrix precision to balance memory usage and computational accuracy.
 */
enum class MatrixPrecision {
    /**
     * Single precision (32-bit) - Uses half the memory of double precision.
     * Suitable for most neural network applications where slight precision loss is acceptable.
     * Memory usage: ~4 bytes per element
     */
    SINGLE,
    
    /**
     * Double precision (64-bit) - Higher accuracy but uses more memory.
     * Recommended for applications requiring high numerical precision.
     * Memory usage: ~8 bytes per element
     */
    DOUBLE;
    
    /**
     * Returns true if this is single precision
     */
    val isSinglePrecision: Boolean
        get() = this == SINGLE
}

/**
 * Global configuration for matrix precision used throughout the neural network.
 * This can be set once at the beginning of your application to control memory usage.
 */
object MatrixConfig {
    /**
     * Default precision for new matrices. Can be changed globally.
     * Default is DOUBLE for backward compatibility, but SINGLE is recommended for most use cases.
     */
    var defaultPrecision: MatrixPrecision = MatrixPrecision.SINGLE
    
    /**
     * Convenience function to set single precision globally for memory optimization
     */
    fun useSinglePrecision() {
        defaultPrecision = MatrixPrecision.SINGLE
    }
    
    /**
     * Convenience function to set double precision globally for maximum accuracy
     */
    fun useDoublePrecision() {
        defaultPrecision = MatrixPrecision.DOUBLE
    }
    
    /**
     * Estimates memory savings when using single precision vs double precision
     * @param totalElements Total number of matrix elements in your model
     * @return Memory savings in bytes when using single precision
     */
    fun estimateMemorySavings(totalElements: Long): Long {
        return totalElements * 4 // 4 bytes saved per element (8 - 4)
    }
}

/**
 * Factory functions that respect the global precision setting
 */
fun createMatrix(rows: Int, cols: Int, initializer: (Int, Int) -> Double = { _, _ -> 0.0 }): FlexibleMatrix {
    return createMatrix(rows, cols, MatrixConfig.defaultPrecision.isSinglePrecision, initializer)
}

fun createMatrix(rows: Int, cols: Int, precision: MatrixPrecision, initializer: (Int, Int) -> Double = { _, _ -> 0.0 }): FlexibleMatrix {
    return createMatrix(rows, cols, precision.isSinglePrecision, initializer)
}

// Matrix typealias removed - all conversions now handled by FlexibleMatrix directly
