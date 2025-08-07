package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
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
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 */
class BatchNormalizationLayer(
    private val size: Int,
    private val precision: MatrixPrecision = MatrixPrecision.SINGLE
) : Layer, Serializable {

    override var output: FlexibleMatrix? = null
    override var preActivation: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma: FlexibleMatrix
    private var beta: FlexibleMatrix
    private var mean: FlexibleMatrix? = null
    private var variance: FlexibleMatrix? = null
    private var normalized: FlexibleMatrix? = null

    // Added running statistics
    private var runningMean: FlexibleMatrix
    private var runningVariance: FlexibleMatrix
    private val momentum = 0.9 // Common momentum value; adjust as needed

    private var gradGamma: FlexibleMatrix? = null
    private var gradBeta: FlexibleMatrix? = null

    private var momentGamma: FlexibleMatrix
    private var velocityGamma: FlexibleMatrix
    private var momentBeta: FlexibleMatrix
    private var velocityBeta: FlexibleMatrix

    init {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        
        gamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 1.0 }
        beta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        runningMean = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        runningVariance = createMatrix(1, size, isSinglePrecision) { _, _ -> 1.0 }
        momentGamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        velocityGamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        momentBeta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        velocityBeta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
    }

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
            val gradientGamma = gradGamma!![0, j]
            val gradientBeta = gradBeta!![0, j]

            momentGamma[0, j] = adamBeta1 * momentGamma[0, j] + (1 - adamBeta1) * gradientGamma
            velocityGamma[0, j] = adamBeta2 * velocityGamma[0, j] + (1 - adamBeta2) * gradientGamma * gradientGamma
            gamma[0, j] -= learningRate * correctMoment(momentGamma[0, j]) / (sqrt(correctVelocity(velocityGamma[0, j])) + EPSILON)

            momentBeta[0, j] = adamBeta1 * momentBeta[0, j] + (1 - adamBeta1) * gradientBeta
            velocityBeta[0, j] = adamBeta2 * velocityBeta[0, j] + (1 - adamBeta2) * gradientBeta * gradientBeta
            beta[0, j] -= learningRate * correctMoment(momentBeta[0, j]) / (sqrt(correctVelocity(velocityBeta[0, j])) + EPSILON)
        }
    }

    override fun preForward(input: Matrix, isTraining: Boolean): Matrix {
        return preForwardFlexible(input.toFlexibleMatrix(), isTraining).toMatrix()
    }

    private fun preForwardFlexible(input: FlexibleMatrix, isTraining: Boolean): FlexibleMatrix {
        if (isTraining) {
            // Compute batch statistics
            val meanVector = createMatrix(1, size, input.isSinglePrecision) { _, j -> 
                var sum = 0.0
                for (i in 0 until input.rows) {
                    sum += input[i, j]
                }
                sum / input.rows
            }
            val varianceVector = createMatrix(1, size, input.isSinglePrecision) { _, j -> 
                var sum = 0.0
                for (i in 0 until input.rows) {
                    sum += (input[i, j] - meanVector[0, j]).pow(2)
                }
                sum / input.rows
            }

            this.mean = meanVector
            this.variance = varianceVector

            // Normalize using batch statistics
            this.normalized = createMatrix(input.rows, input.cols, input.isSinglePrecision) { i, j ->
                (input[i, j] - meanVector[0, j]) / sqrt(varianceVector[0, j] + EPSILON)
            }

            // Update running statistics (not used for current normalization)
            for (j in 0 until size) {
                runningMean[0, j] = momentum * runningMean[0, j] + (1 - momentum) * meanVector[0, j]
                runningVariance[0, j] = momentum * runningVariance[0, j] + (1 - momentum) * varianceVector[0, j]
            }
        } else {
            // Use running statistics for inference
            this.normalized = createMatrix(input.rows, input.cols, input.isSinglePrecision) { i, j ->
                (input[i, j] - runningMean[0, j]) / sqrt(runningVariance[0, j] + EPSILON)
            }
        }

        // Apply affine transformation
        this.output = createMatrix(input.rows, input.cols, input.isSinglePrecision) { i, j ->
            gamma[0, j] * normalized!![i, j] + beta[0, j]
        }

        return output!!
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        return preForwardFlexible(input, isTraining)
    }

    // Implement forward to use isTraining from the network
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()
    }

    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        checkNotNull(previousLayer) { "BatchNormalization Layer must not be the output layer." }

        // CHANGED:  use delta directly; BN shouldn't apply activation derivative
        val adjustedDelta = delta

        require(adjustedDelta.cols == size) {
            "Batch Normalization layer expects width=$size but got ${adjustedDelta.cols}"
        }

        val inverseStdDev = createMatrix(1, size, adjustedDelta.isSinglePrecision) { _, j ->
            1.0 / sqrt(variance!![0, j] + EPSILON)
        }

        gradGamma = createMatrix(1, size, adjustedDelta.isSinglePrecision) { _, _ -> 0.0 }
        gradBeta = createMatrix(1, size, adjustedDelta.isSinglePrecision) { _, _ -> 0.0 }

        for (j in 0 until size) {
            for (i in 0 until adjustedDelta.rows) {
                gradGamma!![0, j] += adjustedDelta[i, j] * normalized!![i, j]
                gradBeta!![0, j] += adjustedDelta[i, j]
            }
            // CHANGED:  no "/ featureSize" here – keep sums
        }

        return createMatrix(adjustedDelta.rows, size, adjustedDelta.isSinglePrecision) { i, j ->
            val xHat = normalized!![i, j]
            val dY = adjustedDelta[i, j]
            gamma[0, j] * inverseStdDev[0, j] * (featureSize * dY - gradBeta!![0, j] - xHat * gradGamma!![0, j]) / featureSize
        }
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
        return backward(
            currentInput?.toFlexibleMatrix(),
            delta.toFlexibleMatrix(),
            featureSize,
            nextLayer,
            previousLayer,
            lambda
        ).toMatrix()
    }

    override fun clone(): Layer {
        return BatchNormalizationLayer(size, precision).also { copy ->
            copy.gamma = gamma.deepCopy()
            copy.beta = beta.deepCopy()
            copy.mean = mean?.deepCopy()
            copy.variance = variance?.deepCopy()
            copy.normalized = normalized?.deepCopy()
            copy.output = output?.deepCopy()
            copy.runningMean = runningMean.deepCopy()
            copy.runningVariance = runningVariance.deepCopy()
            copy.gradGamma = gradGamma?.deepCopy()
            copy.gradBeta = gradBeta?.deepCopy()
            copy.momentGamma = momentGamma.deepCopy()
            copy.velocityGamma = velocityGamma.deepCopy()
            copy.momentBeta = momentBeta.deepCopy()
            copy.velocityBeta = velocityBeta.deepCopy()
        }
    }

    companion object {
        private const val EPSILON = 1e-8
        private const val serialVersionUID = 1L
    }
}
