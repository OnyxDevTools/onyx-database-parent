@file:Suppress("PrivatePropertyName")

package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt

/**
 * Multi-Head Attention Layer implementation for transformer architectures.
 * 
 * This layer implements the multi-head attention mechanism as described in "Attention Is All You Need".
 * It allows the model to jointly attend to information from different representation subspaces
 * at different positions.
 *
 * @param tokensPerSample Number of tokens in each input sequence
 * @param modelSize The dimensionality of the model (must be divisible by headCount)
 * @param headCount Number of attention heads
 */
@Suppress("LocalVariableName")
class MultiHeadAttentionLayer(
    private val tokensPerSample: Int,
    private val modelSize: Int,
    private val headCount: Int
) : Layer {

    private val random = java.util.Random()
    
    init {
        require(modelSize % headCount == 0) { "modelSize must be divisible by headCount" }
    }

    /** Dimension of each attention head */
    private val dK = modelSize / headCount

    /** Learnable weight matrices for Query, Key, Value, and Output projections */
    private var wQ: Matrix = Array(modelSize) { DoubleArray(modelSize) { random.nextGaussian() * 0.02 } }
    private var wK: Matrix = Array(modelSize) { DoubleArray(modelSize) { random.nextGaussian() * 0.02 } }
    private var wV: Matrix = Array(modelSize) { DoubleArray(modelSize) { random.nextGaussian() * 0.02 } }
    private var wO: Matrix = Array(modelSize) { DoubleArray(modelSize) { random.nextGaussian() * 0.02 } }

    /** Adam optimizer momentum states for weight matrices */
    private var momentWQ: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var velocityWQ: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var momentWK: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var velocityWK: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var momentWV: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var velocityWV: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var momentWO: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }
    private var velocityWO: Matrix = Array(modelSize) { DoubleArray(modelSize) { 0.0 } }

    /** Gradients for weight matrices */
    private var gradWQ: Matrix? = null
    private var gradWK: Matrix? = null
    private var gradWV: Matrix? = null
    private var gradWO: Matrix? = null

    /** Intermediate results cached for backward pass */
    private var Q: Matrix? = null
    private var K: Matrix? = null
    private var V: Matrix? = null
    private var attentionWeights: List<MutableList<Matrix>>? = null
    private var attentionOutputs: Matrix? = null

    override var preActivation: Matrix? = null
    override var output: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    /**
     * Performs forward pass through the multi-head attention layer.
     *
     * Implements the complete multi-head attention computation:
     * 1. Computes Query (Q), Key (K), and Value (V) matrices from input
     * 2. Splits Q, K, V into multiple attention heads
     * 3. Computes scaled dot-product attention for each head
     * 4. Applies causal masking for autoregressive generation
     * 5. Concatenates all attention head outputs
     * 6. Applies final linear projection
     *
     * The attention mechanism follows: Attention(Q,K,V) = softmax(QK^T/√d_k)V
     *
     * @param input Input matrix of shape [batchSize * seqLen, modelSize] containing
     *              the token embeddings or previous layer outputs
     * @param isTraining Whether the layer is in training mode (affects caching behavior)
     * @param nextLayer The next layer in the network (unused in this implementation)
     * @return Output matrix of shape [batchSize * seqLen, modelSize] after applying
     *         multi-head attention and output projection
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        val batchSize = input.size / tokensPerSample
        val seqLen = tokensPerSample

        // Compute Q, K, V for all heads at once
        Q = matrixMultiply(input, wQ)
        K = matrixMultiply(input, wK)
        V = matrixMultiply(input, wV)

        // Initialize storage for attention weights and outputs
        attentionWeights = List(batchSize) { MutableList(headCount) { Array(seqLen) { DoubleArray(seqLen) } } }
        attentionOutputs = Array(batchSize * seqLen) { DoubleArray(modelSize) { 0.0 } }

        // Compute attention for each batch and head
        for (b in 0 until batchSize) {
            for (h in 0 until headCount) {
                val Q_h = Array(seqLen) { i -> DoubleArray(dK) { Q!![b * seqLen + i][h * dK + it] } }
                val K_h = Array(seqLen) { i -> DoubleArray(dK) { K!![b * seqLen + i][h * dK + it] } }
                val V_h = Array(seqLen) { i -> DoubleArray(dK) { V!![b * seqLen + i][h * dK + it] } }

                // Attention scores: Q_h @ K_h^T / sqrt(dK)
                val scores = matrixMultiply(Q_h, transpose(K_h))
                val scaledScores = scalarMultiply(scores, 1.0 / sqrt(dK.toDouble()))

                // Apply causal mask
                val causalMask = Array(seqLen) { row ->
                    DoubleArray(seqLen) { col ->
                        if (col > row) Double.NEGATIVE_INFINITY else 0.0
                    }
                }

                val maskedScores = add(scaledScores, causalMask)

                // Apply softmax to get attention weights
                val attentionWeightsBh = applySoftmax(maskedScores)
                attentionWeights!![b][h] = attentionWeightsBh

                // Attention output: weights @ V_h
                val attentionOutputH = matrixMultiply(attentionWeightsBh, V_h)

                // Store in attentionOutputs
                for (i in 0 until seqLen) {
                    for (j in 0 until dK) {
                        attentionOutputs!![b * seqLen + i][h * dK + j] = attentionOutputH[i][j]
                    }
                }
            }
        }

        // Final linear projection
        val output = matrixMultiply(attentionOutputs!!, wO)
        preActivation = output
        this.output = output
        return output
    }

    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        val X = currentInput!!
        val batchSize = X.size / tokensPerSample
        val seqLen = tokensPerSample

        // Gradient w.r.t. attention outputs
        val gradAttentionOutputs = matrixMultiply(delta, transpose(wO))

        // Initialize gradients for Q, K, V
        val gradQ = Array(X.size) { DoubleArray(modelSize) { 0.0 } }
        val gradK = Array(X.size) { DoubleArray(modelSize) { 0.0 } }
        val gradV = Array(X.size) { DoubleArray(modelSize) { 0.0 } }

        // Compute gradients for each batch and head
        for (b in 0 until batchSize) {
            for (h in 0 until headCount) {
                val attentionWeightsBh = attentionWeights!![b][h]
                val Vh = Array(seqLen) { i -> DoubleArray(dK) { V!![b * seqLen + i][h * dK + it] } }
                val gradAttentionOutputH = Array(seqLen) { i -> DoubleArray(dK) { gradAttentionOutputs[b * seqLen + i][h * dK + it] } }

                val gradAttentionWeightsBh = matrixMultiply(gradAttentionOutputH, transpose(Vh))
                val gradVh = matrixMultiply(transpose(attentionWeightsBh), gradAttentionOutputH)
                val gradScoresBh = softmaxGradient(gradAttentionWeightsBh, attentionWeightsBh)
                val gradQKT = scalarMultiply(gradScoresBh, sqrt(dK.toDouble()))

                val Qh = Array(seqLen) { i -> DoubleArray(dK) { Q!![b * seqLen + i][h * dK + it] } }
                val Kh = Array(seqLen) { i -> DoubleArray(dK) { K!![b * seqLen + i][h * dK + it] } }

                val gradQh = matrixMultiply(gradQKT, Kh)
                val gradKh = matrixMultiply(transpose(gradQKT), Qh)

                // Accumulate gradients
                for (i in 0 until seqLen) {
                    for (j in 0 until dK) {
                        gradQ[b * seqLen + i][h * dK + j] += gradQh[i][j]
                        gradK[b * seqLen + i][h * dK + j] += gradKh[i][j]
                        gradV[b * seqLen + i][h * dK + j] += gradVh[i][j]
                    }
                }
            }
        }

        // Compute weight gradients with regularization
        gradWQ = add(matrixMultiply(transpose(X), gradQ), scalarMultiply(wQ, lambda))
        gradWK = add(matrixMultiply(transpose(X), gradK), scalarMultiply(wK, lambda))
        gradWV = add(matrixMultiply(transpose(X), gradV), scalarMultiply(wV, lambda))
        gradWO = add(matrixMultiply(transpose(attentionOutputs!!), delta), scalarMultiply(wO, lambda))

        // Compute input gradient
        val gradX = add(
            add(
                matrixMultiply(gradQ, transpose(wQ)),
                matrixMultiply(gradK, transpose(wK))
            ),
            matrixMultiply(gradV, transpose(wV))
        )

        return gradX
    }

    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        updateMatrix(wQ, gradWQ!!, momentWQ, velocityWQ, adamBeta1, adamBeta2, adamBeta1Power, adamBeta2Power, learningRate)
        updateMatrix(wK, gradWK!!, momentWK, velocityWK, adamBeta1, adamBeta2, adamBeta1Power, adamBeta2Power, learningRate)
        updateMatrix(wV, gradWV!!, momentWV, velocityWV, adamBeta1, adamBeta2, adamBeta1Power, adamBeta2Power, learningRate)
        updateMatrix(wO, gradWO!!, momentWO, velocityWO, adamBeta1, adamBeta2, adamBeta1Power, adamBeta2Power, learningRate)
    }

    private fun updateMatrix(
        weights: Matrix,
        grad: Matrix,
        moment: Matrix,
        velocity: Matrix,
        beta1: Double,
        beta2: Double,
        beta1Power: Double,
        beta2Power: Double,
        learningRate: Double
    ) {
        for (i in weights.indices) {
            for (j in weights[i].indices) {
                val g = grad[i][j]
                moment[i][j] = beta1 * moment[i][j] + (1 - beta1) * g
                velocity[i][j] = beta2 * velocity[i][j] + (1 - beta2) * g * g
                val mHat = moment[i][j] / (1 - beta1Power)
                val vHat = velocity[i][j] / (1 - beta2Power)
                weights[i][j] -= learningRate * mHat / (sqrt(vHat) + EPSILON)
            }
        }
    }

    override fun clone(): Layer {
        val copy = MultiHeadAttentionLayer(tokensPerSample, modelSize, headCount)
        copy.wQ = wQ.deepCopy()
        copy.wK = wK.deepCopy()
        copy.wV = wV.deepCopy()
        copy.wO = wO.deepCopy()
        copy.momentWQ = momentWQ.deepCopy()
        copy.velocityWQ = velocityWQ.deepCopy()
        copy.momentWK = momentWK.deepCopy()
        copy.velocityWK = velocityWK.deepCopy()
        copy.momentWV = momentWV.deepCopy()
        copy.velocityWV = velocityWV.deepCopy()
        copy.momentWO = momentWO.deepCopy()
        copy.velocityWO = velocityWO.deepCopy()
        return copy
    }

    private fun applySoftmax(matrix: Matrix): Matrix {
        return matrix.map { row ->
            val max = row.maxOrNull() ?: 0.0
            val expRow = row.map { kotlin.math.exp(it - max) }
            val sumExp = expRow.sum()
            expRow.map { it / sumExp }.toDoubleArray()
        }.toTypedArray()
    }

    private fun softmaxGradient(gradOutput: Matrix, output: Matrix): Matrix {
        val result = Array(output.size) { DoubleArray(output[0].size) }
        for (i in output.indices) {
            val y = output[i]
            val gradY = gradOutput[i]
            val dot = y.zip(gradY).sumOf { it.first * it.second }
            for (j in y.indices) {
                result[i][j] = y[j] * (gradY[j] - dot)
            }
        }
        return result
    }

    companion object {
        private const val EPSILON = 1e-8
    }
}
