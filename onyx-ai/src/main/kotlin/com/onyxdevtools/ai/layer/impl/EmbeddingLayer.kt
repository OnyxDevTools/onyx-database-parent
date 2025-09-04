package dev.onyx.ai.layer.impl

import Activation
import dev.onyx.ai.Tensor
import dev.onyx.ai.layer.Layer
import dev.onyx.ai.compute.ComputeContext
import dev.onyx.ai.compute.DefaultComputeContext
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class EmbeddingLayer(
    private val vocabSize: Int,
    private val embeddingSize: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
    @kotlin.jvm.Transient private var ctx = computeContext

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val random = java.util.Random()

    private var weights: Tensor
    private var momentWeights: Tensor
    private var velocityWeights: Tensor
    private var gradientWeights: Tensor? = null
    // NEW: track which rows were touched this step for sparse updates
    private var touchedIds: IntArray? = null

    init {
        // Glorot-normal-ish for embeddings: std = 1/sqrt(d)
        val std = (1.0 / sqrt(embeddingSize.toDouble())).toFloat()
        weights = Tensor(vocabSize, embeddingSize) { _, _ ->
            (random.nextGaussian().toFloat() * std)
        }
        momentWeights   = Tensor(vocabSize, embeddingSize) { _, _ -> 0f }
        velocityWeights = Tensor(vocabSize, embeddingSize) { _, _ -> 0f }
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        // input: [batchSize, sequenceLength] of token IDs (as floats)
        val batchSize = input.rows
        val sequenceLength = input.columnSize

        val flatIds = IntArray(batchSize * sequenceLength) { idx ->
            val b = idx / sequenceLength
            val t = idx % sequenceLength
            toTokenId(input[b, t]).also { id ->
                require(id in 0 until vocabSize) { "Token id $id out of range [0, $vocabSize)" }
            }
        }

        val out = ctx.backend.gatherRows(weights, flatIds) // [batchSize*sequenceLength, embeddingSize]
        preActivation = out
        output = out
        return out
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val x = requireNotNull(currentInput) { "currentInput required for EmbeddingLayer.backward" }
        val batchSize = x.rows
        val sequenceLength = x.columnSize
        val width = embeddingSize

        // Safety: delta must match forward's gather shape
        require(delta.rows == batchSize * sequenceLength && delta.columnSize == width) {
            "delta shape ${delta.rows}x${delta.columnSize} != expected ${batchSize * sequenceLength}x$width"
        }

        val gw = Tensor(vocabSize, embeddingSize) { _, _ -> 0.0f }
        val G = gw.data
        val D = delta.data
        val W = weights.data

        val touched = BooleanArray(vocabSize)
        var deltaIndex = 0
        val scale = if (featureSize > 1f) 1f / featureSize else 1f

        var b = 0
        while (b < batchSize) {
            var t = 0
            while (t < sequenceLength) {
                val tokenId = toTokenId(x[b, t])
                val baseG = tokenId * width
                val baseD = deltaIndex * width
                // accumulate grad for this token’s row
                var j = 0
                while (j < width) {
                    G[baseG + j] += D[baseD + j] * scale
                    j++
                }
                touched[tokenId] = true
                deltaIndex++
                t++
            }
            b++
        }

        // Optional L2 weight decay (same semantics as your other layers): add lambda * W to grad for touched rows
        if (lambda != 0f) {
            var id = 0
            while (id < vocabSize) {
                if (touched[id]) {
                    val base = id * width
                    var j = 0
                    while (j < width) {
                        G[base + j] += lambda * W[base + j]
                        j++
                    }
                }
                id++
            }
        }

        // pack touched ids for sparse Adam update
        var count = 0
        for (i in 0 until vocabSize) if (touched[i]) count++
        val ids = IntArray(count)
        var k = 0
        for (i in 0 until vocabSize) if (touched[i]) { ids[k++] = i }
        touchedIds = ids

        gradientWeights = gw

        // No gradient through discrete token IDs
        return Tensor(batchSize, sequenceLength) // zeros
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        val gw = gradientWeights ?: return
        val ids = touchedIds

        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        val W = weights.data
        val M = momentWeights.data
        val V = velocityWeights.data
        val G = gw.data
        val width = embeddingSize

        if (ids != null && ids.isNotEmpty()) {
            // Sparse row-wise Adam
            var p = 0
            while (p < ids.size) {
                val row = ids[p]
                val base = row * width
                var j = 0
                while (j < width) {
                    val idx = base + j
                    val g = G[idx]
                    val m = adamBeta1 * M[idx] + (1.0f - adamBeta1) * g
                    val v = adamBeta2 * V[idx] + (1.0f - adamBeta2) * g * g
                    M[idx] = m
                    V[idx] = v
                    W[idx] -= learningRate * (correctMoment(m) / (sqrt(correctVelocity(v)) + EPSILON))
                    j++
                }
                p++
            }
        } else {
            // Dense fallback (first step or if not tracking ids)
            val n = vocabSize * embeddingSize
            var i = 0
            while (i < n) {
                val g = G[i]
                val m = adamBeta1 * M[i] + (1.0f - adamBeta1) * g
                val v = adamBeta2 * V[i] + (1.0f - adamBeta2) * g * g
                M[i] = m
                V[i] = v
                W[i] -= learningRate * (correctMoment(m) / (sqrt(correctVelocity(v)) + EPSILON))
                i++
            }
        }

        // Prevent double application on the next step
        gradientWeights = null
        touchedIds = null
    }

    override fun clone(): Layer {
        return EmbeddingLayer(vocabSize, embeddingSize, computeContext).also { copy ->
            copy.weights = this.weights.deepCopy()
            copy.momentWeights = this.momentWeights.deepCopy()
            copy.velocityWeights = this.velocityWeights.deepCopy()
            copy.gradientWeights = this.gradientWeights?.deepCopy()
            copy.preActivation = this.preActivation?.deepCopy()
            copy.output = this.output?.deepCopy()
        }
    }

    companion object {
        private const val EPSILON = 1e-8f
        private const val serialVersionUID = 1L
    }

    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
        ctx = computeContext
    }

    // ---- helpers ----
    private fun toTokenId(x: Float): Int {
        val r = x.roundToInt()
        require(abs(x - r) < 1e-3f) { "Non-integer token id: $x" }
        return r
    }

    override fun scaleAccumulatedGradients(f: Float) {
        val ids = touchedIds ?: return
        val G = gradientWeights?.data ?: return
        val w = embeddingSize
        var p = 0
        while (p < ids.size) {
            val base = ids[p] * w
            var j = 0; while (j < w) { G[base + j] *= f; j++ }
            p++
        }
    }

}
