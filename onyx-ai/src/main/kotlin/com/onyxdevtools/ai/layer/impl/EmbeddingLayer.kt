package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
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
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 * @see Layer
 * @see BPETokenizer
 * @see Vocabulary
 */
class EmbeddingLayer(
    private val vocabSize: Int,
    private val embeddingSize: Int,
    private val precision: MatrixPrecision = MatrixPrecision.SINGLE
) : Layer {

    override var preActivation: FlexibleMatrix? = null
    override var output: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    private val random: Random = Random()

    private var weights: FlexibleMatrix
    private var momentWeights: FlexibleMatrix
    private var velocityWeights: FlexibleMatrix
    private var gradientWeights: FlexibleMatrix? = null

    init {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        
        weights = createMatrix(vocabSize, embeddingSize, isSinglePrecision) { _, _ ->
            random.nextGaussian() * 0.02
        }
        
        // Initialize moment and velocity matrices for Adam optimizer
        momentWeights = createMatrix(vocabSize, embeddingSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWeights = createMatrix(vocabSize, embeddingSize, isSinglePrecision) { _, _ -> 0.0 }
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        val inputMatrix = input.toMatrix()
        val batchSize = inputMatrix.size
        val sequenceLength = inputMatrix[0].size
        
        // Convert input token IDs to embeddings and flatten batch and sequence dimensions
        val embeddings = createMatrix(batchSize * sequenceLength, embeddingSize, weights.isSinglePrecision) { i, j ->
            val b = i / sequenceLength
            val t = i % sequenceLength
            val tokenId = inputMatrix[b][t].toInt()
            weights[tokenId, j] // Lookup embedding vector
        }
        
        preActivation = embeddings
        output = embeddings
        return output!!
    }

    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()
    }

    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        val inputMatrix = currentInput!!.toMatrix()
        val batchSize = inputMatrix.size
        val sequenceLength = inputMatrix[0].size
        
        // Initialize gradient matrix for weights
        gradientWeights = createMatrix(vocabSize, embeddingSize, weights.isSinglePrecision) { _, _ -> 0.0 }
        
        var deltaIndex = 0
        // Accumulate gradients for each token's embedding
        for (b in 0 until batchSize) {
            for (t in 0 until sequenceLength) {
                val tokenId = inputMatrix[b][t].toInt()
                for (d in 0 until embeddingSize) {
                    gradientWeights!![tokenId, d] += delta[deltaIndex, d]
                }
                deltaIndex++
            }
        }
        
        // Return zero matrix for input gradient (no backprop to token IDs)
        return createMatrix(batchSize, sequenceLength, delta.isSinglePrecision) { _, _ -> 0.0 }
    }

    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        return backward(
            currentInput?.toFlexibleMatrix(),
            delta.toFlexibleMatrix(),
            featureSize,
            nextLayer,
            previousLayer,
            lambda
        ).toMatrix()
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
                val gradient = gradientWeights!![i, j]
                momentWeights[i, j] = adamBeta1 * momentWeights[i, j] + (1 - adamBeta1) * gradient
                velocityWeights[i, j] = adamBeta2 * velocityWeights[i, j] + (1 - adamBeta2) * gradient * gradient
                weights[i, j] -= learningRate * correctMoment(momentWeights[i, j]) /
                        (sqrt(correctVelocity(velocityWeights[i, j])) + EPSILON)
            }
        }
    }

    override fun clone(): Layer {
        return EmbeddingLayer(vocabSize, embeddingSize, precision).also { copy ->
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
