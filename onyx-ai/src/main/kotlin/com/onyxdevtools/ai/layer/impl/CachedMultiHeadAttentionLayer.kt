package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.layer.Layer
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
    private val headCount: Int
) : Layer {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weights (Tensors)
    internal var wQuery: Tensor
    internal var wKey: Tensor
    internal var wValue: Tensor
    internal var wOutput: Tensor

    // ======= per-sequence KV cache (for inference) =======
    private var batchSize: Int = 1
    private var maxCacheLength = 0
    // kCache[b][t][d], vCache[b][t][d]
    private var kCache: Array<Array<FloatArray>>? = null
    private var vCache: Array<Array<FloatArray>>? = null
    private var curLen: IntArray? = null

    // Gradients/optimizer state
    private var gradWQuery: Tensor? = null
    private var gradWKey: Tensor? = null
    private var gradWValue: Tensor? = null
    private var gradWOutput: Tensor? = null

    private var momentWQuery: Tensor
    private var velocityWQuery: Tensor
    private var momentWKey: Tensor
    private var velocityWKey: Tensor
    private var momentWValue: Tensor
    private var velocityWValue: Tensor
    private var momentWOutput: Tensor
    private var velocityWOutput: Tensor

    // Cached fwd values for training
    private var queries: Tensor? = null
    private var keys: Tensor? = null
    private var values: Tensor? = null
    private var attentionWeights: Tensor? = null
    private var attentionOutput: Tensor? = null

    init {
        require(modelSize % headCount == 0) {
            "Model size ($modelSize) must be divisible by head count ($headCount)"
        }
        val rnd = java.util.Random()
        val scale = 0.02f

        // Xavier-ish random init
        wQuery  = createTensor(modelSize, modelSize) { _, _ -> (rnd.nextGaussian().toFloat() * scale) }
        wKey    = createTensor(modelSize, modelSize) { _, _ -> (rnd.nextGaussian().toFloat() * scale) }
        wValue  = createTensor(modelSize, modelSize) { _, _ -> (rnd.nextGaussian().toFloat() * scale) }
        wOutput = createTensor(modelSize, modelSize) { _, _ -> (rnd.nextGaussian().toFloat() * scale) }

        // Moments/velocities start at 0
        momentWQuery  = createTensor(modelSize, modelSize)
        velocityWQuery= createTensor(modelSize, modelSize)
        momentWKey    = createTensor(modelSize, modelSize)
        velocityWKey  = createTensor(modelSize, modelSize)
        momentWValue  = createTensor(modelSize, modelSize)
        velocityWValue= createTensor(modelSize, modelSize)
        momentWOutput = createTensor(modelSize, modelSize)
        velocityWOutput= createTensor(modelSize, modelSize)
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
    fun clearCache() { curLen?.fill(0) }

    /** Disable caches. */
    fun disableCache() {
        kCache = null; vCache = null; curLen = null
        maxCacheLength = 0; batchSize = 1
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        return if (isTraining || kCache == null) {
            forwardStandard(input)
        } else {
            forwardWithCache(input) // input is B×modelSize (B can be 1)
        }
    }

    /** Standard path for training. */
    private fun forwardStandard(input: Tensor): Tensor {
        val batchSizeLocal = input.rows / tokensPerSample

        queries = input.multiply(wQuery)
        keys    = input.multiply(wKey)
        values  = input.multiply(wValue)

        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSizeLocal)
        output = attentionOutput!!.multiply(wOutput)
        return output!!
    }

    /** Cached inference path: append K/V per sequence and compute attention without rebuilding arrays. */
    private fun forwardWithCache(input: Tensor): Tensor {
        val B = input.rows
        val kC = kCache ?: error("Cache not initialized")
        val vC = vCache ?: error("Cache not initialized")
        val lens = curLen ?: error("Cache not initialized")
        require(B <= lens.size) { "Input batch $B exceeds initialized cache batch ${lens.size}" }

        // Project batch once (B×D)
        val Q = input.multiply(wQuery) // [B, D]
        val K = input.multiply(wKey)   // [B, D]
        val V = input.multiply(wValue) // [B, D]

        val out = createTensor(B, modelSize)

        // For each sequence in the batch: append K/V and attend to its own cache
        var b = 0
        while (b < B) {
            val len = lens[b]
            require(len + 1 <= maxCacheLength) { "Cache overflow for seq $b: $len+1 > $maxCacheLength" }

            // Read current projected rows
            val qVec = Q[b].toFloatArray()
            val kVec = K[b].toFloatArray()
            val vVec = V[b].toFloatArray()

            // Append new K,V (no full-array copies)
            System.arraycopy(kVec, 0, kC[b][len], 0, modelSize)
            System.arraycopy(vVec, 0, vC[b][len], 0, modelSize)
            val total = len + 1

            // Compute attn for this sequence
            val outRow = FloatArray(modelSize)
            computeCachedAttentionSingle(
                qVec = qVec,
                kSeq = kC[b],
                vSeq = vC[b],
                totalLength = total,
                outVec = outRow
            )
            // write out row
            var d = 0
            while (d < modelSize) { out[b, d] = outRow[d]; d++ }
            lens[b] = total
            b++
        }

        output = out.multiply(wOutput) // [B, D]
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
                while (d < headSize) { s += qVec[off + d] * kt[off + d]; d++ }
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

    /** Training-time multi-head attention. Returns [totalTokens, modelSize]. */
    private fun computeMultiHeadAttention(
        q: Tensor,
        k: Tensor,
        v: Tensor,
        batchSize: Int
    ): Tensor {
        val totalTokens = q.rows
        val result = createTensor(totalTokens, modelSize)

        val scoresBuffer = Array(tokensPerSample) { FloatArray(tokensPerSample) }
        val attentionBuf = Array(tokensPerSample) { FloatArray(tokensPerSample) }
        val scale = (1.0 / sqrt(headSize.toDouble())).toFloat()

        for (batch in 0 until batchSize) {
            val startIdx = batch * tokensPerSample

            for (head in 0 until headCount) {
                val headStartCol = head * headSize

                // scores
                var i = 0
                while (i < tokensPerSample) {
                    var j = 0
                    while (j < tokensPerSample) {
                        var score = 0.0f
                        var d = 0
                        while (d < headSize) {
                            score += q[startIdx + i][headStartCol + d] * k[startIdx + j][headStartCol + d]
                            d++
                        }
                        scoresBuffer[i][j] = score * scale
                        j++
                    }
                    i++
                }

                // causal mask
                i = 0
                while (i < tokensPerSample) {
                    var j = i + 1
                    while (j < tokensPerSample) { scoresBuffer[i][j] = Float.NEGATIVE_INFINITY; j++ }
                    i++
                }

                // softmax
                applySoftmaxInPlace(scoresBuffer, attentionBuf)

                // weighted sum
                i = 0
                while (i < tokensPerSample) {
                    var d = 0
                    while (d < headSize) {
                        var outVal = 0.0f
                        var j = 0
                        while (j < tokensPerSample) {
                            outVal += attentionBuf[i][j] * v[startIdx + j][headStartCol + d]
                            j++
                        }
                        result[startIdx + i][headStartCol + d] = outVal
                        d++
                    }
                    i++
                }
            }
        }

        attentionWeights = result
        return result
    }

    private fun applySoftmaxInPlace(input: Array<FloatArray>, output: Array<FloatArray>) {
        var i = 0
        while (i < input.size) {
            val row = input[i]
            var maxVal = row[0]
            var j = 1
            while (j < row.size) { if (row[j] > maxVal) maxVal = row[j]; j++ }
            var sum = 0.0f
            j = 0
            while (j < row.size) {
                val e = kotlin.math.exp((row[j] - maxVal).toDouble()).toFloat()
                output[i][j] = e; sum += e
                j++
            }
            val inv = 1.0f / (sum + 1e-9f)
            j = 0
            while (j < row.size) { output[i][j] *= inv; j++ }
            i++
        }
    }

    // ======= Backward/update =======
    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val input = currentInput ?: error("CachedMHA.backward: currentInput is null")

        // dW_out = (attnOut)^T · delta
        gradWOutput = attentionOutput!!.transpose().multiply(delta)
        // d(attnOut) = delta · (W_out)^T
        val gradAttentionOutput = delta.multiply(wOutput.transpose())

        // dW_Q/K/V = X^T · dQ/dK/dV
        gradWQuery = input.transpose().multiply(gradAttentionOutput)
        gradWKey   = input.transpose().multiply(gradAttentionOutput)
        gradWValue = input.transpose().multiply(gradAttentionOutput)

        // dX = dQ·W_Q^T + dK·W_K^T + dV·W_V^T
        val gradQ = gradAttentionOutput.multiply(wQuery.transpose())
        val gradK = gradAttentionOutput.multiply(wKey.transpose())
        val gradV = gradAttentionOutput.multiply(wValue.transpose())

        val rows = input.rows
        val cols = input.cols
        val gradInput = createTensor(rows, cols)
        var i = 0
        while (i < rows) {
            var j = 0
            while (j < cols) {
                gradInput[i, j] = gradQ[i][j] + gradK[i][j] + gradV[i][j]
                j++
            }
            i++
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

        updateWeightMatrix(
            wQuery, gradWQuery!!, momentWQuery, velocityWQuery,
            adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity
        )
        updateWeightMatrix(
            wKey, gradWKey!!, momentWKey, velocityWKey,
            adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity
        )
        updateWeightMatrix(
            wValue, gradWValue!!, momentWValue, velocityWValue,
            adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity
        )
        updateWeightMatrix(
            wOutput, gradWOutput!!, momentWOutput, velocityWOutput,
            adamBeta1, adamBeta2, learningRate, ::correctMoment, ::correctVelocity
        )
    }

    private fun updateWeightMatrix(
        weights: Tensor,
        gradients: Tensor,
        moment: Tensor,
        velocity: Tensor,
        beta1: Float,
        beta2: Float,
        learningRate: Float,
        correctMoment: (Float) -> Float,
        correctVelocity: (Float) -> Float
    ) {
        var i = 0
        while (i < weights.rows) {
            var j = 0
            while (j < weights.cols) {
                val g = gradients[i][j]
                moment[i][j]   = beta1 * moment[i][j] + (1 - beta1) * g
                velocity[i][j] = beta2 * velocity[i][j] + (1 - beta2) * g * g
                weights[i][j]  = weights[i][j] - learningRate *
                        correctMoment(moment[i][j]) / (sqrt(correctVelocity(velocity[i][j])) + EPSILON)
                j++
            }
            i++
        }
    }

    override fun clone(): Layer {
        return CachedMultiHeadAttentionLayer(tokensPerSample, modelSize, headCount).also { copy ->
            copy.wQuery  = wQuery.copy()
            copy.wKey    = wKey.copy()
            copy.wValue  = wValue.copy()
            copy.wOutput = wOutput.copy()

            copy.momentWQuery   = momentWQuery.copy()
            copy.velocityWQuery = velocityWQuery.copy()
            copy.momentWKey     = momentWKey.copy()
            copy.velocityWKey   = velocityWKey.copy()
            copy.momentWValue   = momentWValue.copy()
            copy.velocityWValue = velocityWValue.copy()
            copy.momentWOutput  = momentWOutput.copy()
            copy.velocityWOutput= velocityWOutput.copy()

            copy.output        = output?.copy()
            copy.preActivation = preActivation?.copy()
            copy.queries       = queries?.copy()
            copy.keys          = keys?.copy()
            copy.values        = values?.copy()
            copy.attentionWeights = attentionWeights?.copy()
            copy.attentionOutput  = attentionOutput?.copy()
        }
    }
}
