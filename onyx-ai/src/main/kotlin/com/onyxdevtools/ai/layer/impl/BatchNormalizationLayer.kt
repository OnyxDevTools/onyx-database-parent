package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
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

    override var output: Matrix? = null
    override var preActivation: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta = FloatArray(size)
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null
    private var normalized: Matrix? = null

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

    override fun preForward(input: Matrix, isTraining: Boolean): Matrix {
        if (isTraining) {
            // Compute batch statistics
            val meanVector = FloatArray(size) { j -> input.sumOf { it[j].toDouble() }.toFloat() / input.size }
            val varianceVector = FloatArray(size) { j -> input.sumOf { (it[j] - meanVector[j]).pow(2).toDouble() }.toFloat() / input.size }

            this.mean = meanVector
            this.variance = varianceVector

            // Normalize using batch statistics
            this.normalized = input.map { row ->
                FloatArray(row.size) { j -> (row[j] - meanVector[j]) / sqrt(varianceVector[j] + EPSILON) }
            }.toTypedArray()

            // Update running statistics (not used for current normalization)
            for (j in 0 until size) {
                runningMean[j] = momentum * runningMean[j] + (1.0f - momentum) * meanVector[j]
                runningVariance[j] = momentum * runningVariance[j] + (1.0f - momentum) * varianceVector[j]
            }
        } else {
            // Use running statistics for inference
            this.normalized = input.map { row ->
                FloatArray(row.size) { j -> (row[j] - runningMean[j]) / sqrt(runningVariance[j] + EPSILON) }
            }.toTypedArray()
        }

        // Apply affine transformation
        this.output = this.normalized!!.map { row ->
            FloatArray(row.size) { j -> gamma[j] * row[j] + beta[j] }
        }.toTypedArray()

        return output!!
    }

    // Implement forward to use isTraining from the network
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return preForward(input, isTraining)
    }

    /**
     * Computes the backward pass of the batch normalization layer.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
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

        return Array(adjustedDelta.size) { i ->
            FloatArray(size) { j ->
                val xHat = normalized!![i][j]
                val dY = adjustedDelta[i][j]
                gamma[j] * inverseStdDev[j] * (featureSize * dY - gradBeta!![j] - xHat * gradGamma!![j]) / featureSize
            }
        }
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
