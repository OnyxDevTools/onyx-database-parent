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
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext

class BatchNormalizationLayer(
    private val size: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext? = DefaultComputeContext()
) : Layer, Serializable {

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
        val rows = input.rows
        val cols = input.columnSize
        // 1) Batch statistics: mean & variance per column
        val sumCols = computeContext.backend.sumColumns(input)
        val meanArr = FloatArray(cols) { j -> sumCols[j] / rows }
        val meanRow = computeContext.createRowVector(meanArr)
        val centered = computeContext.backend.subtract(input, meanRow)

        val sq = computeContext.backend.elementWiseMultiply(centered, centered)
        val varSum = computeContext.backend.sumColumns(sq)
        val varArr = FloatArray(cols) { j -> varSum[j] / rows }
        val varRow = computeContext.createRowVector(varArr)

        // Update running stats
        if (isTraining) {
            for (j in 0 until cols) {
                runningMean[j]     = momentum * runningMean[j]     + (1 - momentum) * meanArr[j]
                runningVariance[j] = momentum * runningVariance[j] + (1 - momentum) * varArr[j]
            }
        }

        // 2) Normalize (use running stats if not training)
        val normBase = if (isTraining) varRow else computeContext.createRowVector(
            FloatArray(cols) { j -> runningVariance[j] }
        )
        // invStd = 1 / sqrt(var + EPSILON)
        val invStdRow = computeContext.backend.applyElementWise(
            computeContext.backend.add(
                normBase,
                computeContext.createRowVector(FloatArray(cols) { EPSILON })
            ), { v -> 1f / sqrt(v) }
        )
        this.normalized = computeContext.backend.elementWiseMultiply(centered, invStdRow)

        // 3) Affine transform: gamma * x̂ + beta
        val gammaRow = computeContext.createRowVector(gamma)
        val betaRow  = computeContext.createRowVector(beta)
        this.output = computeContext.backend.add(
            computeContext.backend.elementWiseMultiply(normalized!!, gammaRow),
            betaRow
        )
        return output!!
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor = preForward(input, isTraining)

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

        val rows = delta.rows
        val cols = size

        // Parameter gradients: gradGamma = Σ delta * x̂, gradBeta = Σ delta
        val gradGammaArr = computeContext.backend.sumColumns(
            computeContext.backend.elementWiseMultiply(delta, normalized!!)
        )
        val gradBetaArr = computeContext.backend.sumColumns(delta)
        gradGamma = gradGammaArr
        gradBeta = gradBetaArr

        // invStdRow = 1 / sqrt(variance + EPSILON)
        val invStdRow = computeContext.backend.applyElementWise(
            computeContext.createRowVector(
                FloatArray(cols) { j -> variance!![j] + EPSILON }
            ), { v -> 1f / sqrt(v) }
        )

        // dYg = delta * gamma
        val gammaRow = computeContext.createRowVector(gamma)
        val dYg = computeContext.backend.elementWiseMultiply(delta, gammaRow)

        // sumDY = Σ dYg, sumDYX = Σ dYg * x̂
        val sumDY  = computeContext.backend.sumColumns(dYg)
        val sumDYX = computeContext.backend.sumColumns(
            computeContext.backend.elementWiseMultiply(dYg, normalized!!)
        )
        val sumDYRow  = computeContext.createRowVector(sumDY)
        val sumDYXRow = computeContext.createRowVector(sumDYX)

        // dx_hat = (rows * dYg - sumDYRow - x̂ * sumDYXRow) * (1/rows)
        val num = computeContext.backend.subtract(
            computeContext.backend.scalarMultiply(dYg, rows.toFloat()),
            computeContext.backend.add(sumDYRow,
                computeContext.backend.elementWiseMultiply(normalized!!, sumDYXRow)
            )
        )
        val dxHat = computeContext.backend.scalarMultiply(num, 1f / rows)

        // dX = dx_hat * invStdRow
        return computeContext.backend.elementWiseMultiply(dxHat, invStdRow)
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

    @Suppress("unused")
    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
    }
}
