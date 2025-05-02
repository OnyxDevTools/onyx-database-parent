package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.deepCopy
import com.onyxdevtools.ai.layer.Layer
import java.util.*
import kotlin.math.sqrt

class EmbeddingLayer(
    private val vocabSize: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Matrix? = null
    override var output: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    private val random: Random = Random()

    private var weights: Matrix
    private var momentWeights: Matrix
    private var velocityWeights: Matrix
    private var gradientWeights: Matrix? = null

    init {
        weights = Array(vocabSize) { DoubleArray(embeddingSize) { random.nextGaussian() * 0.02 } }
        // Initialize moment and velocity matrices for Adam optimizer
        momentWeights = Array(vocabSize) { DoubleArray(embeddingSize) { 0.0 } }
        velocityWeights = Array(vocabSize) { DoubleArray(embeddingSize) { 0.0 } }
    }

    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        val batchSize = input.size
        val sequenceLength = input[0].size
        // Convert input token IDs to embeddings and flatten batch and sequence dimensions
        val embeddings = Array(batchSize * sequenceLength) { i ->
            val b = i / sequenceLength
            val t = i % sequenceLength
            val tokenId = input[b][t].toInt()
            weights[tokenId].copyOf() // Lookup embedding vector
        }
        preActivation = embeddings
        output = embeddings
        return output!!
    }

    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        val batchSize = currentInput!!.size
        val sequenceLength = currentInput[0].size
        // Initialize gradient matrix for weights
        gradientWeights = Array(vocabSize) { DoubleArray(embeddingSize) { 0.0 } }
        var deltaIndex = 0
        // Accumulate gradients for each token's embedding
        for (b in 0 until batchSize) {
            for (t in 0 until sequenceLength) {
                val tokenId = currentInput[b][t].toInt()
                for (d in 0 until embeddingSize) {
                    gradientWeights!![tokenId][d] += delta[deltaIndex][d]
                }
                deltaIndex++
            }
        }
        // Return zero matrix for input gradient (no backprop to token IDs)
        return Array(batchSize) { DoubleArray(sequenceLength) { 0.0 } }
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        // Adam optimizer update for embedding weights
        fun correctMoment(m: Double) = m / (1.0 - adamBeta1Power)
        fun correctVelocity(v: Double) = v / (1.0 - adamBeta2Power)
        for (i in 0 until vocabSize) {
            for (j in 0 until embeddingSize) {
                val gradient = gradientWeights!![i][j]
                momentWeights[i][j] = adamBeta1 * momentWeights[i][j] + (1 - adamBeta1) * gradient
                velocityWeights[i][j] = adamBeta2 * velocityWeights[i][j] + (1 - adamBeta2) * gradient * gradient
                weights[i][j] -= learningRate * correctMoment(momentWeights[i][j]) /
                        (sqrt(correctVelocity(velocityWeights[i][j])) + EPSILON)
            }
        }
    }

    override fun clone(): Layer {
        return EmbeddingLayer(vocabSize, embeddingSize).also { copy ->
            copy.weights = weights.deepCopy()
            copy.momentWeights = momentWeights.deepCopy()
            copy.velocityWeights = velocityWeights.deepCopy()
            copy.gradientWeights = gradientWeights?.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
        }
    }

    companion object {
        private const val EPSILON = 1e-8
    }
}