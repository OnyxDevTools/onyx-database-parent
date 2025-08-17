package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import java.lang.ThreadLocal
import com.onyxdevtools.ai.compute.*
import kotlin.math.sqrt
import kotlin.math.exp

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

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize = modelSize / headCount

    // Weights
    internal var wQuery: Tensor
    internal var wKey: Tensor
    internal var wValue: Tensor
    internal var wOutput: Tensor

    // ==================== Thread-local KV Cache ====================
    private data class KVCache(
        val batchSize: Int,
        val maxCacheLength: Int,
        val kCache: Array<Tensor>,
        val vCache: Array<Tensor>,
        val curLen: IntArray,
        val scratch: FloatArray
    )
    private val threadLocalCache = ThreadLocal<KVCache>()
    private fun currentCache(): KVCache? = threadLocalCache.get()

    /** Initialize per-thread KV cache buffers. */
    fun initializeCache(maxSequenceLength: Int, batchSize: Int = 1) {
        require(maxSequenceLength > 0) { "maxSequenceLength must be > 0" }
        require(batchSize > 0) { "batchSize must be > 0" }
        val kbuf = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        val vbuf = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        val lens = IntArray(batchSize)
        val scratch = FloatArray(maxSequenceLength)
        threadLocalCache.set(KVCache(batchSize, maxSequenceLength, kbuf, vbuf, lens, scratch))
    }

    /** Reset only the per-thread lengths; keep buffers. */
    fun clearCache() {
        currentCache()?.curLen?.fill(0)
    }

    /** Disable per-thread caching entirely. */
    fun disableCache() {
        threadLocalCache.remove()
    }

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
        val random = java.util.Random()
        val scale = 0.02f
        wQuery = Tensor(modelSize, modelSize) { _, _ -> (random.nextGaussian() * scale).toFloat() }
        wKey   = Tensor(modelSize, modelSize) { _, _ -> (random.nextGaussian() * scale).toFloat() }
        wValue = Tensor(modelSize, modelSize) { _, _ -> (random.nextGaussian() * scale).toFloat() }
        wOutput= Tensor(modelSize, modelSize) { _, _ -> (random.nextGaussian() * scale).toFloat() }

        momentWQuery = Tensor(modelSize, modelSize)
        velocityWQuery = Tensor(modelSize, modelSize)
        momentWKey = Tensor(modelSize, modelSize)
        velocityWKey = Tensor(modelSize, modelSize)
        momentWValue = Tensor(modelSize, modelSize)
        velocityWValue = Tensor(modelSize, modelSize)
        momentWOutput = Tensor(modelSize, modelSize)
        velocityWOutput = Tensor(modelSize, modelSize)
    }

    /** Initialize per-sequence cache. Backwards-compatible default for batchSize=1. */

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        val cache = currentCache()
        return if (isTraining || cache == null) {
            forwardStandard(input, isTraining)
        } else {
            forwardWithCache(input, cache)
        }
    }

    /** Standard path for training. */
    private fun forwardStandard(input: Tensor, isTraining: Boolean): Tensor {
        val batchSizeLocal = input.rows / tokensPerSample

        queries = backend.backend.matrixMultiply(input, wQuery)
        keys    = backend.backend.matrixMultiply(input, wKey)
        values  = backend.backend.matrixMultiply(input, wValue)

        attentionOutput = computeMultiHeadAttention(queries!!, keys!!, values!!, batchSizeLocal)
        output = backend.backend.matrixMultiply(attentionOutput!!, wOutput)
        return output!!
    }

    /** Cached inference path: append K/V per sequence and compute attention using per-thread buffers. */
    private fun forwardWithCache(input: Tensor, cache: KVCache): Tensor {
        val B = input.rows
        require(B <= cache.batchSize) { "Input batch $B exceeds initialized cache batch ${cache.batchSize}" }

        // Project batch once
        val Q = backend.backend.matrixMultiply(input, wQuery)
        val K = backend.backend.matrixMultiply(input, wKey)
        val V = backend.backend.matrixMultiply(input, wValue)

        val out = Tensor(B, modelSize)
        for (b in 0 until B) {
            val len = cache.curLen[b]
            require(len + 1 <= cache.maxCacheLength) { "Cache overflow for seq $b: ${len + 1} > ${cache.maxCacheLength}" }

            cache.kCache[b].copyRowFrom(K, b, len)
            cache.vCache[b].copyRowFrom(V, b, len)
            val total = len + 1

            computeCachedAttentionSingle(
                q = Q, qRow = b,
                kSeq = cache.kCache[b],
                vSeq = cache.vCache[b],
                totalLength = total,
                out = out, outRow = b,
                scratch = cache.scratch
            )
            cache.curLen[b] = total
        }

        val Y = backend.backend.matrixMultiply(out, wOutput)
        output = Y
        return Y
    }

    /** Single-sequence cached attention: softmax(q·K) then weighted sum over V, per head. */
    private fun computeCachedAttentionSingle(
        q: Tensor,
        qRow: Int,
        kSeq: Tensor,
        vSeq: Tensor,
        totalLength: Int,
        out: Tensor,
        outRow: Int,
        scratch: FloatArray
    ) {
        out.zeroRow(outRow)

        val scale = (1.0 / sqrt(headSize.toDouble())).toFloat()
        val scores = scratch

        var h = 0
        while (h < headCount) {
            val off = h * headSize

            // scores[t] = (q_h · k_h[t]) * scale
            var maxScore = Float.NEGATIVE_INFINITY
            var t = 0
            while (t < totalLength) {
                val s = q.dotRowSegment(qRow, kSeq, t, off, headSize) * scale
                scores[t] = s
                if (s > maxScore) maxScore = s
                t++
            }

            // softmax over t
            var sumExp = 0.0f
            t = 0
            while (t < totalLength) {
                val e = exp((scores[t] - maxScore).toDouble()).toFloat()
                scores[t] = e
                sumExp += e
                t++
            }
            val inv = 1.0f / (sumExp + 1e-9f)

            // out_h = Σ_t softmax[t] * v_h[t]
            t = 0
            while (t < totalLength) {
                val w = scores[t] * inv
                var d = 0
                while (d < headSize) {
                    out[outRow, off + d] = out[outRow, off + d] + w * vSeq[t, off + d]
                    d++
                }
                t++
            }
            h++
        }
    }

    /** Training-time multi-head attention. Returns Tensor(totalTokens, modelSize). */
    private fun computeMultiHeadAttention(
        q: Tensor,
        k: Tensor,
        v: Tensor,
        batchSize: Int
    ): Tensor {
        val totalTokens = q.rows
        val result = Tensor(totalTokens, modelSize) // zeros

        // Reusable buffers per-head
        val scores = Tensor(tokensPerSample, tokensPerSample) // unnormalized logits
        val attn   = Tensor(tokensPerSample, tokensPerSample) // softmax

        val scale = (1.0 / sqrt(headSize.toDouble())).toFloat()

        var batch = 0
        while (batch < batchSize) {
            val startIdx = batch * tokensPerSample

            var head = 0
            while (head < headCount) {
                val headStartCol = head * headSize

                // scores[i,j] = q[i]·k[j] (within the head)
                var i = 0
                while (i < tokensPerSample) {
                    var j = 0
                    while (j < tokensPerSample) {
                        val s = q.dotRowSegment(
                            row = startIdx + i,
                            other = k,
                            otherRow = startIdx + j,
                            offset = headStartCol,
                            length = headSize
                        )
                        scores[i, j] = s * scale
                        j++
                    }
                    i++
                }

                // causal mask: j > i → -inf
                i = 0
                while (i < tokensPerSample) {
                    var j2 = i + 1
                    while (j2 < tokensPerSample) {
                        scores[i, j2] = Float.NEGATIVE_INFINITY
                        j2++
                    }
                    i++
                }

                // softmax row-wise
                scores.softmaxRowsInto(attn)

                // weighted sum over V per head into result
                i = 0
                while (i < tokensPerSample) {
                    var d = 0
                    while (d < headSize) {
                        var outVal = 0.0f
                        var j3 = 0
                        while (j3 < tokensPerSample) {
                            outVal += attn[i, j3] * v[startIdx + j3, headStartCol + d]
                            j3++
                        }
                        result[startIdx + i, headStartCol + d] = outVal
                        d++
                    }
                    i++
                }

                head++
            }
            batch++
        }

        attentionWeights = attn // (last-head softmax; kept for compatibility)
        attentionOutput = result
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
        val input = currentInput ?: error("currentInput is null in backward()")

        gradWOutput = backend.backend.matrixMultiply(backend.backend.transpose(attentionOutput!!), delta)
        val gradAttentionOutput = backend.backend.matrixMultiply(delta, backend.backend.transpose(wOutput))

        gradWQuery = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)
        gradWKey   = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)
        gradWValue = backend.backend.matrixMultiply(backend.backend.transpose(input), gradAttentionOutput)

        val gradQ = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wQuery))
        val gradK = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wKey))
        val gradV = backend.backend.matrixMultiply(gradAttentionOutput, backend.backend.transpose(wValue))

        val gradInput = Tensor(input.rows, input.cols)
        var i = 0
        while (i < input.rows) {
            var j = 0
            while (j < input.cols) {
                gradInput[i, j] = gradQ[i, j] + gradK[i, j] + gradV[i, j]
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
                val g = gradients[i, j]
                val m = beta1 * moment[i, j] + (1 - beta1) * g
                val v = beta2 * velocity[i, j] + (1 - beta2) * g * g
                moment[i, j] = m
                velocity[i, j] = v
                weights[i, j] = weights[i, j] - learningRate *
                        (correctMoment(m) / (sqrt(correctVelocity(v)) + EPSILON))
                j++
            }
            i++
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
