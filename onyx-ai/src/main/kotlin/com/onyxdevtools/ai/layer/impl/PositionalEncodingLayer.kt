package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class PositionalEncodingLayer(
    private val tokensPerSample: Int,
    private val embeddingSize: Int
) : Layer {

    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    // Precomputed positional encoding matrix of shape (tokensPerSample, embeddingSize)
    private val positionalEncoding: Tensor = computePositionalEncoding(tokensPerSample, embeddingSize)

    private fun computePositionalEncoding(seqLen: Int, embDim: Int): Tensor {
        require(seqLen >= 0) { "seqLen must be >= 0" }
        require(embDim > 0) { "embDim must be > 0" }

        val half = (embDim + 1) / 2
        val invFreq = DoubleArray(half) { j ->
            1.0 / 10000.0.pow(2.0 * j / embDim.toDouble())
        }

        return Tensor(seqLen, embDim) { pos, c ->
            val j = c / 2
            val angle = pos * invFreq[j]
            if ((c and 1) == 0) sin(angle).toFloat() else cos(angle).toFloat()
        }
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        if (input.isEmpty()) {
            preActivation = input
            output = input
            return input
        }

        require(input.columnSize == embeddingSize) {
            "embedding dimension mismatch: input=${input.columnSize}, expected=$embeddingSize"
        }

        // effective sequence length per sample (cap at precomputed length)
        val effectiveSeqLen = minOf(input.size, tokensPerSample)
        val width = embeddingSize

        // Build a Tensor with identical shape to input and fill rows by repeating PE rows
        val posEnc = Tensor(input.size, width)
        val src = positionalEncoding.data
        val dst = posEnc.data

        var r = 0
        while (r < input.size) {
            val t = r % effectiveSeqLen                  // position within current sequence
            val srcOff = t * width
            val dstOff = r * width
            System.arraycopy(src, srcOff, dst, dstOff, width)
            r++
        }

        preActivation = input
        output = input.add(posEnc) // element-wise addition (Tensor + Tensor)
        return output!!
    }

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
    ) { /* no-op */ }

    override fun clone(): Layer = PositionalEncodingLayer(tokensPerSample, embeddingSize)
}
