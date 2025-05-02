package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.add
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A layer that adds fixed positional encodings to input embeddings, enabling the model to capture sequence order.
 * This implementation uses sine and cosine functions as in the original Transformer paper.
 *
 * @param tokensPerSample The fixed length of input sequences.
 * @param embeddingSize The dimensionality of the embeddings.
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
        val pe = Array(seqLen) { DoubleArray(embDim) }
        for (pos in 0 until seqLen) {
            for (i in 0 until embDim step 2) {
                val denominator = 10000.0.pow((i / embDim.toDouble()))
                pe[pos][i] = sin(pos / denominator)
                if (i + 1 < embDim) {
                    pe[pos][i + 1] = cos(pos / denominator)
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
        val batchSize = input.size / tokensPerSample
        // Create positional encodings matrix matching input shape
        val posEncodings = Array(batchSize * tokensPerSample) { i ->
            val t = i % tokensPerSample
            positionalEncoding[t].copyOf()
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
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        return delta
    }

    /**
     * No learnable parameters to update.
     */
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
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