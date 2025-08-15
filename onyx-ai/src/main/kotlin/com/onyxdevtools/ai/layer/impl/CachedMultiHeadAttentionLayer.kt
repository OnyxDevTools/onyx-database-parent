package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.compute.*
import kotlin.math.sqrt

/**
 * Optimized Multi-Head Attention with per-sequence KV caching.
 * Training path unchanged. Inference path:
 *  - No copying of whole K/V each step
 *  - Supports batched 1-step input (B rows)
 */
class CachedMultiHeadAttentionLayer(
    private val tokensPerSample: Int,
    private val modelSize: Int,
    private val headCount: Int,
    @Transient private var computeContext: ComputeContext? = null
) : Layer {

    // Lazy init of compute context
    private val backend: ComputeContext
        get() = computeContext ?: DefaultComputeContext().also { computeContext = it }

    override var output: Matrix? = null
    override var preActivation: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weights
    internal var wQuery: Matrix
    internal var wKey: Matrix
    internal var wValue: Matrix
    internal var wOutput: Matrix

    // ======= NEW: per-sequence KV cache (for inference) =======
    private var batchSize: Int = 1
    private var maxCacheLength = 0
    // kCache[b][t][d], vCache[b][t][d]
    private var kCache: Array<Array<FloatArray>>? = null
    private var vCache: Array<Array<FloatArray>>? = null
    private var curLen: IntArray? = null

    // Gradients/optimizer state
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

    // Cached fwd values for training
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
        wQuery = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wKey   = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wValue = Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }
        wOutput= Array(modelSize) { FloatArray(modelSize) { random.nextGaussian().toFloat() * scale } }

        momentWQuery = Array(modelSize) { FloatArray(modelSize) }
        velocityWQuery = Array(modelSize) { FloatArray(modelSize) }
        momentWKey = Array(modelSize) { FloatArray(modelSize) }
        velocityWKey = Array(modelSize) { FloatArray(modelSize) }
        momentWValue = Array(modelSize) { FloatArray(modelSize) }
        velocityWValue = Array(modelSize) { FloatArray(modelSize) }
        momentWOutput = Array(modelSize) { FloatArray(modelSize) }
        velocityWOutput = Array(modelSize) { FloatArray(modelSize) }
    }

    /** Initialize per-sequence cache. Backwards-compatible default for batchSize=1. */
    fun initializeCache(maxSequenceLength: Int, batchSize: Int = 1) {
        require(maxSequenceLength > 0) { "maxSequenceLength must be > 0" }
        require(batchSize > 0) { "batchSize must be > 0" }
        this.maxCacheLength = maxSequenceLength
        this.batchSize = batchSize
        kCache = Array(batchSize) { Array(maxSequenceLength) { FloatArray(modelSize) } }
        vCache = Array(batchSize) { Array(maxSequenceLength) { FloatArray(modelSize) } }
        curLen = IntArray(batchSize) { 0 }
    }

    /** Reset lengths; keep buffers. */
    fun clearCache() {
        curLen?.fill(0)
    }

    /** Disable caches. */
    fun disableCache() {
        kCache = null
        vCache = null
        curLen = null
        maxCacheLength = 0
        batchSize = 1
    }

    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return if (isTraining || kCache == null) {
            forwardStandard(input, isTraining)
        } else {
            forwardWithCache(input) // input is B×modelSize (B can be 1)
        }
    }

    /** Standard path for training (unchanged). */
    private fun forwardStandard(input: Matrix, isTraining: Boolean): Matrix {
        val batchSizeLocal = input.size / tokensPerSample

        queries = backend.backend.matrixMultiply(input, wQuery)
        keys    = backend.backend.matrixMultiply(input, wKey)
        values  = backend.backend.matrixMultiply(input, wValue)

        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSizeLocal)
        output = backend.backend.matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /** Cached inference path: append K/V per sequence and compute attention without rebuilding arrays. */
    private fun forwardWithCache(input: Matrix): Matrix {
        val B = input.size
        val kC = kCache ?: error("Cache not initialized")
        val vC = vCache ?: error("Cache not initialized")
        val lens = curLen ?: error("Cache not initialized")
        require(B <= lens.size) { "Input batch $B exceeds initialized cache batch ${lens.size}" }

        // Project batch once (GEMM with rows=B)
        val Q = backend.backend.matrixMultiply(input, wQuery) // [B, D]
        val K = backend.backend.matrixMultiply(input, wKey)   // [B, D]
        val V = backend.backend.matrixMultiply(input, wValue) // [B, D]

        val out = Array(B) { FloatArray(modelSize) }

        // For each sequence in the batch: append K/V and attend to its own cache
        var b = 0
        while (b < B) {
            val len = lens[b]
            require(len + 1 <= maxCacheLength) { "Cache overflow for seq $b: $len+1 > $maxCacheLength" }

            // Append new K,V (no full-array copies)
            System.arraycopy(K[b], 0, kC[b][len], 0, modelSize)
            System.arraycopy(V[b], 0, vC[b][len], 0, modelSize)
            val total = len + 1

            // Compute attn for this sequence
            computeCachedAttentionSingle(
                qVec = Q[b],
                kSeq = kC[b],
                vSeq = vC[b],
                totalLength = total,
                outVec = out[b]
            )
            lens[b] = total
            b++
        }

        output = backend.backend.matrixMultiply(out, wOutput) // [B, D]
        return output!!
    }

    /** Single-sequence cached attention: softmax(q·K) then weighted sum over V, per head. */
    private fun computeCachedAttentionSingle(
        qVec: FloatArray,
        kSeq: Array<FloatArray>,
        vSeq: Array<FloatArray>,
        totalLength: Int,
        outVec: FloatArray
    ) {
        java.util.Arrays.fill(outVec, 0.0f)
        val scale = (1.0 / sqrt(headSize.toDouble())).toFloat()

        for (h in 0 until headCount) {
            val off = h * headSize

            // scores[t] = (q_h · k_h[t]) * scale
            var maxScore = Float.NEGATIVE_INFINITY
            val scores = FloatArray(totalLength)
            var t = 0
            while (t < totalLength) {
                val kt = kSeq[t]
                var s = 0.0f
                var d = 0
                while (d < headSize) {
                    s += qVec[off + d] * kt[off + d]
                    d++
                }
                s *= scale
                scores[t] = s
                if (s > maxScore) maxScore = s
                t++
            }

            // softmax
            var sumExp = 0.0f
            t = 0
            while (t < totalLength) {
                val e = kotlin.math.exp((scores[t] - maxScore).toDouble()).toFloat()
                scores[t] = e
                sumExp += e
                t++
            }
            val inv = 1.0f / (sumExp + 1e-9f)

            // out_h = Σ_t softmax[t] * v_h[t]
            t = 0
            while (t < totalLength) {
                val w = scores[t] * inv
                val vt = vSeq[t]
                var d = 0
                while (d < headSize) {
                    outVec[off + d] += w * vt[off + d]
                    d++
                }
                t++
            }
        }
    }

    /** Training-time multi-head attention (unchanged) */
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

                // scores
                for (i in 0 until tokensPerSample) {
                    for (j in 0 until tokensPerSample) {
                        var score = 0.0f
                        for (d in 0 until headSize) {
                            score += q[startIdx + i][headStartCol + d] * k[startIdx + j][headStartCol + d]
                        }
                        scoresBuffer[i][j] = (score * scale).toFloat()
                    }
                }

                // causal mask
                for (i in 0 until tokensPerSample) {
                    for (j in i + 1 until tokensPerSample) {
                        scoresBuffer[i][j] = Float.NEGATIVE_INFINITY
                    }
                }

                // softmax
                applySoftmaxInPlace(scoresBuffer, attentionBuffer)

                // weighted sum
                for (i in 0 until tokensPerSample) {
                    for (d in 0 until headSize) {
                        var outVal = 0.0f
                        for (j in 0 until tokensPerSample) {
                            outVal += attentionBuffer[i][j] * v[startIdx + j][headStartCol + d]
                        }
                        result[startIdx + i][headStartCol + d] = outVal
                    }
                }
            }
        }

        attentionWeights = result
        return result
    }

    private fun applySoftmaxInPlace(input: Matrix, output: Matrix) {
        for (i in 0 until input.size) {
            var maxVal = input[i][0]
            for (j in 1 until input[i].size) if (input[i][j] > maxVal) maxVal = input[i][j]
            var sum = 0.0f
            for (j in 0 until input[i].size) {
                val e = kotlin.math.exp((input[i][j] - maxVal).toDouble()).toFloat()
                output[i][j] = e; sum += e
            }
            val inv = 1.0f / (sum + 1e-9f)
            for (j in 0 until input[i].size) output[i][j] *= inv
        }
    }

    // ======= Backward/update (unchanged) =======
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
        gradWKey   = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)
        gradWValue = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)

        val gradQ = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wQuery))
        val gradK = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wKey))
        val gradV = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wValue))

        val gradInput = Array(input.size) { FloatArray(input[0].size) }
        for (i in 0 until input.size) {
            for (j in 0 until input[0].size) {
                gradInput[i][j] = gradQ[i][j] + gradK[i][j] + gradV[i][j]
            }
        }
        return gradInput
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

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
                val g = gradients[i][j]
                moment[i][j]   = beta1 * moment[i][j] + (1 - beta1) * g
                velocity[i][j] = beta2 * velocity[i][j] + (1 - beta2) * g * g
                weights[i][j]  = weights[i][j] - learningRate *
                        correctMoment(moment[i][j]) / (sqrt(correctVelocity(velocity[i][j])) + EPSILON)
            }
        }
    }

    override fun clone(): Layer {
        return CachedMultiHeadAttentionLayer(tokensPerSample, modelSize, headCount).also { copy ->
            copy.wQuery = wQuery.deepCopy()
            copy.wKey   = wKey.deepCopy()
            copy.wValue = wValue.deepCopy()
            copy.wOutput= wOutput.deepCopy()

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
            // cache state intentionally not cloned
        }
    }
}
