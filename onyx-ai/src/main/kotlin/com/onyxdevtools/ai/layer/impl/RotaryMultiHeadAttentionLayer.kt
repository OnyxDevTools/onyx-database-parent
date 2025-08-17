package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.exp

/**
 * Multi-head attention layer with rotary positional embeddings (RoPE),
 * supporting dynamic context lengths without a fixed window size.
 *
 * Optimizations vs. original:
 * - Precomputed RoPE sin/cos lookup tables (amortized O(1) per use)
 * - In-place RoPE rotation (no temp Tensors)
 * - Fused cached-attention using Δ-angle identity (no per-step rotate/alloc)
 */
class RotaryMultiHeadAttentionLayer(
    private val modelSize: Int,
    private val headCount: Int,
    private val base: Double = 10000.0,
    @Transient private var computeContext: ComputeContext? = null
) : Layer {

    private val backend: ComputeContext
        get() = computeContext ?: DefaultComputeContext().also { computeContext = it }

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val headSize: Int
    private val headPairs: Int
    private val invSqrtHead: Float

    // Projection weights
    private val wQuery: Tensor
    private val wKey: Tensor
    private val wValue: Tensor
    private val wOutput: Tensor

    // Rotary frequency inverses (per pair)
    private val invFreq: FloatArray

    // Per-sequence KV cache
    private var batchSize: Int = 1
    private var maxCacheLength: Int = 0
    private var kCache: Array<Tensor>? = null
    private var vCache: Array<Tensor>? = null
    private var curLen: IntArray? = null

    // Scratch for cached path
    private var scratchScores: FloatArray = FloatArray(0)

    // RoPE lookup tables: [position][pairIndex]
    private var ropeCos: Array<FloatArray> = emptyArray()
    private var ropeSin: Array<FloatArray> = emptyArray()

    init {
        require(modelSize % headCount == 0) { "modelSize must be divisible by headCount" }
        headSize = modelSize / headCount
        require(headSize > 1) { "headSize must be >= 2 for RoPE, but got $headSize" }
        headPairs = headSize / 2
        invSqrtHead = (1.0 / sqrt(headSize.toDouble())).toFloat()

        val rand = java.util.Random()
        val scale = 0.02f
        wQuery = Tensor(modelSize, modelSize) { _, _ -> (rand.nextGaussian() * scale).toFloat() }
        wKey   = Tensor(modelSize, modelSize) { _, _ -> (rand.nextGaussian() * scale).toFloat() }
        wValue = Tensor(modelSize, modelSize) { _, _ -> (rand.nextGaussian() * scale).toFloat() }
        wOutput= Tensor(modelSize, modelSize) { _, _ -> (rand.nextGaussian() * scale).toFloat() }

        invFreq = FloatArray(headPairs) { j -> (1.0 / base.pow(2.0 * j / headSize)).toFloat() }
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        return if (isTraining || kCache == null) {
            forwardStandard(input)
        } else {
            forwardWithCache(input)
        }
    }

    /** Ensure RoPE sin/cos tables up to maxLen (inclusive-exclusive by size). */
    private fun ensureRopeTables(maxLen: Int) {
        if (maxLen <= 0) return
        if (ropeCos.size >= maxLen) return

        val newLen = maxOf(maxLen, ropeCos.size * 2 + 1)
        val cosNew = Array(newLen) { FloatArray(headPairs) }
        val sinNew = Array(newLen) { FloatArray(headPairs) }

        // carry over old rows (shallow ok)
        var i = 0
        while (i < ropeCos.size) {
            cosNew[i] = ropeCos[i]
            sinNew[i] = ropeSin[i]
            i++
        }

        // fill new rows
        var pos = ropeCos.size
        while (pos < newLen) {
            var p = 0
            while (p < headPairs) {
                val theta = pos * invFreq[p]
                cosNew[pos][p] = cos(theta.toDouble()).toFloat()
                sinNew[pos][p] = sin(theta.toDouble()).toFloat()
                p++
            }
            pos++
        }

        ropeCos = cosNew
        ropeSin = sinNew
    }

    private fun ensureScratch(n: Int) {
        if (scratchScores.size < n) scratchScores = FloatArray(n)
    }

    /** In-place RoPE application to tensor x of shape [seqLen, modelSize]. */
    private fun applyRoPEInPlace(x: Tensor, startPos: Int = 0): Tensor {
        val seqLen = x.rows
        if (seqLen == 0) return x
        ensureRopeTables(startPos + seqLen)

        var i = 0
        while (i < seqLen) {
            val pos = startPos + i
            val cosRow = ropeCos[pos]
            val sinRow = ropeSin[pos]
            var h = 0
            while (h < headCount) {
                val base = h * headSize
                var p = 0
                var j = 0
                while (j < headSize) {
                    val a = x[i, base + j]
                    val b = x[i, base + j + 1]
                    val c = cosRow[p]
                    val s = sinRow[p]
                    x[i, base + j    ] = a * c - b * s
                    x[i, base + j + 1] = b * c + a * s
                    j += 2; p++
                }
                h++
            }
            i++
        }
        return x
    }

    private fun computeAttention(q: Tensor, k: Tensor, v: Tensor): Tensor {
        // q,k are already RoPE-rotated in-place by forwardStandard()
        return backend.backend.multiHeadAttentionAllHeads(
            q = q,
            k = k,
            v = v,
            headCount = headCount,
            headSize = headSize,
            causal = true,
            scale = invSqrtHead
        )
    }

    /** Initialize per-sequence cache. Default batchSize=1. */
    fun initializeCache(maxSequenceLength: Int, batchSize: Int = 1) {
        require(maxSequenceLength > 0) { "maxSequenceLength must be > 0" }
        require(batchSize > 0) { "batchSize must be > 0" }
        this.maxCacheLength = maxSequenceLength
        this.batchSize = batchSize
        kCache = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        vCache = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        curLen = IntArray(batchSize) { 0 }
        ensureScratch(maxSequenceLength)
        ensureRopeTables(maxSequenceLength)
    }

    /** Reset per-sequence cache lengths; buffers retained. */
    fun clearCache() { curLen?.fill(0) }

    /** Disable cache and free buffers. */
    fun disableCache() {
        kCache = null
        vCache = null
        curLen = null
        maxCacheLength = 0
        batchSize = 1
        scratchScores = FloatArray(0)
        ropeCos = emptyArray()
        ropeSin = emptyArray()
    }

    private fun forwardStandard(input: Tensor): Tensor {
        if (input.isEmpty()) return input
        require(input.columnSize == modelSize) {
            "Input embedding size ${input.columnSize} does not match modelSize $modelSize"
        }
        preActivation = input

        val Q = backend.backend.matrixMultiply(input, wQuery)
        val K = backend.backend.matrixMultiply(input, wKey)
        val V = backend.backend.matrixMultiply(input, wValue)

        ensureRopeTables(input.rows)
        applyRoPEInPlace(Q, startPos = 0)
        applyRoPEInPlace(K, startPos = 0)

        val attnOut = computeAttention(Q, K, V)
        output = backend.backend.matrixMultiply(attnOut, wOutput)
        return output!!
    }

    private fun forwardWithCache(input: Tensor): Tensor {
        val B = input.rows
        val kC = kCache ?: error("Cache not initialized")
        val vC = vCache ?: error("Cache not initialized")
        val lens = curLen ?: error("Cache not initialized")
        require(B <= lens.size) { "Batch size $B exceeds cache batchSize ${lens.size}" }

        val Q = backend.backend.matrixMultiply(input, wQuery)
        val K = backend.backend.matrixMultiply(input, wKey)
        val V = backend.backend.matrixMultiply(input, wValue)

        val out = Tensor(B, modelSize)

        var b = 0
        while (b < B) {
            val len = lens[b]
            require(len + 1 <= maxCacheLength) { "Cache overflow: $len+1 > $maxCacheLength" }

            // append K/V (unrotated) for position = len
            kC[b].copyRowFrom(K, b, len)
            vC[b].copyRowFrom(V, b, len)

            val total = len + 1
            computeCachedAttentionSingleRoPE(
                qRaw = Q,
                qRow = b,
                kSeq = kC[b],
                vSeq = vC[b],
                totalLength = total,
                out = out,
                outRow = b
            )
            lens[b] = total
            b++
        }

        output = backend.backend.matrixMultiply(out, wOutput)
        return output!!
    }

    /**
     * Fused cached attention using Δ-angle identity:
     * dot( R(θq) q , R(θk) k ) = (q·k) cos(Δ) + (q×k) sin(Δ)
     * where Δ = θq - θk computed from lookup tables.
     */
    private fun computeCachedAttentionSingleRoPE(
        qRaw: Tensor,
        qRow: Int,
        kSeq: Tensor,
        vSeq: Tensor,
        totalLength: Int,
        out: Tensor,
        outRow: Int
    ) {
        out.zeroRow(outRow)
        ensureScratch(totalLength)
        ensureRopeTables(totalLength)

        val scores = scratchScores
        val posQ = totalLength - 1
        val cq = ropeCos[posQ]
        val sq = ropeSin[posQ]

        var h = 0
        while (h < headCount) {
            val off = h * headSize

            // pass 1: compute scores and running max
            var maxScore = Float.NEGATIVE_INFINITY
            var t = 0
            while (t < totalLength) {
                val ck = ropeCos[t]
                val sk = ropeSin[t]

                var dot = 0.0f
                var p = 0
                var j = 0
                while (j < headSize) {
                    val q1 = qRaw[qRow, off + j    ]
                    val q2 = qRaw[qRow, off + j + 1]
                    val k1 = kSeq[t,  off + j    ]
                    val k2 = kSeq[t,  off + j + 1]

                    val even = q1 * k1 + q2 * k2
                    val odd  = q1 * k2 - q2 * k1

                    val cosΔ = (cq[p] * ck[p] + sq[p] * sk[p])
                    val sinΔ = (sq[p] * ck[p] - cq[p] * sk[p])

                    dot += even * cosΔ + odd * sinΔ
                    j += 2; p++
                }

                val s = dot * invSqrtHead
                scores[t] = s
                if (s > maxScore) maxScore = s
                t++
            }

            // pass 2: softmax normalize
            var sumExp = 0.0f
            t = 0
            while (t < totalLength) {
                val e = exp((scores[t] - maxScore).toDouble()).toFloat()
                scores[t] = e
                sumExp += e
                t++
            }
            val invSum = 1.0f / (sumExp + 1e-9f)

            // pass 3: accumulate weighted values
            t = 0
            while (t < totalLength) {
                val w = scores[t] * invSum
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

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor = delta

    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) { /* no-op */ }

    override fun clone(): Layer = RotaryMultiHeadAttentionLayer(modelSize, headCount, base)
}
