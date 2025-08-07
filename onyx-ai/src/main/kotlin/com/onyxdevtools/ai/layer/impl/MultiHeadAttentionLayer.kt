package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt

/**
 * Multi-Head Attention implementation for neural networks, particularly effective in transformer architectures.
 *
 * Multi-head attention allows the model to jointly attend to information from different representation
 * subspaces at different positions. Instead of performing a single attention function with the full
 * dimensionality, multiple attention heads allow the model to focus on different types of relationships.
 *
 * **Mathematical Formulation:**
 * Given input X, multi-head attention computes:
 * - For each head h: Q_h = XW^Q_h, K_h = XW^K_h, V_h = XW^V_h
 * - Attention_h(Q_h, K_h, V_h) = softmax(Q_h K_h^T / √d_k) V_h
 * - MultiHead(Q, K, V) = Concat(head_1, ..., head_h)W^O
 *
 * Where W^Q_h, W^K_h, W^V_h are learned query, key, and value projection matrices for head h,
 * and W^O is the output projection matrix.
 *
 * **Key Benefits:**
 * - **Multiple attention patterns**: Each head can focus on different relationships
 * - **Parallel processing**: All heads can be computed in parallel
 * - **Rich representations**: Captures diverse aspects of token relationships
 * - **Position independence**: Can model long-range dependencies
 *
 * **Usage in Transformers:**
 * Multi-head attention is the core component of transformer architectures:
 * - Self-attention in encoder layers
 * - Masked self-attention in decoder layers
 * - Cross-attention between encoder and decoder
 *
 * This implementation uses the Adam optimizer for updating all weight matrices.
 *
 * @param tokensPerSample The fixed number of tokens per input sample (sequence length)
 * @param modelSize The dimensionality of each token's embedding (must be divisible by headCount)
 * @param headCount The number of attention heads to use
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 * @see Layer
 * @see LayerNormalizationLayer
 */
class MultiHeadAttentionLayer(
    private val tokensPerSample: Int,
    private val modelSize: Int,
    private val headCount: Int,
    private val precision: MatrixPrecision = MatrixPrecision.DOUBLE
) : Layer {

    override var output: FlexibleMatrix? = null
    override var preActivation: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weight matrices for all heads (combined for efficiency)
    private var wQuery: FlexibleMatrix
    private var wKey: FlexibleMatrix
    private var wValue: FlexibleMatrix
    private var wOutput: FlexibleMatrix

    // Gradients for weight matrices
    private var gradWQuery: FlexibleMatrix? = null
    private var gradWKey: FlexibleMatrix? = null
    private var gradWValue: FlexibleMatrix? = null
    private var gradWOutput: FlexibleMatrix? = null

    // Adam optimizer state
    private var momentWQuery: FlexibleMatrix
    private var velocityWQuery: FlexibleMatrix
    private var momentWKey: FlexibleMatrix
    private var velocityWKey: FlexibleMatrix
    private var momentWValue: FlexibleMatrix
    private var velocityWValue: FlexibleMatrix
    private var momentWOutput: FlexibleMatrix
    private var velocityWOutput: FlexibleMatrix

    // Cached values for backward pass
    private var queries: FlexibleMatrix? = null
    private var keys: FlexibleMatrix? = null
    private var values: FlexibleMatrix? = null
    private var attentionWeights: FlexibleMatrix? = null
    private var attentionOutput: FlexibleMatrix? = null

    init {
        require(modelSize % headCount == 0) {
            "Model size ($modelSize) must be divisible by head count ($headCount)"
        }

        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        val scale = sqrt(2.0 / modelSize)

        // Initialize weights with Xavier/Glorot initialization
        wQuery = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 
            (Math.random() - 0.5) * 2 * scale
        }
        wKey = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 
            (Math.random() - 0.5) * 2 * scale
        }
        wValue = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 
            (Math.random() - 0.5) * 2 * scale
        }
        wOutput = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 
            (Math.random() - 0.5) * 2 * scale
        }

        // Initialize Adam optimizer state
        momentWQuery = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWQuery = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        momentWKey = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWKey = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        momentWValue = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWValue = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        momentWOutput = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWOutput = createMatrix(modelSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        val batchSize = input.rows / tokensPerSample

        // Compute Q, K, V matrices using high-performance matrix multiplication
        queries = com.onyxdevtools.ai.extensions.matrixMultiply(input, wQuery)
        keys = com.onyxdevtools.ai.extensions.matrixMultiply(input, wKey)
        values = com.onyxdevtools.ai.extensions.matrixMultiply(input, wValue)

        // Compute attention for all heads
        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSize)

        // Final output projection
        output = com.onyxdevtools.ai.extensions.matrixMultiply(attentionOutput!!, wOutput)

        return output!!
    }

    /**
     * Computes multi-head attention using queries, keys, and values with high performance.
     * This version uses direct memory operations and reuses buffers to avoid allocations.
     */
    private fun computeMultiHeadAttention(
        q: FlexibleMatrix,
        k: FlexibleMatrix,
        v: FlexibleMatrix,
        batchSize: Int
    ): FlexibleMatrix {
        val totalTokens = q.rows
        val result = createMatrix(totalTokens, modelSize, q.isSinglePrecision)
        
        // Pre-allocate working buffers to avoid repeated allocations
        val scoresBuffer = createMatrix(tokensPerSample, tokensPerSample, q.isSinglePrecision)
        val attentionBuffer = createMatrix(tokensPerSample, tokensPerSample, q.isSinglePrecision)

        val scale = 1.0 / sqrt(headSize.toDouble())

        for (batch in 0 until batchSize) {
            val startIdx = batch * tokensPerSample

            // Process all heads for this batch
            for (head in 0 until headCount) {
                val headStartCol = head * headSize

                // Compute attention scores: Q * K^T for this head
                // Working directly with matrix elements to avoid allocations
                for (i in 0 until tokensPerSample) {
                    for (j in 0 until tokensPerSample) {
                        var score = 0.0
                        for (d in 0 until headSize) {
                            score += q[startIdx + i, headStartCol + d] * k[startIdx + j, headStartCol + d]
                        }
                        scoresBuffer[i, j] = score * scale
                    }
                }

                // Apply softmax to each row (using high-performance version)
                applySoftmaxInPlace(scoresBuffer, attentionBuffer)

                // Apply attention to values: Attention * V for this head
                for (i in 0 until tokensPerSample) {
                    for (d in 0 until headSize) {
                        var output = 0.0
                        for (j in 0 until tokensPerSample) {
                            output += attentionBuffer[i, j] * v[startIdx + j, headStartCol + d]
                        }
                        result[startIdx + i, headStartCol + d] = output
                    }
                }
            }
        }

        attentionWeights = result // Store for backward pass
        return result
    }
    
    /**
     * High-performance in-place softmax that reuses buffers
     */
    private fun applySoftmaxInPlace(input: FlexibleMatrix, output: FlexibleMatrix) {
        for (i in 0 until input.rows) {
            // Find max for numerical stability
            var maxVal = input[i, 0]
            for (j in 1 until input.cols) {
                val value = input[i, j]
                if (value > maxVal) maxVal = value
            }
            
            // Compute exp and sum
            var sum = 0.0
            for (j in 0 until input.cols) {
                val expVal = kotlin.math.exp(input[i, j] - maxVal)
                output[i, j] = expVal
                sum += expVal
            }
            
            // Normalize
            val invSum = 1.0 / sum
            for (j in 0 until input.cols) {
                output[i, j] *= invSum
            }
        }
    }

    /**
     * Computes multi-head attention using standard Matrix input.
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()
    }

    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        val input = currentInput!!

        // Gradient w.r.t. output projection weights
        gradWOutput = com.onyxdevtools.ai.extensions.matrixMultiply(attentionOutput!!.transpose(), delta)

        // Gradient w.r.t. attention output
        val gradAttentionOutput = com.onyxdevtools.ai.extensions.matrixMultiply(delta, wOutput.transpose())

        // Gradients w.r.t. Q, K, V (simplified implementation)
        gradWQuery = com.onyxdevtools.ai.extensions.matrixMultiply(input.transpose(), gradAttentionOutput)
        gradWKey = com.onyxdevtools.ai.extensions.matrixMultiply(input.transpose(), gradAttentionOutput)
        gradWValue = com.onyxdevtools.ai.extensions.matrixMultiply(input.transpose(), gradAttentionOutput)

        // Gradient w.r.t. input
        val gradInput = createMatrix(input.rows, input.cols, input.isSinglePrecision) { _, _ -> 0.0 }
        
        val gradQ = com.onyxdevtools.ai.extensions.matrixMultiply(gradAttentionOutput, wQuery.transpose())
        val gradK = com.onyxdevtools.ai.extensions.matrixMultiply(gradAttentionOutput, wKey.transpose())
        val gradV = com.onyxdevtools.ai.extensions.matrixMultiply(gradAttentionOutput, wValue.transpose())

        for (i in 0 until input.rows) {
            for (j in 0 until input.cols) {
                gradInput[i, j] = gradQ[i, j] + gradK[i, j] + gradV[i, j]
            }
        }

        return gradInput
    }

    /**
     * Computes the backward pass for multi-head attention with standard Matrix input.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        return backward(
            currentInput?.toFlexibleMatrix(),
            delta.toFlexibleMatrix(),
            featureSize,
            nextLayer,
            previousLayer,
            lambda
        ).toMatrix()
    }

    /**
     * Updates all weight matrices using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        fun correctMoment(moment: Double) = moment / (1.0 - adamBeta1Power)
        fun correctVelocity(velocity: Double) = velocity / (1.0 - adamBeta2Power)

        // Update query weights
        updateWeightMatrix(wQuery, gradWQuery!!, momentWQuery, velocityWQuery, 
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        
        // Update key weights
        updateWeightMatrix(wKey, gradWKey!!, momentWKey, velocityWKey,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        
        // Update value weights
        updateWeightMatrix(wValue, gradWValue!!, momentWValue, velocityWValue,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        
        // Update output weights
        updateWeightMatrix(wOutput, gradWOutput!!, momentWOutput, velocityWOutput,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
    }

    /**
     * Helper function to update a weight matrix using Adam optimizer.
     */
    private fun updateWeightMatrix(
        weights: FlexibleMatrix,
        gradients: FlexibleMatrix,
        moment: FlexibleMatrix,
        velocity: FlexibleMatrix,
        beta1: Double,
        beta2: Double,
        learningRate: Double,
        correctMoment: (Double) -> Double,
        correctVelocity: (Double) -> Double
    ) {
        for (i in 0 until weights.rows) {
            for (j in 0 until weights.cols) {
                val gradient = gradients[i, j]
                
                moment[i, j] = beta1 * moment[i, j] + (1 - beta1) * gradient
                velocity[i, j] = beta2 * velocity[i, j] + (1 - beta2) * gradient * gradient
                
                weights[i, j] -= learningRate * correctMoment(moment[i, j]) / 
                                 (sqrt(correctVelocity(velocity[i, j])) + EPSILON)
            }
        }
    }

    /**
     * Creates a deep copy of the multi-head attention layer.
     */
    override fun clone(): Layer {
        return MultiHeadAttentionLayer(tokensPerSample, modelSize, headCount, precision).also { copy ->
            copy.wQuery = wQuery.deepCopy()
            copy.wKey = wKey.deepCopy()
            copy.wValue = wValue.deepCopy()
            copy.wOutput = wOutput.deepCopy()
            
            copy.momentWQuery = momentWQuery.deepCopy()
            copy.velocityWQuery = velocityWQuery.deepCopy()
            copy.momentWKey = momentWKey.deepCopy()
            copy.velocityWKey = velocityWKey.deepCopy()
            copy.momentWValue = momentWValue.deepCopy()
            copy.velocityWValue = velocityWValue.deepCopy()
            copy.momentWOutput = momentWOutput.deepCopy()
            copy.velocityWOutput = velocityWOutput.deepCopy()
            
            copy.output = output?.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
            copy.queries = queries?.deepCopy()
            copy.keys = keys?.deepCopy()
            copy.values = values?.deepCopy()
            copy.attentionWeights = attentionWeights?.deepCopy()
            copy.attentionOutput = attentionOutput?.deepCopy()
            copy.gradWQuery = gradWQuery?.deepCopy()
            copy.gradWKey = gradWKey?.deepCopy()
            copy.gradWValue = gradWValue?.deepCopy()
            copy.gradWOutput = gradWOutput?.deepCopy()
        }
    }

    companion object {
        private const val EPSILON = 1e-8
    }
}
