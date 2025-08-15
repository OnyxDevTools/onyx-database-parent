package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Positional encoding layer that adds position information to token embeddings.
 */
class PositionalEncodingLayer(
    private val tokensPerSample: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    // Precomputed positional encoding matrix of shape (tokensPerSample, embeddingSize)
    private val positionalEncoding: Tensor = computePositionalEncoding(tokensPerSample, embeddingSize)

    /**
     * Computes positional encoding as a Tensor (seqLen x embDim).
     * For column c: even -> sin, odd -> cos; exponent uses floor(c/2) like the paper (2k / d_model).
     */
    private fun computePositionalEncoding(seqLen: Int, embDim: Int): Tensor {
        return Tensor(seqLen, embDim) { pos, c ->
            // exponent = floor(c/2) * 2 / embDim  == (c - (c % 2)) / embDim
            val base = (c - (c and 1)).toDouble()        // even index for the pair
            val exponent = base / embDim.toDouble()
            val denom = 10000.0.pow(exponent)
            val angle = pos.toDouble() / denom
            if ((c and 1) == 0) sin(angle).toFloat() else cos(angle).toFloat()
        }
    }

    /**
     * Adds positional encodings to the input embeddings.
     * Input shape: (batchSize * sequenceLength, embeddingDim)
     * Output shape: (batchSize * sequenceLength, embeddingDim)
     */
    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        if (input.isEmpty()) {
            preActivation = input
            output = input
            return input
        }

        require(input.cols == embeddingSize) {
            "Input embedding dimension ${input.cols} must equal $embeddingSize"
        }

        // Number of rows in input and PE rows available
        val rows = input.rows
        val cols = input.cols
        val peRows = positionalEncoding.rows  // == tokensPerSample

        // Allocate output like input (keeps heap/metal/gpu kind if your createTensor does that)
        val out = createTensor(rows, cols)

        // Reuse scratch buffers
        val xRow = FloatArray(cols)
        val pRow = FloatArray(cols)

        var r = 0
        while (r < rows) {
            input.readRowInto(r, xRow)
            // use modulo across the sequence length
            val t = r % peRows
            positionalEncoding.readRowInto(t, pRow)

            var c = 0
            while (c < cols) {
                out[r, c] = xRow[c] + pRow[c]
                c++
            }
            r++
        }

        preActivation = input
        output = out
        return out
    }

    /**
     * Gradients pass through unchanged (PE is fixed).
     */
    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor = delta

    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        // No parameters.
    }

    override fun clone(): Layer = PositionalEncodingLayer(tokensPerSample, embeddingSize)
}
