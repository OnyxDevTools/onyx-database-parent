package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix

/**
 * Core compute backend interface that abstracts linear algebra operations.
 * This allows the AI framework to support different compute backends (CPU, GPU, etc.)
 * without changing the neural network layer implementations.
 */
interface ComputeBackend {
    
    /**
     * Performs matrix multiplication: A × B
     */
    fun matrixMultiply(a: Matrix, b: Matrix): Matrix
    
    /**
     * Performs element-wise addition: A + B
     */
    fun add(a: Matrix, b: Matrix): Matrix
    
    /**
     * Performs element-wise subtraction: A - B  
     */
    fun subtract(a: Matrix, b: Matrix): Matrix
    
    /**
     * Performs element-wise multiplication: A ⊙ B
     */
    fun elementWiseMultiply(a: Matrix, b: Matrix): Matrix
    
    /**
     * Transposes the matrix: A^T
     */
    fun transpose(matrix: Matrix): Matrix
    
    /**
     * Multiplies matrix by scalar: scalar × A
     */
    fun scalarMultiply(matrix: Matrix, scalar: Float): Matrix
    
    /**
     * Adds a vector to each row of the matrix
     */
    fun addVectorToRows(matrix: Matrix, vector: FloatArray): Matrix
    
    /**
     * Applies element-wise transformation function
     */
    fun applyElementWise(matrix: Matrix, transform: (Float) -> Float): Matrix
    
    /**
     * Computes sum of each column
     */
    fun sumColumns(matrix: Matrix): FloatArray
    
    /**
     * Applies softmax activation function
     */
    fun softmax(matrix: Matrix): Matrix
    
    /**
     * Calculates mean standard error between predicted and actual matrices
     */
    fun meanStandardError(predicted: Matrix, actual: Matrix): Float
    
    /**
     * Creates a deep copy of the matrix
     */
    fun deepCopy(matrix: Matrix): Matrix
    
    /**
     * Flattens matrix to 1D array in row-major order
     */
    fun flatten(matrix: Matrix): FloatArray
    
    /**
     * Gets the backend type identifier
     */
    val backendType: ComputeBackendType
}

/**
 * Enumeration of supported compute backend types
 */
enum class ComputeBackendType {
    CPU,
    METAL,
    CUDA, 
    OPENCL
}
