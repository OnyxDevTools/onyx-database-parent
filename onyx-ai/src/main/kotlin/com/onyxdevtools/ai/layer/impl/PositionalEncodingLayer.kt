package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.add
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Positional encoding layer that adds position information to token embeddings in transformer architectures.
 *
 * Transformer models lack inherent understanding of sequence order since attention mechanisms
 * are permutation-invariant. This layer addresses this by adding deterministic positional
 * encodings to input embeddings, allowing the model to understand token positions within sequences.
 *
 * **Mathematical Formulation:**
 * Uses sine and cosine functions with different frequencies as described in "Attention Is All You Need":
 * - PE(pos, 2i) = sin(pos / 10000^(2i / d_model))
 * - PE(pos, 2i+1) = cos(pos / 10000^(2i / d_model))
 *
 * Where:
 * - pos = position in the sequence
 * - i = dimension index
 * - d_model = embedding dimension
 *
 * **Key Properties:**
 * - **Deterministic**: Same position always gets the same encoding
 * - **Unique**: Each position has a unique encoding pattern
 * - **Relative Distance**: Model can learn to attend to relative positions
 * - **Extrapolation**: Can handle sequences longer than seen during training
 * - **No Parameters**: Fixed encodings require no training
 *
 * **Advantages over Learned Positional Embeddings:**
 * - Works with variable sequence lengths
 * - Can extrapolate to longer sequences
 * - No additional parameters to learn
 * - Captures smooth positional relationships
 *
 * **Usage in Architecture:**
 * Typically applied immediately after the embedding layer and before
 * the first transformer block in models like GPT, BERT, and T5.
 *
 * @param tokensPerSample The maximum sequence length that will be processed.
 *                       Determines the size of the precomputed encoding matrix.
 * @param embeddingSize The dimensionality of the input embeddings.
 *                     Must match the embedding dimension of input tokens.
 * @see EmbeddingLayer
 * @see MultiHeadAttentionLayer
 */
class PositionalEncodingLayer(
    private val tokensPerSample: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Matrix? = null
    override var output: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    // Precomputed positional encoding matrix of shape (sequenceLength, embeddingDim)
    private val positionalEncoding: Matrix

    init {
        positionalEncoding = computePositionalEncoding(tokensPerSample, embeddingSize)
    }

    /**
     * Computes the positional encoding matrix using sine and cosine functions.
     * For position pos and dimension i:
     * - pe[pos, 2k] = sin(pos / 10000^(2k / embeddingDim))
     * - pe[pos, 2k+1] = cos(pos / 10000^(2k / embeddingDim))
     */
    private fun computePositionalEncoding(seqLen: Int, embDim: Int): Matrix {
        val pe = Array(seqLen) { FloatArray(embDim) }
        for (pos in 0 until seqLen) {
            for (i in 0 until embDim step 2) {
            val denominator = 10000.0.pow((i / embDim.toDouble()))
            pe[pos][i] = sin(pos / denominator).toFloat()
            if (i + 1 < embDim) {
                pe[pos][i + 1] = cos(pos / denominator).toFloat()
            }
            }
        }
        return pe
    }

    /**
     * Adds positional encodings to the input embeddings.
     * Input shape: (batchSize * sequenceLength, embeddingDim)
     * Output shape: (batchSize * sequenceLength, embeddingDim)
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        if (input.isEmpty()) {
            preActivation = input
            output = input
            return output!!
        }
        
        // Handle cases where input size is less than tokensPerSample
        val actualSequenceLength = minOf(input.size, tokensPerSample)
        val batchSize = maxOf(1, input.size / tokensPerSample)
        
        // Create positional encodings matrix matching input shape exactly
        val posEncodings = Array(input.size) { i ->
            val t = i % actualSequenceLength
            if (t < positionalEncoding.size) {
                positionalEncoding[t].copyOf()
            } else {
                FloatArray(embeddingSize) // Zero vector for positions beyond precomputed range
            }
        }
        
        preActivation = input
        output = add(input, posEncodings) // Element-wise addition
        return output!!
    }

    /**
     * Passes the gradient through unchanged, as positional encodings are fixed.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
        return delta
    }

    /**
     * No learnable parameters to update.
     */
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        // No-op
    }

    /**
     * Creates a new instance with the same parameters.
     */
    override fun clone(): Layer {
        return PositionalEncodingLayer(tokensPerSample, embeddingSize)
    }
}
