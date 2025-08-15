package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt
import java.util.Random

class EmbeddingLayer(
    private val vocabSize: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private val random = Random()

    // Learnable parameters (Tensors)
    private var weights: Tensor              // [vocabSize, embeddingSize]
    private var momentWeights: Tensor        // Adam m
    private var velocityWeights: Tensor      // Adam v
    private var gradientWeights: Tensor? = null

    init {
        // weights ~ N(0, 0.02)
        weights = createTensor(vocabSize, embeddingSize) { _, _ ->
            (random.nextGaussian().toFloat() * 0.02f)
        }
        momentWeights = createTensor(vocabSize, embeddingSize) // zeros
        velocityWeights = createTensor(vocabSize, embeddingSize)
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        // Expect input as [batchSize, sequenceLength] with token ids stored as floats
        val batchSize = input.rows
        val sequenceLength = input.cols

        val outRows = batchSize * sequenceLength
        val out = createTensor(outRows, embeddingSize)

        var i = 0
        var b = 0
        while (b < batchSize) {
            var t = 0
            while (t < sequenceLength) {
                val tokenId = input[b, t].toInt()
                require(tokenId in 0 until vocabSize) {
                    "Token id $tokenId out of range [0, $vocabSize)"
                }
                var d = 0
                while (d < embeddingSize) {
                    out[i, d] = weights[tokenId, d]
                    d++
                }
                i++; t++
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
        val input = currentInput ?: error("EmbeddingLayer.backward: currentInput is null")

        val batchSize = input.rows
        val sequenceLength = input.cols
        require(delta.rows == batchSize * sequenceLength && delta.cols == embeddingSize) {
            "delta shape ${delta.rows}x${delta.cols} incompatible with ${(batchSize * sequenceLength)}x$embeddingSize"
        }

        // Accumulate gradients only for used tokens
        val gW = gradientWeights ?: createTensor(vocabSize, embeddingSize).also { gradientWeights = it }

        var i = 0
        var b = 0
        while (b < batchSize) {
            var t = 0
            while (t < sequenceLength) {
                val tokenId = input[b, t].toInt()
                if (tokenId in 0 until vocabSize) {
                    var d = 0
                    while (d < embeddingSize) {
                        gW[tokenId, d] = gW[tokenId, d] + delta[i, d]
                        d++
                    }
                }
                i++; t++
            }
            b++
        }

        // No gradient to token ids → return zeros with same [batchSize, sequenceLength]
        return createTensor(batchSize, sequenceLength) // zeros
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        val gW = gradientWeights ?: return // nothing to update if no grads accumulated

        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        var i = 0
        while (i < vocabSize) {
            var j = 0
            while (j < embeddingSize) {
                val g = gW[i, j]
                // Adam moments
                momentWeights[i, j]   = adamBeta1 * momentWeights[i, j]   + (1f - adamBeta1) * g
                velocityWeights[i, j] = adamBeta2 * velocityWeights[i, j] + (1f - adamBeta2) * g * g

                // Update weight
                val mHat = correctMoment(momentWeights[i, j])
                val vHat = correctVelocity(velocityWeights[i, j])
                weights[i, j] = weights[i, j] - learningRate * (mHat / (sqrt(vHat) + EPSILON))
                j++
            }
            i++
        }

        // (Optional) zero grads after update
        gradientWeights = createTensor(vocabSize, embeddingSize)
    }

    override fun clone(): Layer {
        return EmbeddingLayer(vocabSize, embeddingSize).also { copy ->
            // Copy learned state
            // (weights/moments/velocities are Tensors, use copy() you added on Tensor)
            copyAs(copy)
        }
    }

    // Helper to copy internal tensors to another instance
    private fun copyAs(dst: EmbeddingLayer) {
        dst.preActivation   = this.preActivation?.copy()
        dst.output          = this.output?.copy()
        dst.weights         = this.weights.copy()
        dst.momentWeights   = this.momentWeights.copy()
        dst.velocityWeights = this.velocityWeights.copy()
        dst.gradientWeights = this.gradientWeights?.copy()
    }

    companion object {
        private const val EPSILON = 1e-8f
    }
}
