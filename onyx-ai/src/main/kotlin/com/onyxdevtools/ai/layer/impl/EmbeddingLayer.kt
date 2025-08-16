package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import java.util.Random
import kotlin.math.sqrt

class EmbeddingLayer(
    private val vocabSize: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val random: Random = Random()

    // Learnable parameters and Adam buffers (all as Tensors)
    private var weights: Tensor
    private var momentWeights: Tensor
    private var velocityWeights: Tensor
    private var gradientWeights: Tensor? = null

    init {
        // weights ~ N(0, 0.02)
        weights = Tensor(vocabSize, embeddingSize) { _, _ ->
            (random.nextGaussian().toFloat() * 0.02f)
        }
        // Adam buffers start at 0
        momentWeights = Tensor(vocabSize, embeddingSize) { _, _ -> 0.0f }
        velocityWeights = Tensor(vocabSize, embeddingSize) { _, _ -> 0.0f }
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        // input shape: (batchSize, sequenceLength), containing token IDs as floats
        val batchSize = input.size
        val sequenceLength = input[0].size

        // Flatten (B, T) → (B*T, D)
        val outRows = batchSize * sequenceLength
        val out = Tensor(outRows, embeddingSize)

        val src = weights.data
        val dst = out.data
        val width = embeddingSize

        var i = 0
        var b = 0
        while (b < batchSize) {
            var t = 0
            while (t < sequenceLength) {
                val tokenId = input[b, t].toInt()
                require(tokenId in 0 until vocabSize) {
                    "Token id $tokenId out of range [0, $vocabSize)"
                }
                val srcOff = tokenId * width
                val dstOff = i * width
                System.arraycopy(src, srcOff, dst, dstOff, width)
                i++
                t++
            }
            b++
        }

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
        // currentInput shape: (batchSize, sequenceLength)
        val x = requireNotNull(currentInput) { "currentInput required for EmbeddingLayer.backward" }
        val batchSize = x.size
        val sequenceLength = x[0].size
        val width = embeddingSize

        // Initialize / reset gradients for the embedding matrix
        val gw = Tensor(vocabSize, embeddingSize) { _, _ -> 0.0f }
        val g = gw.data
        val d = delta.data

        var deltaIndex = 0
        var b = 0
        while (b < batchSize) {
            var t = 0
            while (t < sequenceLength) {
                val tokenId = x[b, t].toInt()
                require(tokenId in 0 until vocabSize) {
                    "Token id $tokenId out of range [0, $vocabSize)"
                }
                val gOff = tokenId * width
                val dOff = deltaIndex * width
                var j = 0
                while (j < width) {
                    g[gOff + j] += d[dOff + j]
                    j++
                }
                deltaIndex++
                t++
            }
            b++
        }

        gradientWeights = gw

        // No gradient to token IDs (they're discrete) → return zeros shaped like input
        return Tensor(batchSize, sequenceLength) // zero-initialized
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        val gw = gradientWeights ?: return // nothing to update if no backward yet

        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        val W = weights.data
        val M = momentWeights.data
        val V = velocityWeights.data
        val G = gw.data

        val n = vocabSize * embeddingSize
        var i = 0
        while (i < n) {
            val g = G[i]
            val m = adamBeta1 * M[i] + (1.0f - adamBeta1) * g
            val v = adamBeta2 * V[i] + (1.0f - adamBeta2) * g * g
            M[i] = m
            V[i] = v
            val mHat = correctMoment(m)
            val vHat = correctVelocity(v)
            W[i] = W[i] - learningRate * mHat / (sqrt(vHat) + EPSILON)
            i++
        }
    }

    override fun clone(): Layer {
        return EmbeddingLayer(vocabSize, embeddingSize).also { copy ->
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
    }
}
