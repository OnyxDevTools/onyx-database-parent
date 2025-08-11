package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.deepCopy
import com.onyxdevtools.ai.layer.Layer
import java.util.*
import kotlin.math.sqrt

/**
 * Embedding layer that converts discrete token IDs into dense vector representations.
 *
 * Embedding layers are fundamental components in natural language processing models,
 * transforming sparse one-hot encoded tokens into dense, learnable vector representations.
 * This transformation allows neural networks to learn semantic relationships between
 * tokens in a continuous vector space.
 *
 * **Mathematical Operation:**
 * Given input token IDs, the layer performs a lookup operation:
 * output[i] = weights[tokenId[i]]
 *
 * Where weights is a learnable embedding matrix of shape [vocabSize, embeddingSize].
 *
 * **Key Features:**
 * - **Learnable Representations**: Embedding vectors are learned during training
 * - **Semantic Similarity**: Similar tokens develop similar embedding vectors
 * - **Dimensionality Control**: Configurable embedding dimension for memory/performance trade-offs
 * - **Adam Optimization**: Built-in Adam optimizer support for efficient training
 * - **Gradient Accumulation**: Properly accumulates gradients for repeated tokens
 *
 * **Architecture Usage:**
 * Embedding layers are typically used as:
 * - Input layers in language models (GPT, BERT, etc.)
 * - Word embedding layers in NLP tasks
 * - Token representation in sequence-to-sequence models
 * - First layer in transformer architectures
 *
 * **Training Behavior:**
 * During backpropagation, gradients flow only to the embedding vectors corresponding
 * to the tokens present in the input batch. This sparse gradient update makes
 * training efficient even with large vocabularies.
 *
 * **Memory Considerations:**
 * Memory usage scales with vocabSize × embeddingSize. For large vocabularies,
 * consider techniques like:
 * - Subword tokenization (BPE, WordPiece)
 * - Embedding dimension reduction
 * - Gradient checkpointing
 *
 * @param vocabSize The size of the vocabulary (number of unique tokens).
 *                  Must be greater than the maximum token ID that will be input.
 * @param embeddingSize The dimensionality of the embedding vectors.
 *                     Common values are 128, 256, 512, 768, or 1024.
 * @see Layer
 * @see BPETokenizer
 * @see Vocabulary
 */
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
        weights = Array(vocabSize) { FloatArray(embeddingSize) { random.nextGaussian().toFloat() * 0.02f } }
        // Initialize moment and velocity matrices for Adam optimizer
        momentWeights = Array(vocabSize) { FloatArray(embeddingSize) { 0.0f } }
        velocityWeights = Array(vocabSize) { FloatArray(embeddingSize) { 0.0f } }
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
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
        val batchSize = currentInput!!.size
        val sequenceLength = currentInput[0].size
        // Initialize gradient matrix for weights
        gradientWeights = Array(vocabSize) { FloatArray(embeddingSize) { 0.0f } }
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
        return Array(batchSize) { FloatArray(sequenceLength) { 0.0f } }
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        // Adam optimizer update for embedding weights
        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)
        for (i in 0 until vocabSize) {
            for (j in 0 until embeddingSize) {
                val gradient = gradientWeights!![i][j]
                momentWeights[i][j] = adamBeta1 * momentWeights[i][j] + (1.0f - adamBeta1) * gradient
                velocityWeights[i][j] = adamBeta2 * velocityWeights[i][j] + (1.0f - adamBeta2) * gradient * gradient
                weights[i][j] = weights[i][j] - learningRate * correctMoment(momentWeights[i][j]) /
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
        private const val EPSILON = 1e-8f
    }
}
