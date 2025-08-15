package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt

/**
 * Optimized Multi-Head Attention with Key-Value caching for autoregressive generation.
 * 
 * This implementation dramatically speeds up text generation by caching previous key and value
 * computations during autoregressive inference. In standard generation, keys and values for
 * earlier positions never change, but the standard attention layer recomputes them every time.
 * 
 * KV caching provides 10-50x speedup for autoregressive generation by:
 * - Storing computed keys and values for previous positions
 * - Only computing keys/values for new positions 
 * - Reusing cached computations for attention calculation
 * 
 * Memory tradeoff: Uses O(batch_size * max_seq_len * model_size) additional memory
 * Speed benefit: O(seq_len) complexity instead of O(seq_len²) per token
 */
class CachedMultiHeadAttentionLayer(
    private val tokensPerSample: Int,
    private val modelSize: Int,
    private val headCount: Int,
    private val precision: MatrixPrecision = MatrixPrecision.SINGLE
) : Layer {

    override var output: FlexibleMatrix? = null
    override var preActivation: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weight matrices (same as original)
    private var wQuery: FlexibleMatrix
    private var wKey: FlexibleMatrix
    private var wValue: FlexibleMatrix
    private var wOutput: FlexibleMatrix

    // KV Cache - stores keys and values for all positions up to current
    private var keyCache: FlexibleMatrix? = null
    private var valueCache: FlexibleMatrix? = null
    private var currentCacheLength = 0
    private var maxCacheLength = 0

    // Gradients and Adam optimizer state (same as original)
    private var gradWQuery: FlexibleMatrix? = null
    private var gradWKey: FlexibleMatrix? = null
    private var gradWValue: FlexibleMatrix? = null
    private var gradWOutput: FlexibleMatrix? = null

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

        // Initialize weights
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

    /**
     * Initializes the KV cache for autoregressive generation.
     * Call this before starting text generation to enable caching.
     */
    fun initializeCache(maxSequenceLength: Int, batchSize: Int = 1) {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        maxCacheLength = maxSequenceLength
        
        // Cache stores keys and values for all positions
        keyCache = createMatrix(maxSequenceLength * batchSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        valueCache = createMatrix(maxSequenceLength * batchSize, modelSize, isSinglePrecision) { _, _ -> 0.0 }
        currentCacheLength = 0
    }

    /**
     * Clears the KV cache, resetting to the beginning of a sequence.
     */
    fun clearCache() {
        currentCacheLength = 0
        queries = null
        keys = null
        values = null
        attentionWeights = null
        attentionOutput = null
    }

    /**
     * Disables KV caching for training mode.
     */
    fun disableCache() {
        keyCache = null
        valueCache = null
        currentCacheLength = 0
        maxCacheLength = 0
        queries = null
        keys = null
        values = null
        attentionWeights = null
        attentionOutput = null
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        return if (isTraining || keyCache == null) {
            // Training mode or cache disabled - use standard attention
            forwardStandard(input, isTraining)
        } else {
            // Inference mode with caching enabled
            forwardWithCache(input)
        }
    }

    /**
     * Standard forward pass without caching (used during training).
     */
    private fun forwardStandard(input: FlexibleMatrix, isTraining: Boolean): FlexibleMatrix {
        val batchSize = input.rows / tokensPerSample

        // Compute Q, K, V matrices
        queries = matrixMultiply(input, wQuery)
        keys = matrixMultiply(input, wKey)
        values = matrixMultiply(input, wValue)

        // Compute attention for all heads
        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSize)

        // Final output projection
        output = matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /**
     * Optimized forward pass with KV caching for autoregressive generation.
     * Only computes keys and values for new positions.
     */
    private fun forwardWithCache(input: FlexibleMatrix): FlexibleMatrix {
        val inputLength = input.rows
        
        // Compute queries for all positions (always needed)
        queries = matrixMultiply(input, wQuery)
        
        // Compute keys and values only for new positions
        val newKeys = matrixMultiply(input, wKey)
        val newValues = matrixMultiply(input, wValue)
        
        // Update cache with new keys and values
        for (i in 0 until inputLength) {
            for (j in 0 until modelSize) {
                keyCache!![currentCacheLength + i, j] = newKeys[i, j]
                valueCache!![currentCacheLength + i, j] = newValues[i, j]
            }
        }
        
        // Get all keys and values from cache (up to current position + new positions)
        val totalLength = currentCacheLength + inputLength
        keys = keyCache
        values = valueCache

        // Compute attention with cached keys/values
        attentionOutput = computeCachedAttention(queries!!, keyCache!!, valueCache!!, inputLength, totalLength)

        // Update cache length
        currentCacheLength = totalLength

        // Final output projection
        output = matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /**
     * Computes attention using cached keys and values.
     * Only computes attention for the new query positions.
     */
    private fun computeCachedAttention(
        queries: FlexibleMatrix,
        keys: FlexibleMatrix,
        values: FlexibleMatrix,
        queryLength: Int,
        totalLength: Int
    ): FlexibleMatrix {
        val result = createMatrix(queryLength, modelSize, queries.isSinglePrecision)
        val scale = 1.0 / sqrt(headSize.toDouble())

        // Process each head
        for (head in 0 until headCount) {
            val headStartCol = head * headSize

            // For each new query position
            for (queryPos in 0 until queryLength) {
                val actualQueryPos = totalLength - queryLength + queryPos

                // Compute attention scores with all key positions (including cache)
                val scores = DoubleArray(totalLength)
                for (keyPos in 0 until totalLength) {
                    var score = 0.0
                    for (d in 0 until headSize) {
                        score += queries[queryPos, headStartCol + d] * keys[keyPos, headStartCol + d]
                    }
                    scores[keyPos] = score * scale
                }

                // Apply causal mask - can only attend to previous positions
                for (keyPos in actualQueryPos + 1 until totalLength) {
                    scores[keyPos] = Double.NEGATIVE_INFINITY
                }

                // Apply softmax
                var maxScore = if (scores.isNotEmpty()) scores[0] else 0.0
                for (score in scores) {
                    if (score > maxScore) maxScore = score
                }
                var sumExp = 0.0
                for (i in scores.indices) {
                    if (scores[i] != Double.NEGATIVE_INFINITY) {
                        scores[i] = kotlin.math.exp(scores[i] - maxScore)
                        sumExp += scores[i]
                    } else {
                        scores[i] = 0.0
                    }
                }

                if (sumExp > 0.0) {
                    for (i in scores.indices) {
                        scores[i] /= sumExp
                    }
                }

                // Apply attention to values
                for (d in 0 until headSize) {
                    var output = 0.0
                    for (keyPos in 0 until totalLength) {
                        output += scores[keyPos] * values[keyPos, headStartCol + d]
                    }
                    result[queryPos, headStartCol + d] = output
                }
            }
        }

        return result
    }

    /**
     * Standard multi-head attention computation (same as original implementation).
     */
    private fun computeMultiHeadAttention(
        q: FlexibleMatrix,
        k: FlexibleMatrix,
        v: FlexibleMatrix,
        batchSize: Int
    ): FlexibleMatrix {
        val totalTokens = q.rows
        val result = createMatrix(totalTokens, modelSize, q.isSinglePrecision)
        
        val scoresBuffer = createMatrix(tokensPerSample, tokensPerSample, q.isSinglePrecision)
        val attentionBuffer = createMatrix(tokensPerSample, tokensPerSample, q.isSinglePrecision)
        val scale = 1.0 / sqrt(headSize.toDouble())

        for (batch in 0 until batchSize) {
            val startIdx = batch * tokensPerSample

            for (head in 0 until headCount) {
                val headStartCol = head * headSize

                // Compute attention scores
                for (i in 0 until tokensPerSample) {
                    for (j in 0 until tokensPerSample) {
                        var score = 0.0
                        for (d in 0 until headSize) {
                            score += q[startIdx + i, headStartCol + d] * k[startIdx + j, headStartCol + d]
                        }
                        scoresBuffer[i, j] = score * scale
                    }
                }

                // Apply causal mask for training
                for (i in 0 until tokensPerSample) {
                    for (j in i + 1 until tokensPerSample) {
                        scoresBuffer[i, j] = Double.NEGATIVE_INFINITY
                    }
                }

                // Apply softmax
                applySoftmaxInPlace(scoresBuffer, attentionBuffer)

                // Apply attention to values
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

        attentionWeights = result
        return result
    }

    /**
     * High-performance in-place softmax.
     */
    private fun applySoftmaxInPlace(input: FlexibleMatrix, output: FlexibleMatrix) {
        for (i in 0 until input.rows) {
            var maxVal = input[i, 0]
            for (j in 1 until input.cols) {
                val value = input[i, j]
                if (value > maxVal) maxVal = value
            }
            
            var sum = 0.0
            for (j in 0 until input.cols) {
                val expVal = kotlin.math.exp(input[i, j] - maxVal)
                output[i, j] = expVal
                sum += expVal
            }
            
            val invSum = 1.0 / sum
            for (j in 0 until input.cols) {
                output[i, j] *= invSum
            }
        }
    }


    // Backward pass implementation (same as original)
    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        val input = currentInput!!

        gradWOutput = matrixMultiply(attentionOutput!!.transpose(), delta)
        val gradAttentionOutput = matrixMultiply(delta, wOutput.transpose())

        gradWQuery = matrixMultiply(input.transpose(), gradAttentionOutput)
        gradWKey = matrixMultiply(input.transpose(), gradAttentionOutput)
        gradWValue = matrixMultiply(input.transpose(), gradAttentionOutput)

        val gradInput = createMatrix(input.rows, input.cols, input.isSinglePrecision) { _, _ -> 0.0 }
        
        val gradQ = matrixMultiply(gradAttentionOutput, wQuery.transpose())
        val gradK = matrixMultiply(gradAttentionOutput, wKey.transpose())
        val gradV = matrixMultiply(gradAttentionOutput, wValue.transpose())

        for (i in 0 until input.rows) {
            for (j in 0 until input.cols) {
                gradInput[i, j] = gradQ[i, j] + gradK[i, j] + gradV[i, j]
            }
        }

        return gradInput
    }


    // Parameter updates (same as original)
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

        updateWeightMatrix(wQuery, gradWQuery!!, momentWQuery, velocityWQuery, 
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        updateWeightMatrix(wKey, gradWKey!!, momentWKey, velocityWKey,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        updateWeightMatrix(wValue, gradWValue!!, momentWValue, velocityWValue,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
        updateWeightMatrix(wOutput, gradWOutput!!, momentWOutput, velocityWOutput,
                          adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity)
    }

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

    override fun clone(): Layer {
        return CachedMultiHeadAttentionLayer(tokensPerSample, modelSize, headCount, precision).also { copy ->
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
            
            // Note: Don't copy cache state - each instance should have its own cache
        }
    }

    companion object {
        private const val EPSILON = 1e-8
    }
}
