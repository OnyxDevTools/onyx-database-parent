package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.compute.*
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
    @Transient private var computeContext: ComputeContext? = null
) : Layer {

    // Lazy initialization of compute context
    private val backend: ComputeContext
        get() = computeContext ?: DefaultComputeContext().also { computeContext = it }

    override var output: Matrix? = null
    override var preActivation: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weight matrices (same as original)
    private var wQuery: Matrix
    private var wKey: Matrix
    private var wValue: Matrix
    private var wOutput: Matrix

    // KV Cache - stores keys and values for all positions up to current
    private var keyCache: Matrix? = null
    private var valueCache: Matrix? = null
    private var currentCacheLength = 0
    private var maxCacheLength = 0

    // Gradients and Adam optimizer state (same as original)
    private var gradWQuery: Matrix? = null
    private var gradWKey: Matrix? = null
    private var gradWValue: Matrix? = null
    private var gradWOutput: Matrix? = null

    private var momentWQuery: Matrix
    private var velocityWQuery: Matrix
    private var momentWKey: Matrix
    private var velocityWKey: Matrix
    private var momentWValue: Matrix
    private var velocityWValue: Matrix
    private var momentWOutput: Matrix
    private var velocityWOutput: Matrix

    // Cached values for backward pass
    private var queries: Matrix? = null
    private var keys: Matrix? = null
    private var values: Matrix? = null
    private var attentionWeights: Matrix? = null
    private var attentionOutput: Matrix? = null

    init {
        require(modelSize % headCount == 0) {
            "Model size ($modelSize) must be divisible by head count ($headCount)"
        }

        val random = java.util.Random()
        val scale = 0.02f

        // Initialize weights
        wQuery = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wKey = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wValue = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wOutput = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }

        // Initialize Adam optimizer state
        momentWQuery = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        velocityWQuery = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        momentWKey = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        velocityWKey = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        momentWValue = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        velocityWValue = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        momentWOutput = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
        velocityWOutput = Array(modelSize) { FloatArray(modelSize) { 0.0f } }
    }

    /**
     * Initializes the KV cache for autoregressive generation.
     * Call this before starting text generation to enable caching.
     */
    fun initializeCache(maxSequenceLength: Int, batchSize: Int = 1) {
        maxCacheLength = maxSequenceLength

        // Cache stores keys and values for all positions
        keyCache = Array(maxSequenceLength * batchSize) { FloatArray(modelSize) { 0.0f } }
        valueCache = Array(maxSequenceLength * batchSize) { FloatArray(modelSize) { 0.0f } }
        currentCacheLength = 0
    }

    /**
     * Clears the KV cache, resetting to the beginning of a sequence.
     */
    fun clearCache() {
        currentCacheLength = 0
    }

    /**
     * Disables KV caching for training mode.
     */
    fun disableCache() {
        keyCache = null
        valueCache = null
        currentCacheLength = 0
        maxCacheLength = 0
    }

    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
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
    private fun forwardStandard(input: Matrix, isTraining: Boolean): Matrix {
        val batchSize = input.size / tokensPerSample

        // Compute Q, K, V matrices
        queries = backend.backend.matrixMultiply(input, wQuery)
        keys = backend.backend.matrixMultiply(input, wKey)
        values = backend.backend.matrixMultiply(input, wValue)

        // Compute attention for all heads
        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSize)

        // Final output projection
        output = backend.backend.matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /**
     * Optimized forward pass with KV caching for autoregressive generation.
     * Only computes keys and values for new positions.
     */
    private fun forwardWithCache(input: Matrix): Matrix {
        val inputLength = input.size

        // Check cache bounds
        require(currentCacheLength + inputLength <= maxCacheLength) {
            "Cache overflow: trying to store ${currentCacheLength + inputLength} tokens but max is $maxCacheLength"
        }

        // Compute queries for all positions (always needed)
        queries = backend.backend.matrixMultiply(input, wQuery)

        // Compute keys and values only for new positions
        val newKeys = backend.backend.matrixMultiply(input, wKey)
        val newValues = backend.backend.matrixMultiply(input, wValue)

        // Update cache with new keys and values
        for (i in 0 until inputLength) {
            for (j in 0 until modelSize) {
                keyCache!![currentCacheLength + i][j] = newKeys[i][j]
                valueCache!![currentCacheLength + i][j] = newValues[i][j]
            }
        }

        // Get all keys and values from cache (up to current position + new positions)
        val totalLength = currentCacheLength + inputLength
        keys = Array(totalLength) { r -> FloatArray(modelSize) { c -> keyCache!![r][c] } }
        values = Array(totalLength) { r -> FloatArray(modelSize) { c -> valueCache!![r][c] } }

        // Compute attention with cached keys/values
        attentionOutput = computeCachedAttention(queries!!, keys!!, values!!, inputLength, totalLength)

        // Update cache length
        currentCacheLength = totalLength

        // Final output projection
        output = backend.backend.matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /**
     * Computes attention using cached keys and values.
     * Only computes attention for the new query positions.
     */
    private fun computeCachedAttention(
        queries: Matrix,
        keys: Matrix,
        values: Matrix,
        queryLength: Int,
        totalLength: Int
    ): Matrix {
        val result = Array(queryLength) { FloatArray(modelSize) { 0.0f } }
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
                    var score = 0.0f
                    for (d in 0 until headSize) {
                        score += queries[queryPos][headStartCol + d] * keys[keyPos][headStartCol + d]
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
                    var output = 0.0f
                    for (keyPos in 0 until totalLength) {
                        output += (scores[keyPos] * values[keyPos][headStartCol + d]).toFloat()
                    }
                    result[queryPos][headStartCol + d] = output
                }
            }
        }

        return result
    }

    /**
     * Standard multi-head attention computation (same as original implementation).
     */
    private fun computeMultiHeadAttention(
        q: Matrix,
        k: Matrix,
        v: Matrix,
        batchSize: Int
    ): Matrix {
        val totalTokens = q.size
        val result = Array(totalTokens) { FloatArray(modelSize) { 0.0f } }

        val scoresBuffer = Array(tokensPerSample) { FloatArray(tokensPerSample) { 0.0f } }
        val attentionBuffer = Array(tokensPerSample) { FloatArray(tokensPerSample) { 0.0f } }
        val scale = 1.0 / sqrt(headSize.toDouble())

        for (batch in 0 until batchSize) {
            val startIdx = batch * tokensPerSample

            for (head in 0 until headCount) {
                val headStartCol = head * headSize

                // Compute attention scores
                for (i in 0 until tokensPerSample) {
                    for (j in 0 until tokensPerSample) {
                        var score = 0.0f
                        for (d in 0 until headSize) {
                            score += q[startIdx + i][headStartCol + d] * k[startIdx + j][headStartCol + d]
                        }
                        scoresBuffer[i][j] = (score * scale).toFloat()
                    }
                }

                // Apply causal mask for training
                for (i in 0 until tokensPerSample) {
                    for (j in i + 1 until tokensPerSample) {
                        scoresBuffer[i][j] = Float.NEGATIVE_INFINITY
                    }
                }

                // Apply softmax
                applySoftmaxInPlace(scoresBuffer, attentionBuffer)

                // Apply attention to values
                for (i in 0 until tokensPerSample) {
                    for (d in 0 until headSize) {
                        var output = 0.0f
                        for (j in 0 until tokensPerSample) {
                            output += attentionBuffer[i][j] * v[startIdx + j][headStartCol + d]
                        }
                        result[startIdx + i][headStartCol + d] = output
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
    private fun applySoftmaxInPlace(input: Matrix, output: Matrix) {
        for (i in 0 until input.size) {
            var maxVal = input[i][0]
            for (j in 1 until input[i].size) {
                val value = input[i][j]
                if (value > maxVal) maxVal = value
            }

            var sum = 0.0f
            for (j in 0 until input[i].size) {
                val expVal = kotlin.math.exp(input[i][j] - maxVal)
                output[i][j] = expVal
                sum += expVal
            }

            val invSum = 1.0f / sum
            for (j in 0 until input[i].size) {
                output[i][j] = (output[i][j] * invSum)
            }
        }
    }


    // Backward pass implementation (migrated to use compute backend)
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
        val input = currentInput!!

        gradWOutput = backend.backend.matrixMultiply(backend.backend.transpose(attentionOutput!!), delta)
        val gradAttentionOutput = backend.backend.matrixMultiply(delta, backend.backend.transpose(wOutput))

        gradWQuery = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)
        gradWKey = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)
        gradWValue = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)

        val gradQ = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wQuery))
        val gradK = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wKey))
        val gradV = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wValue))

        // Combine gradients
        val gradInput = Array(input.size) { FloatArray(input[0].size) { 0.0f } }
        for (i in 0 until input.size) {
            for (j in 0 until input[0].size) {
                gradInput[i][j] = gradQ[i][j] + gradK[i][j] + gradV[i][j]
            }
        }

        return gradInput
    }


    // Parameter updates (same as original)
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        fun correctMoment(moment: Float) = moment / (1.0f - adamBeta1Power)
        fun correctVelocity(velocity: Float) = velocity / (1.0f - adamBeta2Power)

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
        weights: Matrix,
        gradients: Matrix,
        moment: Matrix,
        velocity: Matrix,
        beta1: Float,
        beta2: Float,
        learningRate: Float,
        correctMoment: (Float) -> Float,
        correctVelocity: (Float) -> Float
    ) {
        for (i in 0 until weights.size) {
            for (j in 0 until weights[i].size) {
                val gradient = gradients[i][j].toDouble()

                moment[i][j] = (beta1 * moment[i][j] + (1 - beta1) * gradient).toFloat()
                velocity[i][j] = (beta2 * velocity[i][j] + (1 - beta2) * gradient * gradient).toFloat()

                weights[i][j] = (weights[i][j] - learningRate * correctMoment(moment[i][j]) /
                        (sqrt(correctVelocity(velocity[i][j])) + EPSILON))
            }
        }
    }

    override fun clone(): Layer {
        return CachedMultiHeadAttentionLayer(tokensPerSample, modelSize, headCount).also { copy ->
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

}
