package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Positional Encoding implementation for transformer architectures.
 *
 * Positional encoding injects information about the position of tokens in a sequence into their embeddings.
 * This is crucial for transformer models since attention mechanisms are inherently permutation-invariant
 * and don't have built-in notion of sequence order.
 *
 * **Mathematical Formulation:**
 * For position pos and dimension i, the positional encoding is computed as:
 * - PE(pos, 2i) = sin(pos / 10000^(2i/d_model))
 * - PE(pos, 2i+1) = cos(pos / 10000^(2i/d_model))
 *
 * Where d_model is the embedding dimension.
 *
 * **Key Benefits:**
 * - **Fixed patterns**: Uses deterministic sinusoidal functions, no learnable parameters
 * - **Relative positions**: The model can learn to attend by relative positions
 * - **Extrapolation**: Can potentially handle sequences longer than those seen during training
 * - **Unique encoding**: Each position gets a unique encoding pattern
 *
 * **Usage in Transformers:**
 * Positional encodings are typically:
 * - Added to input embeddings at the beginning of the model
 * - Applied before the first transformer block
 * - Combined with token embeddings via element-wise addition
 *
 * This implementation is parameter-free and simply adds pre-computed positional encodings
 * to the input embeddings during the forward pass.
 *
 * @param tokensPerSample The maximum sequence length (number of positions to encode)
 * @param embeddingSize The dimensionality of the embeddings (must match input dimension)
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 * @see Layer
 * @see EmbeddingLayer
 * @see MultiHeadAttentionLayer
 */
class PositionalEncodingLayer(
    private val tokensPerSample: Int,
    private val embeddingSize: Int,
    private val precision: MatrixPrecision = MatrixPrecision.SINGLE
) : Layer {

    override var output: FlexibleMatrix? = null
    override var preActivation: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    // Pre-computed positional encodings (fixed, not learnable)
    private val positionalEncodings: FlexibleMatrix

    init {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        
        // Pre-compute positional encodings using sinusoidal functions
        positionalEncodings = createMatrix(tokensPerSample, embeddingSize, isSinglePrecision) { pos, i ->
            val angle = pos.toDouble() / 10000.0.pow(2.0 * (i / 2) / embeddingSize.toDouble())
            if (i % 2 == 0) {
                sin(angle)
            } else {
                cos(angle)
            }
        }
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        val batchSize = input.rows / tokensPerSample
        
        // Add positional encodings to input embeddings
        output = createMatrix(input.rows, input.cols, input.isSinglePrecision) { row, col ->
            val positionInSequence = row % tokensPerSample
            input[row, col] + positionalEncodings[positionInSequence, col]
        }

        return output!!
    }


    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        // Positional encoding has no learnable parameters, so gradient simply passes through
        return delta
    }


    /**
     * No parameters to update since positional encodings are fixed.
     */
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        // No parameters to update - positional encodings are fixed
    }

    /**
     * Creates a deep copy of the positional encoding layer.
     */
    override fun clone(): Layer {
        return PositionalEncodingLayer(tokensPerSample, embeddingSize, precision).also { copy ->
            copy.output = output?.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
        }
    }
}
