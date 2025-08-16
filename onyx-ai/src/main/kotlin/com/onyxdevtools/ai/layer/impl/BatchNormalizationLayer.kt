package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import java.io.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A layer that performs batch normalization on its input.
 * Useful for accelerating training and stabilizing learning in deep networks.
 *
 * @param size The number of features to normalize.
 */
class BatchNormalizationLayer(private val size: Int) : Layer, Serializable {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta = FloatArray(size)
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null
    private var normalized: Tensor? = null

    // Added running statistics
    private var runningMean = FloatArray(size) { 0.0f }
    private var runningVariance = FloatArray(size) { 1.0f }
    private val momentum = 0.9f // Common momentum value; adjust as needed

    private var gradGamma: FloatArray? = null
    private var gradBeta: FloatArray? = null

    private var momentGamma = FloatArray(size)
    private var velocityGamma = FloatArray(size)
    private var momentBeta = FloatArray(size)
    private var velocityBeta = FloatArray(size)

    /**
     * Updates gamma and beta parameters using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        fun correctMoment(moment: Float) = moment / (1.0f - adamBeta1Power)
        fun correctVelocity(velocity: Float) = velocity / (1.0f - adamBeta2Power)

        for (j in 0 until size) {
            val gradientGamma = gradGamma!![j]
            val gradientBeta = gradBeta!![j]

            momentGamma[j] = adamBeta1 * momentGamma[j] + (1.0f - adamBeta1) * gradientGamma
            velocityGamma[j] = adamBeta2 * velocityGamma[j] + (1.0f - adamBeta2) * gradientGamma * gradientGamma
            gamma[j] = gamma[j] - learningRate * correctMoment(momentGamma[j]) / (sqrt(correctVelocity(velocityGamma[j])) + EPSILON)

            momentBeta[j] = adamBeta1 * momentBeta[j] + (1.0f - adamBeta1) * gradientBeta
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1.0f - adamBeta2) * gradientBeta * gradientBeta
            beta[j] = beta[j] - learningRate * correctMoment(momentBeta[j]) / (sqrt(correctVelocity(velocityBeta[j])) + EPSILON)
        }
    }

    override fun preForward(input: Tensor, isTraining: Boolean): Tensor {
        if (isTraining) {
            // Compute batch statistics
            val meanVector = FloatArray(size) { j -> input.sumOf { it[j].toDouble() }.toFloat() / input.size }
            val varianceVector = FloatArray(size) { j -> input.sumOf { (it[j] - meanVector[j]).pow(2).toDouble() }.toFloat() / input.size }

            this.mean = meanVector
            this.variance = varianceVector

            // Normalize using batch statistics
            this.normalized = Tensor(input.size, input.columnSize) { r, c ->
                (input[r, c] - meanVector[c]) / sqrt(varianceVector[c] + EPSILON)
            }

            // Update running statistics (not used for current normalization)
            for (j in 0 until size) {
                runningMean[j] = momentum * runningMean[j] + (1.0f - momentum) * meanVector[j]
                runningVariance[j] = momentum * runningVariance[j] + (1.0f - momentum) * varianceVector[j]
            }
        } else {
            // Use running statistics for inference
            this.normalized = Tensor(input.size, input.columnSize) { r, c ->
                (input[r, c] - runningMean[c]) / sqrt(runningVariance[c] + EPSILON)
            }
        }

        // Apply affine transformation
        this.output = Tensor(input.size, input.columnSize) { r, c ->
            gamma[c] * this.normalized!![r, c] + beta[c]
        }

        return output!!
    }

    // Implement forward to use isTraining from the network
    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        return preForward(input, isTraining)
    }

    /**
     * Computes the backward pass of the batch normalization layer.
     */
    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        checkNotNull(previousLayer) { "BatchNormalization Layer must not be the output layer." }

        // CHANGED:  use delta directly; BN shouldn’t apply activation derivative
        val adjustedDelta = delta

        require(adjustedDelta[0].size == size) {
            "Batch Normalization layer expects width=$size but got ${adjustedDelta[0].size}"
        }

        val inverseStdDev = variance!!.map { (1.0f / sqrt(it + EPSILON)) }.toFloatArray()

        gradGamma = FloatArray(size)
        gradBeta = FloatArray(size)

        for (j in 0 until size) {
            for (i in adjustedDelta.indices) {
                gradGamma!![j] += adjustedDelta[i][j] * normalized!![i][j]
                gradBeta!![j] += adjustedDelta[i][j]
            }
            // CHANGED:  no “/ featureSize” here – keep sums
        }

        val rows = adjustedDelta.rows
        val cols = size // or adjustedDelta.cols
        val out = Tensor(rows, cols)

        var r = 0
        while (r < rows) {
            val base = r * cols
            var c = 0
            while (c < cols) {
                val xHat = normalized!![r, c]
                val dY   = adjustedDelta[r, c]
                out.data[base + c] =
                    gamma[c] * inverseStdDev[c] *
                            (featureSize * dY - gradBeta!![c] - xHat * gradGamma!![c]) / featureSize
                c++
            }
            r++
        }
        return out
    }

    override fun clone(): Layer {
        return BatchNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta = beta.copyOf()
            copy.mean = mean?.copyOf()
            copy.variance = variance?.copyOf()
            copy.normalized = normalized?.deepCopy()
            copy.output = output?.deepCopy()
            copy.runningMean = runningMean.copyOf()
            copy.runningVariance = runningVariance.copyOf()
            copy.gradGamma = gradGamma?.copyOf()
            copy.gradBeta = gradBeta?.copyOf()
            copy.momentGamma = momentGamma.copyOf()
            copy.velocityGamma = velocityGamma.copyOf()
            copy.momentBeta = momentBeta.copyOf()
            copy.velocityBeta = velocityBeta.copyOf()
        }
    }

    companion object {
        private const val EPSILON = 1e-8f
        private const val serialVersionUID = 1L
    }
}
