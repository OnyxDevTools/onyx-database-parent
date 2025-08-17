package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class PositionalEncodingLayer(
    private val tokensPerSample: Int,
    private val embeddingSize: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext? = DefaultComputeContext()
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

        // Build positional encoding for each row via backend gatherRows
        val rows = input.size
        val indices = IntArray(rows) { r -> r % effectiveSeqLen }
        val posEnc = computeContext.backend.gatherRows(positionalEncoding, indices)
        preActivation = input
        output = computeContext.backend.add(input, posEnc)
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
    @Suppress("unused")
    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
    }
}
