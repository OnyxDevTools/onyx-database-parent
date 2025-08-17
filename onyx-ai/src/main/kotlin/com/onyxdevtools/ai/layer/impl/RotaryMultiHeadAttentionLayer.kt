package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import java.lang.ThreadLocal
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
    private var wQuery: Tensor
    private var wKey: Tensor
    private var wValue: Tensor
    private var wOutput: Tensor

    // Rotary frequency inverses (per pair)
    private val invFreq: FloatArray

    // Per-sequence KV cache
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

    // RoPE lookup tables: [position][pairIndex]
    private var ropeCos: Array<FloatArray> = emptyArray()
    private var ropeSin: Array<FloatArray> = emptyArray()

    // ======== Training-time buffers for backward/update ========
    private var queries: Tensor? = null
    private var keys: Tensor? = null
    private var values: Tensor? = null
    private var attentionOutput: Tensor? = null

    // Gradients and optimizer state
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

    init {
        require(modelSize % headCount == 0) { "modelSize must be divisible by headCount" }
        headSize = modelSize / headCount
        require(headSize >= 2 && headSize % 2 == 0) { "headSize must be even and >= 2 for RoPE, got $headSize" }
        headPairs = headSize / 2
        invSqrtHead = (1.0 / sqrt(headSize.toDouble())).toFloat()

        val rand = java.util.Random()
        val d = modelSize
        val std = (1.0 / sqrt(d.toDouble())).toFloat()

        fun randn(std: Float) = (rand.nextGaussian() * std).toFloat()

        wQuery = Tensor(d, d) { _, _ -> randn(std) }
        wKey   = Tensor(d, d) { _, _ -> randn(std) }
        wValue = Tensor(d, d) { _, _ -> randn(std) }
        wOutput= Tensor(d, d) { _, _ -> randn(std) }

        invFreq = FloatArray(headPairs) { j -> (1.0 / base.pow(2.0 * j / headSize)).toFloat() }
        // initialize optimizer state
        momentWQuery = Tensor(modelSize, modelSize)
        velocityWQuery = Tensor(modelSize, modelSize)
        momentWKey = Tensor(modelSize, modelSize)
        velocityWKey = Tensor(modelSize, modelSize)
        momentWValue = Tensor(modelSize, modelSize)
        velocityWValue = Tensor(modelSize, modelSize)
        momentWOutput = Tensor(modelSize, modelSize)
        velocityWOutput = Tensor(modelSize, modelSize)
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        val cache = currentCache()
        return if (isTraining || cache == null) {
            forwardStandard(input)
        } else {
            forwardWithCache(input, cache)
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
        val kbuf = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        val vbuf = Array(batchSize) { Tensor(maxSequenceLength, modelSize) }
        val lens = IntArray(batchSize)
        val scratch = FloatArray(maxSequenceLength)
        threadLocalCache.set(KVCache(batchSize, maxSequenceLength, kbuf, vbuf, lens, scratch))
        ensureRopeTables(maxSequenceLength)
    }

    /** Reset per-thread cache lengths; buffers retained. */
    fun clearCache() { currentCache()?.curLen?.fill(0) }

    /** Disable per-thread cache and free buffers. */
    fun disableCache() {
        threadLocalCache.remove()
        ropeCos = emptyArray()
        ropeSin = emptyArray()
    }

    private fun forwardStandard(input: Tensor): Tensor {
        if (input.isEmpty()) return input
        require(input.columnSize == modelSize) {
            "Input embedding size ${input.columnSize} does not match modelSize $modelSize"
        }
        preActivation = input

        // training buffers
        val Q = backend.backend.matrixMultiply(input, wQuery).also { queries = it }
        val K = backend.backend.matrixMultiply(input, wKey).also { keys = it }
        val V = backend.backend.matrixMultiply(input, wValue).also { values = it }

        ensureRopeTables(input.rows)
        applyRoPEInPlace(Q, startPos = 0)
        applyRoPEInPlace(K, startPos = 0)

        val attnOut = computeAttention(Q, K, V).also { attentionOutput = it }
        output = backend.backend.matrixMultiply(attnOut, wOutput)
        return output!!
    }

    private fun forwardWithCache(input: Tensor, cache: KVCache): Tensor {
        val B = input.rows
        require(B <= cache.batchSize) { "Batch size $B exceeds cache batchSize ${cache.batchSize}" }

        val Q = backend.backend.matrixMultiply(input, wQuery)
        val K = backend.backend.matrixMultiply(input, wKey)
        val V = backend.backend.matrixMultiply(input, wValue)

        val out = Tensor(B, modelSize)

        var b = 0
        while (b < B) {
            val len = cache.curLen[b]
            require(len + 1 <= cache.maxCacheLength) { "Cache overflow: ${len + 1} > ${cache.maxCacheLength}" }

            // append K/V (unrotated) for position = len
            cache.kCache[b].copyRowFrom(K, b, len)
            cache.vCache[b].copyRowFrom(V, b, len)

            val total = len + 1
            computeCachedAttentionSingleRoPE(
                qRaw = Q,
                qRow = b,
                kSeq = cache.kCache[b],
                vSeq = cache.vCache[b],
                totalLength = total,
                out = out,
                outRow = b,
                scratch = cache.scratch
            )
            cache.curLen[b] = total
            b++
        }

        val Y = backend.backend.matrixMultiply(out, wOutput)
        output = Y
        return Y
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
        outRow: Int,
        scratch: FloatArray
    ) {
        out.zeroRow(outRow)
        ensureRopeTables(totalLength)

        val scores = scratch
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

    private fun applyRoPEBackwardInPlace(grad: Tensor, startPos: Int = 0): Tensor {
        val seqLen = grad.rows
        if (seqLen == 0) return grad
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
                    val g1 = grad[i, base + j    ] // d a'
                    val g2 = grad[i, base + j + 1] // d b'
                    val c = cosRow[p]
                    val s = sinRow[p]
                    // [a'; b'] = [ a c - b s; b c + a s ]  =>  d[a;b] = R(θ)^T * d[a';b']
                    val da = g1 * c + g2 * s
                    val db = g2 * c - g1 * s
                    grad[i, base + j    ] = da
                    grad[i, base + j + 1] = db
                    j += 2; p++
                }
                h++
            }
            i++
        }
        return grad
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val X   = currentInput ?: error("currentInput is null in backward()")
        val Qr  = queries ?: error("queries missing for backward()")
        val Kr  = keys    ?: error("keys missing for backward()")
        val V   = values  ?: error("values missing for backward()")
        val O   = attentionOutput ?: error("attentionOutput missing for backward()")

        // 1) dWout and dO
        gradWOutput = backend.backend.matrixMultiply(backend.backend.transpose(O), delta)
        val dO = backend.backend.matrixMultiply(delta, backend.backend.transpose(wOutput))

        // 2) Recompute scores S and softmax A from saved rotated Q/K
        val S = backend.backend.matrixMultiply(Qr, backend.backend.transpose(Kr)) // [T,T]
        // scale and causal mask
        val T = S.rows
        var i = 0
        while (i < T) {
            var j = 0
            while (j < T) {
                var s = S[i, j] * invSqrtHead
                if (j > i) s = Float.NEGATIVE_INFINITY // causal
                S[i, j] = s
                j++
            }
            i++
        }
        // stable softmax row-wise -> A
        val A = Tensor(T, T)
        i = 0
        while (i < T) {
            var maxv = Float.NEGATIVE_INFINITY
            var j2 = 0
            while (j2 < T) { val v = S[i, j2]; if (v > maxv) maxv = v; j2++ }
            var sum = 0.0f
            j2 = 0
            while (j2 < T) { val e = kotlin.math.exp((S[i, j2] - maxv).toDouble()).toFloat(); A[i, j2] = e; sum += e; j2++ }
            val inv = 1.0f / (sum + 1e-9f)
            j2 = 0
            while (j2 < T) { A[i, j2] = A[i, j2] * inv; j2++ }
            i++
        }

        // 3) dV, dA, dS
        val dV = backend.backend.matrixMultiply(backend.backend.transpose(A), dO)         // [T,d]
        val dA = backend.backend.matrixMultiply(dO, backend.backend.transpose(V))         // [T,T]

        val dS = Tensor(T, T)
        i = 0
        while (i < T) {
            // softmax Jacobian: dS_i = (dA_i - (dA_i·A_i) * 1) ⊙ A_i
            var dot = 0.0f
            var j3 = 0
            while (j3 < T) { dot += dA[i, j3] * A[i, j3]; j3++ }
            j3 = 0
            while (j3 < T) {
                var g = (dA[i, j3] - dot) * A[i, j3]
                if (j3 > i) g = 0.0f            // causal mask gradient
                dS[i, j3] = g * invSqrtHead     // unscale here
                j3++
            }
            i++
        }

        // 4) dQ_rot, dK_rot
        val dQ_rot = backend.backend.matrixMultiply(dS, Kr)                 // [T,d]
        val dK_rot = backend.backend.matrixMultiply(backend.backend.transpose(dS), Qr) // [T,d]

        // 5) invert RoPE to raw Q/K grads
        applyRoPEBackwardInPlace(dQ_rot, startPos = 0)
        applyRoPEBackwardInPlace(dK_rot, startPos = 0)
        val dQ_raw = dQ_rot
        val dK_raw = dK_rot
        val dV_raw = dV // V was not rotated

        // 6) weight grads
        gradWQuery = backend.backend.matrixMultiply(backend.backend.transpose(X), dQ_raw)
        gradWKey   = backend.backend.matrixMultiply(backend.backend.transpose(X), dK_raw)
        gradWValue = backend.backend.matrixMultiply(backend.backend.transpose(X), dV_raw)

        // optional mean reduction / L2
        if (featureSize > 1f) {
            val scale = 1.0f / featureSize
            // scale all 4 grad matrices
            var r = 0; while (r < gradWQuery!!.rows) {
                var c = 0; while (c < gradWQuery!!.cols) {
                    gradWQuery!![r,c] *= scale; gradWKey!![r,c] *= scale; gradWValue!![r,c] *= scale; gradWOutput!![r,c] *= scale
                    c++
                }
                r++
            }
        }
        if (lambda != 0f) {
            var r = 0; while (r < wQuery.rows) {
                var c = 0; while (c < wQuery.cols) {
                    gradWQuery!![r,c] += lambda * wQuery[r,c]
                    gradWKey!!  [r,c] += lambda * wKey  [r,c]
                    gradWValue!![r,c] += lambda * wValue[r,c]
                    gradWOutput!![r,c]+= lambda * wOutput[r,c]
                    c++
                }
                r++
            }
        }

        // 7) dX
        val dX_q = backend.backend.matrixMultiply(dQ_raw, backend.backend.transpose(wQuery))
        val dX_k = backend.backend.matrixMultiply(dK_raw, backend.backend.transpose(wKey))
        val dX_v = backend.backend.matrixMultiply(dV_raw, backend.backend.transpose(wValue))

        val gradInput = Tensor(X.rows, X.cols)
        var r = 0
        while (r < gradInput.rows) {
            var c = 0
            while (c < gradInput.cols) {
                gradInput[r,c] = dX_q[r,c] + dX_k[r,c] + dX_v[r,c]
                c++
            }
            r++
        }
        return gradInput
    }

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

        gradWQuery = null
        gradWKey = null
        gradWValue = null
        gradWOutput = null
    }

    /**
     * Adam update step for a weight matrix.
     */
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
        for (i in 0 until weights.rows) {
            for (j in 0 until weights.cols) {
                val g = gradients[i, j]
                val m = beta1 * moment[i, j] + (1 - beta1) * g
                val v = beta2 * velocity[i, j] + (1 - beta2) * g * g
                moment[i, j] = m
                velocity[i, j] = v
                weights[i, j] = weights[i, j] - learningRate *
                        (correctMoment(m) / (sqrt(correctVelocity(v)) + com.onyxdevtools.ai.Constants.EPSILON))
            }
        }
    }

    override fun clone(): Layer {
        return RotaryMultiHeadAttentionLayer(modelSize, headCount, base).also { copy ->
            copy.wQuery = wQuery.deepCopy()
            copy.wKey   = wKey.deepCopy()
            copy.wValue = wValue.deepCopy()
            copy.wOutput= wOutput.deepCopy()

            copy.momentWQuery   = momentWQuery.deepCopy()
            copy.velocityWQuery = velocityWQuery.deepCopy()
            copy.momentWKey     = momentWKey.deepCopy()
            copy.velocityWKey   = velocityWKey.deepCopy()
            copy.momentWValue   = momentWValue.deepCopy()
            copy.velocityWValue = velocityWValue.deepCopy()
            copy.momentWOutput  = momentWOutput.deepCopy()
            copy.velocityWOutput= velocityWOutput.deepCopy()

            copy.preActivation   = preActivation?.deepCopy()
            copy.output          = output?.deepCopy()
            copy.queries         = queries?.deepCopy()
            copy.keys            = keys?.deepCopy()
            copy.values          = values?.deepCopy()
            copy.attentionOutput = attentionOutput?.deepCopy()

            copy.gradWQuery = gradWQuery?.deepCopy()
            copy.gradWKey   = gradWKey?.deepCopy()
            copy.gradWValue = gradWValue?.deepCopy()
            copy.gradWOutput= gradWOutput?.deepCopy()

            copy.ropeCos = Array(ropeCos.size) { i -> ropeCos[i].clone() }
            copy.ropeSin = Array(ropeSin.size) { i -> ropeSin[i].clone() }
            // cache state intentionally not cloned
        }
    }

    override fun scaleAccumulatedGradients(f: Float) {
        gradWQuery  = gradWQuery ?.let { backend.backend.scalarMultiply(it, f) }
        gradWKey    = gradWKey   ?.let { backend.backend.scalarMultiply(it, f) }
        gradWValue  = gradWValue ?.let { backend.backend.scalarMultiply(it, f) }
        gradWOutput = gradWOutput?.let { backend.backend.scalarMultiply(it, f) }
    }
}
