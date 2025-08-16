package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor

/**
 * Core compute backend interface that abstracts linear algebra operations.
 * This allows the AI framework to support different compute backends (CPU, GPU, etc.)
 * without changing the neural network layer implementations.
 */
interface ComputeBackend {
    
    /**
     * Performs matrix multiplication: A × B
     */
    fun matrixMultiply(a: Tensor, b: Tensor): Tensor
    
    /**
     * Performs element-wise addition: A + B
     */
    fun add(a: Tensor, b: Tensor): Tensor
    
    /**
     * Performs element-wise subtraction: A - B  
     */
    fun subtract(a: Tensor, b: Tensor): Tensor
    
    /**
     * Performs element-wise multiplication: A ⊙ B
     */
    fun elementWiseMultiply(a: Tensor, b: Tensor): Tensor
    
    /**
     * Transposes the matrix: A^T
     */
    fun transpose(tensor: Tensor): Tensor
    
    /**
     * Multiplies matrix by scalar: scalar × A
     */
    fun scalarMultiply(tensor: Tensor, scalar: Float): Tensor
    
    /**
     * Adds a vector to each row of the matrix
     */
    fun addVectorToRows(tensor: Tensor, vector: FloatArray): Tensor
    
    /**
     * Applies element-wise transformation function
     */
    fun applyElementWise(tensor: Tensor, transform: (Float) -> Float): Tensor
    
    /**
     * Computes sum of each column
     */
    fun sumColumns(tensor: Tensor): FloatArray
    
    /**
     * Applies softmax activation function
     */
    fun softmax(tensor: Tensor): Tensor
    /**
     * Gathers rows from a parameter matrix according to the given indices.
     * @param params  The source matrix (numParams x cols)
     * @param indices The output row indices into params (length = output rows)
     * @return Tensor of shape (indices.size x params.columnSize)
     */
    fun gatherRows(params: Tensor, indices: IntArray): Tensor

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
