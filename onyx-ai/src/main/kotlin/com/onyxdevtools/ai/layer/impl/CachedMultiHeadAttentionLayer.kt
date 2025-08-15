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

    // Precomputed causal mask so masking happens on GPU
    private val causalMask: Tensor = createTensor(tokensPerSample, tokensPerSample) { r, c ->
        if (c > r) -1e9f else 0f
    }
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

        val mask = createTensor(totalLength, totalLength) { r, c -> if (c > r) -1e9f else 0f }

        for (h in 0 until headCount) {
            val off = h * headSize
            val headColsStart = off

            val qHead = createTensor(1, headSize) { _, d -> qVec[headColsStart + d] }
            val kHead = createTensor(totalLength, headSize) { r, d -> kSeq[r][headColsStart + d] }
            val vHead = createTensor(totalLength, headSize) { r, d -> vSeq[r][headColsStart + d] }

            var scores = qHead.multiply(kHead.transpose()).scale(scale)
            scores = scores.add(mask)
            val attn = scores.softmax()
            val headOut = attn.multiply(vHead)

            var d = 0
            while (d < headSize) {
                outVec[off + d] = headOut[0, d]
                d++
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
        val scale = (1.0 / sqrt(headSize.toDouble())).toFloat()

        fun sliceRows(t: Tensor, from: Int, until: Int): Tensor {
            val out = createTensor(until - from, t.cols)
            var r = from
            var dst = 0
            while (r < until) {
                var c = 0
                while (c < t.cols) { out[dst, c] = t[r, c]; c++ }
                r++; dst++
            }
            return out
        }

        fun sliceCols(t: Tensor, from: Int, until: Int): Tensor {
            val out = createTensor(t.rows, until - from)
            var r = 0
            while (r < t.rows) {
                var c = from
                var dst = 0
                while (c < until) { out[r, dst] = t[r, c]; c++; dst++ }
                r++
            }
            return out
        }

        for (batch in 0 until batchSize) {
            val startIdx = batch * tokensPerSample
            val endIdx = startIdx + tokensPerSample
            val qBatch = sliceRows(q, startIdx, endIdx)
            val kBatch = sliceRows(k, startIdx, endIdx)
            val vBatch = sliceRows(v, startIdx, endIdx)

            for (head in 0 until headCount) {
                val headStartCol = head * headSize
                val headEndCol = headStartCol + headSize

                val qHead = sliceCols(qBatch, headStartCol, headEndCol)
                val kHead = sliceCols(kBatch, headStartCol, headEndCol)
                val vHead = sliceCols(vBatch, headStartCol, headEndCol)

                var scores = qHead.multiply(kHead.transpose()).scale(scale)
                scores = scores.add(causalMask)
                val attn = scores.softmax()
                val headResult = attn.multiply(vHead)

                var i = 0
                while (i < tokensPerSample) {
                    var d = 0
                    while (d < headSize) {
                        result[startIdx + i, headStartCol + d] = headResult[i, d]
                        d++
                    }
                    i++
                }
            }
        }

        attentionWeights = result
        return result
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
