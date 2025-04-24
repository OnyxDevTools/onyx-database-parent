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

    private var gamma = DoubleArray(size) { 1.0 }
    private var beta = DoubleArray(size)
    private var mean: DoubleArray? = null
    private var variance: DoubleArray? = null
    private var normalized: Matrix? = null

    private var gradGamma: DoubleArray? = null
    private var gradBeta: DoubleArray? = null

    private var momentGamma = DoubleArray(size)
    private var velocityGamma = DoubleArray(size)
    private var momentBeta = DoubleArray(size)
    private var velocityBeta = DoubleArray(size)

    /**
     * Updates gamma and beta parameters using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        fun correctMoment(moment: Double) = moment / (1.0 - adamBeta1Power)
        fun correctVelocity(velocity: Double) = velocity / (1.0 - adamBeta2Power)

        for (j in 0 until size) {
            val gradientGamma = gradGamma!![j]
            val gradientBeta = gradBeta!![j]

            momentGamma[j] = adamBeta1 * momentGamma[j] + (1 - adamBeta1) * gradientGamma
            velocityGamma[j] = adamBeta2 * velocityGamma[j] + (1 - adamBeta2) * gradientGamma * gradientGamma
            gamma[j] -= learningRate * correctMoment(momentGamma[j]) / (sqrt(correctVelocity(velocityGamma[j])) + EPSILON)

            momentBeta[j] = adamBeta1 * momentBeta[j] + (1 - adamBeta1) * gradientBeta
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1 - adamBeta2) * gradientBeta * gradientBeta
            beta[j] -= learningRate * correctMoment(momentBeta[j]) / (sqrt(correctVelocity(velocityBeta[j])) + EPSILON)
        }
    }

    /**
     * Normalizes the input using batch statistics and applies learned affine transformation.
     */
    override fun preForward(input: Matrix): Matrix {
        val meanVector = DoubleArray(size) { j -> input.sumOf { it[j] } / input.size }
        val varianceVector = DoubleArray(size) { j -> input.sumOf { (it[j] - meanVector[j]).pow(2) } / input.size }

        this.mean = meanVector
        this.variance = varianceVector

        this.normalized = input.map { row ->
            DoubleArray(row.size) { j -> (row[j] - meanVector[j]) / sqrt(varianceVector[j] + EPSILON) }
        }.toTypedArray()

        this.output = this.normalized!!.map { row ->
            DoubleArray(row.size) { j -> gamma[j] * row[j] + beta[j] }
        }.toTypedArray()

        return output!!
    }

    /**
     * Computes the backward pass of the batch normalization layer.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        checkNotNull(previousLayer) { "BatchNormalization Layer must not be the output layer." }

        // Adjust delta using derivative of the previous layer's activation
        val adjustedDelta = elementWiseMultiply(
            delta,
            applyElementWise(previousLayer.preActivation!!, previousLayer.activation::derivative)
        )

        require(adjustedDelta[0].size == size) {
            "Batch Normalization layer expects width=$size but got ${adjustedDelta[0].size}"
        }

        val inverseStdDev = this.variance!!.map { 1.0 / sqrt(it + EPSILON) }.toDoubleArray()

        this.gradGamma = DoubleArray(size)
        this.gradBeta = DoubleArray(size)

        for (j in 0 until size) {
            for (sampleIndex in adjustedDelta.indices) {
                gradGamma!![j] += adjustedDelta[sampleIndex][j] * this.normalized!![sampleIndex][j]
                gradBeta!![j] += adjustedDelta[sampleIndex][j]
            }
            gradGamma!![j] /= featureSize
            gradBeta!![j] /= featureSize
        }

        // Backprop through normalization formula
        val outputDelta = Array(adjustedDelta.size) { sampleIndex ->
            DoubleArray(size) { j ->
                val xHat = normalized!![sampleIndex][j]
                val dY = adjustedDelta[sampleIndex][j]
                gamma[j] * inverseStdDev[j] * (featureSize * dY - gradBeta!![j] - xHat * gradGamma!![j]) / featureSize
            }
        }

        return outputDelta
    }

    /**
     * Creates a deep copy of the batch normalization layer.
     */
    override fun clone(): Layer {
        return BatchNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta = beta.copyOf()
            copy.mean = mean?.copyOf()
            copy.variance = variance?.copyOf()
            copy.normalized = normalized?.deepCopy()
            copy.output = output?.deepCopy()

            copy.gradGamma = gradGamma?.copyOf()
            copy.gradBeta = gradBeta?.copyOf()
            copy.momentGamma = momentGamma.copyOf()
            copy.velocityGamma = velocityGamma.copyOf()
            copy.momentBeta = momentBeta.copyOf()
            copy.velocityBeta = velocityBeta.copyOf()
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
