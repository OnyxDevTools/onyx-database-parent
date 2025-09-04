package dev.onyx.ai.layer.impl

import Activation
import dev.onyx.ai.Tensor
import dev.onyx.ai.layer.Layer
import java.io.Serializable
import kotlin.math.sqrt
import dev.onyx.ai.compute.ComputeContext
import dev.onyx.ai.compute.DefaultComputeContext

class BatchNormalizationLayer(
    private val size: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext? = DefaultComputeContext()
) : Layer, Serializable {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta  = FloatArray(size) { 0.0f }

    // Saved per-batch stats needed for backward (per-feature)
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null

    // Cached normalized activations for backward (same shape as input)
    private var normalized: Tensor? = null

    // Running stats for inference
    private var runningMean     = FloatArray(size) { 0.0f }
    private var runningVariance = FloatArray(size) { 1.0f }
    private val momentum = 0.9f

    // Accumulated parameter gradients (Option A)
    private var gradGamma: FloatArray? = null
    private var gradBeta : FloatArray? = null

    // Adam state for params
    private var momentGamma   = FloatArray(size)
    private var velocityGamma = FloatArray(size)
    private var momentBeta    = FloatArray(size)
    private var velocityBeta  = FloatArray(size)

    override fun preForward(input: Tensor, isTraining: Boolean): Tensor {
        val ctx = computeContext!!
        val rows = input.rows
        val cols = input.columnSize
        require(cols == size) { "BatchNorm: input cols=$cols != size=$size" }

        val (muArr, varArr) =
            if (isTraining) {
                // compute batch mean/var (per feature)
                val sumCols = ctx.backend.sumColumns(input)
                val mu = FloatArray(cols) { j -> sumCols[j] / rows }
                val centered = ctx.backend.subtract(input, ctx.createRowVector(mu))
                val sq = ctx.backend.elementWiseMultiply(centered, centered)
                val varSum = ctx.backend.sumColumns(sq)
                val va = FloatArray(cols) { j -> varSum[j] / rows }

                // save for backward
                this.mean = mu
                this.variance = va

                // update running stats
                var j = 0
                while (j < cols) {
                    runningMean[j]     = momentum * runningMean[j]     + (1f - momentum) * mu[j]
                    runningVariance[j] = momentum * runningVariance[j] + (1f - momentum) * va[j]
                    j++
                }
                mu to va
            } else {
                // use running stats for inference
                runningMean.copyOf() to runningVariance.copyOf()
            }

        // normalize: x̂ = (x - μ) / sqrt(σ² + ε)
        val invStdRow = ctx.backend.applyElementWise(
            ctx.backend.add(ctx.createRowVector(varArr), ctx.createRowVector(FloatArray(cols) { EPSILON }))
        ) { v -> 1f / sqrt(v) }

        val centered = ctx.backend.subtract(input, ctx.createRowVector(muArr))
        normalized = ctx.backend.elementWiseMultiply(centered, invStdRow)

        // affine: y = γ ⊙ x̂ + β
        val y = ctx.backend.add(
            ctx.backend.elementWiseMultiply(normalized!!, ctx.createRowVector(gamma)),
            ctx.createRowVector(beta)
        )
        preActivation = y
        output = y
        return y
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor =
        preForward(input, isTraining)

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val ctx = computeContext!!
        val rows = delta.rows
        val cols = size
        require(variance != null && mean != null && normalized != null) {
            "BatchNormalizationLayer.backward called without saved batch stats"
        }

        // ---- accumulate param grads (per feature) ----
        val dGammaStep = ctx.backend.sumColumns(ctx.backend.elementWiseMultiply(delta, normalized!!))
        val dBetaStep  = ctx.backend.sumColumns(delta)

        val scale = if (featureSize > 1f) 1f / featureSize else 1f
        if (gradGamma == null) gradGamma = FloatArray(cols) { 0f }
        if (gradBeta  == null) gradBeta  = FloatArray(cols) { 0f }
        var j = 0
        while (j < cols) {
            gradGamma!![j] += dGammaStep[j] * scale
            gradBeta!! [j] += dBetaStep [j] * scale
            j++
        }

        // ---- input gradient ----
        // invStd = 1 / sqrt(var + eps)  (per feature, row vector)
        val invStdRow = ctx.backend.applyElementWise(
            ctx.backend.add(ctx.createRowVector(variance!!), ctx.createRowVector(FloatArray(cols) { EPSILON }))
        ) { v -> 1f / sqrt(v) }

        // dYg = delta * gamma
        val dYg = ctx.backend.elementWiseMultiply(delta, ctx.createRowVector(gamma))

        // sum over batch (per feature)
        val sumDY  = ctx.backend.sumColumns(dYg)                                    // [cols]
        val sumDYX = ctx.backend.sumColumns(ctx.backend.elementWiseMultiply(dYg, normalized!!)) // [cols]

        // dx_hat = (rows * dYg - sumDY - x̂ * sumDYX) / rows
        val num = ctx.backend.subtract(
            ctx.backend.scalarMultiply(dYg, rows.toFloat()),
            ctx.backend.add(ctx.createRowVector(sumDY),
                ctx.backend.elementWiseMultiply(normalized!!, ctx.createRowVector(sumDYX))
            )
        )
        val dxHat = ctx.backend.scalarMultiply(num, 1f / rows)

        // dX = dx_hat * invStd
        return ctx.backend.elementWiseMultiply(dxHat, invStdRow)
    }

    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        // nothing to do if no grads accumulated this step
        if (gradGamma == null && gradBeta == null) return

        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        val gG = gradGamma ?: FloatArray(size) { 0f }
        val gB = gradBeta  ?: FloatArray(size) { 0f }

        var j = 0
        while (j < size) {
            // gamma
            val gg = gG[j]
            momentGamma[j]   = adamBeta1 * momentGamma[j]   + (1f - adamBeta1) * gg
            velocityGamma[j] = adamBeta2 * velocityGamma[j] + (1f - adamBeta2) * gg * gg
            gamma[j] -= learningRate * (correctMoment(momentGamma[j]) /
                    (sqrt(correctVelocity(velocityGamma[j])) + EPSILON))

            // beta
            val gb = gB[j]
            momentBeta[j]   = adamBeta1 * momentBeta[j]   + (1f - adamBeta1) * gb
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1f - adamBeta2) * gb * gb
            beta[j] -= learningRate * (correctMoment(momentBeta[j]) /
                    (sqrt(correctVelocity(velocityBeta[j])) + EPSILON))
            j++
        }

        // clear accumulators for next window
        gradGamma = null
        gradBeta  = null
    }

    override fun scaleAccumulatedGradients(f: Float) {
        gradGamma?.let { g -> var i = 0; while (i < g.size) { g[i] *= f; i++ } }
        gradBeta ?.let { g -> var i = 0; while (i < g.size) { g[i] *= f; i++ } }
    }

    override fun clone(): Layer {
        return BatchNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta  = beta.copyOf()
            copy.mean = mean?.copyOf()
            copy.variance = variance?.copyOf()
            copy.normalized = normalized?.deepCopy()
            copy.output = output?.deepCopy()
            copy.runningMean = runningMean.copyOf()
            copy.runningVariance = runningVariance.copyOf()
            copy.gradGamma = gradGamma?.copyOf()
            copy.gradBeta  = gradBeta?.copyOf()
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
