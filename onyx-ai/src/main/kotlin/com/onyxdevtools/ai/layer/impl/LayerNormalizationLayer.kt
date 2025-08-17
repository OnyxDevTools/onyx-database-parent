package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Layer Normalization implementation for neural networks, particularly effective in transformer architectures.
 *
 * Layer normalization normalizes inputs across the feature dimension for each sample independently,
 * unlike batch normalization which normalizes across the batch dimension. This makes it particularly
 * suitable for sequence models where batch sizes may vary or be small.
 *
 * **Mathematical Formulation:**
 * Given input x, layer normalization computes:
 * - μ = mean(x) across feature dimension
 * - σ² = variance(x) across feature dimension
 * - x̂ = (x - μ) / √(σ² + ε)
 * - output = γ ⊙ x̂ + β
 *
 * Where γ (gamma) and β (beta) are learnable affine transformation parameters.
 *
 * **Key Benefits:**
 * - **Training stability**: Reduces internal covariate shift
 * - **Batch size independence**: Works with any batch size, including batch size of 1
 * - **Gradient flow**: Improves gradient propagation in deep networks
 * - **Convergence speed**: Often leads to faster training convergence
 *
 * **Usage in Transformers:**
 * Layer normalization is typically applied:
 * - Before or after multi-head attention layers
 * - Before or after feed-forward layers
 * - Often used in residual connections (Add & Norm)
 *
 * This implementation uses the Adam optimizer for updating the learnable parameters γ and β.
 *
 * @param size The number of features/dimensions to normalize. Must match the last dimension
 *             of input tensors passed to this layer.
 * @see Layer
 * @see CachedMultiHeadAttentionLayer
 */
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext

class LayerNormalizationLayer(
    private val size: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
    @kotlin.jvm.Transient
    private var ctx = computeContext

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta = FloatArray(size) { 0.0f }
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null
    private var normalized: Tensor? = null

    private var gradGamma: FloatArray? = null
    private var gradBeta: FloatArray? = null

    private var momentGamma = FloatArray(size) { 0.0f }
    private var velocityGamma = FloatArray(size) { 0.0f }
    private var momentBeta = FloatArray(size) { 0.0f }
    private var velocityBeta = FloatArray(size) { 0.0f }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        require(input.columnSize == size) {
            "LayerNorm size mismatch: input has ${input.columnSize} features, expected $size"
        }
        val batchSize = input.rows
        val cols = size

        // per-row mean
        val xT = ctx.backend.transpose(input)                                  // [cols x rows]
        val sumRows = ctx.backend.sumColumns(xT)                                // length rows
        val meanArr = FloatArray(batchSize) { i -> sumRows[i] / cols }
        val meanCol = ctx.createColVector(meanArr)

        val centered = ctx.backend.subtract(input, meanCol)
        val sq = ctx.backend.elementWiseMultiply(centered, centered)

        // per-row variance
        val varSum = ctx.backend.sumColumns(ctx.backend.transpose(sq))          // length rows
        val varArr = FloatArray(batchSize) { i -> varSum[i] / cols }
        mean = meanArr
        variance = varArr

        // invStd per row (column vector)
        val invStdCol = ctx.backend.applyElementWise(
            ctx.createColVector(FloatArray(batchSize) { i -> varArr[i] + EPSILON })
        ) { v -> 1f / sqrt(v) }

        normalized = ctx.backend.elementWiseMultiply(centered, invStdCol)

        // affine: broadcast gamma/beta row-wise
        val gammaRow = ctx.createRowVector(gamma)
        val betaRow = ctx.createRowVector(beta)
        output = ctx.backend.add(
            ctx.backend.elementWiseMultiply(normalized!!, gammaRow),
            betaRow
        )
        preActivation = output
        return output!!
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val rows = delta.rows
        val cols = size

        // Param grads (per feature)
        gradGamma = ctx.backend.sumColumns(
            ctx.backend.elementWiseMultiply(delta, normalized!!)
        )
        gradBeta = ctx.backend.sumColumns(delta)

        // dYg = delta ⊙ gamma (broadcast gamma row-wise)
        val gammaRow = ctx.createRowVector(gamma)                               // [1 x cols]
        val dYg = ctx.backend.elementWiseMultiply(delta, gammaRow)              // [rows x cols]

        // Row-wise sums: sum over features for each row
        // sumDY[i]   = Σ_c dYg[i,c]
        // sumDYX[i]  = Σ_c dYg[i,c] * xhat[i,c]
        val sumDY = ctx.backend.sumColumns(ctx.backend.transpose(dYg))        // length rows
        val dYgXhat = ctx.backend.elementWiseMultiply(dYg, normalized!!)
        val sumDYX = ctx.backend.sumColumns(ctx.backend.transpose(dYgXhat))    // length rows

        val invWidth = 1f / cols.toFloat()
        val meanDYCol = ctx.createColVector(FloatArray(rows) { r -> sumDY[r] * invWidth }) // [rows x 1]
        val meanDYXCol = ctx.createColVector(FloatArray(rows) { r -> sumDYX[r] * invWidth }) // [rows x 1]

        // dxHat = dYg - mean_row(dYg) - xhat ⊙ mean_row(dYg ⊙ xhat)
        val dxHat = ctx.backend.subtract(
            ctx.backend.subtract(dYg, meanDYCol),
            ctx.backend.elementWiseMultiply(normalized!!, meanDYXCol)
        )

        // invStd per row (rebuild as column vector)
        val invStdCol = ctx.backend.applyElementWise(
            ctx.createColVector(FloatArray(rows) { r -> variance!![r] + EPSILON })
        ) { v -> 1f / sqrt(v) }

        // dX
        return ctx.backend.elementWiseMultiply(dxHat, invStdCol)
    }

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
            gamma[j] =
                gamma[j] - learningRate * correctMoment(momentGamma[j]) / (sqrt(correctVelocity(velocityGamma[j])) + EPSILON)

            momentBeta[j] = adamBeta1 * momentBeta[j] + (1.0f - adamBeta1) * gradientBeta
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1.0f - adamBeta2) * gradientBeta * gradientBeta
            beta[j] =
                beta[j] - learningRate * correctMoment(momentBeta[j]) / (sqrt(correctVelocity(velocityBeta[j])) + EPSILON)
        }
    }

    /**
     * Creates a deep copy of the layer normalization layer.
     */
    override fun clone(): Layer {
        return LayerNormalizationLayer(size, computeContext).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta = beta.copyOf()
            copy.momentGamma = momentGamma.copyOf()
            copy.velocityGamma = velocityGamma.copyOf()
            copy.momentBeta = momentBeta.copyOf()
            copy.velocityBeta = velocityBeta.copyOf()

            copy.mean = mean?.copyOf()
            copy.variance = variance?.copyOf()
            copy.normalized = normalized?.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
            copy.gradGamma = gradGamma?.copyOf()
            copy.gradBeta = gradBeta?.copyOf()
        }
    }

    companion object {
        private const val EPSILON = 1e-8f
    }

    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
        ctx = computeContext
    }
}
