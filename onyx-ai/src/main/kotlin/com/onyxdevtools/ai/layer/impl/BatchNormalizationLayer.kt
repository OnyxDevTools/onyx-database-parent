package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt

/**
 * Batch Normalization on tensors.
 * Normalizes across the batch dimension for each feature (per-column statistics).
 */
class BatchNormalizationLayer(private val size: Int) : Layer, java.io.Serializable {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta  = FloatArray(size) { 0.0f }

    private var mean: FloatArray? = null         // per-feature (length = size)
    private var variance: FloatArray? = null     // per-feature (length = size)
    private var normalized: Tensor? = null       // same shape as input

    // Running (EMA) stats for inference
    private var runningMean = FloatArray(size) { 0.0f }
    private var runningVariance = FloatArray(size) { 1.0f }
    private val momentum = 0.9f

    private var gradGamma: FloatArray? = null
    private var gradBeta:  FloatArray? = null

    // Adam accumulators
    private var momentGamma   = FloatArray(size)
    private var velocityGamma = FloatArray(size)
    private var momentBeta    = FloatArray(size)
    private var velocityBeta  = FloatArray(size)

    override fun preForward(input: Tensor, isTraining: Boolean): Tensor {
        if (input.rows == 0 || input.cols == 0) {
            preActivation = input
            output = input
            normalized = input
            mean = FloatArray(size) { 0f }
            variance = FloatArray(size) { 1f }
            return input
        }

        require(input.cols == size) {
            "BatchNormalizationLayer expects feature size=$size, got input.cols=${input.cols}"
        }

        val rows = input.rows
        val cols = input.cols

        val mu   = FloatArray(size)   // size == cols (already enforced)
        val varr = FloatArray(size)

// use a float reciprocal once
        val invRows = 1f / rows.toFloat()

// --- Mean over batch per feature ---
        var r = 0
        val scratch = FloatArray(cols)         // reuse a row buffer (fewer element reads)
        while (r < rows) {
            input.readRowInto(r, scratch)
            var c = 0
            while (c < cols) {
                mu[c] += scratch[c]
                c++
            }
            r++
        }
        var c = 0
        while (c < cols) {
            mu[c] *= invRows            // instead of mu[c] /= rows
            c++
        }

// --- Variance over batch per feature ---
        r = 0
        while (r < rows) {
            input.readRowInto(r, scratch)
            c = 0
            while (c < cols) {
                val d = scratch[c] - mu[c]
                varr[c] += d * d
                c++
            }
            r++
        }
        c = 0
        while (c < cols) {
            varr[c] *= invRows          // instead of varr[c] /= rows
            c++
        }

        val y = createTensor(rows, cols)
        if (isTraining) {
            // Normalize with batch stats
            r = 0
            while (r < rows) {
                c = 0
                while (c < cols) {
                    val invStd = 1f / sqrt(varr[c] + EPSILON)
                    y[r, c] = (input[r, c] - mu[c]) * invStd
                    c++
                }
                r++
            }

            // Update running stats (EMA)
            c = 0
            while (c < cols) {
                runningMean[c]     = momentum * runningMean[c]     + (1f - momentum) * mu[c]
                runningVariance[c] = momentum * runningVariance[c] + (1f - momentum) * varr[c]
                c++
            }

            mean = mu
            variance = varr
        } else {
            // Normalize with running stats
            r = 0
            while (r < rows) {
                c = 0
                while (c < cols) {
                    val invStd = 1f / sqrt(runningVariance[c] + EPSILON)
                    y[r, c] = (input[r, c] - runningMean[c]) * invStd
                    c++
                }
                r++
            }
            // keep previous mean/variance; not used in inference backward anyway
        }

        // Affine: z = gamma ⊙ y + beta
        val out = createTensor(rows, cols)
        r = 0
        while (r < rows) {
            c = 0
            while (c < cols) {
                out[r, c] = gamma[c] * y[r, c] + beta[c]
                c++
            }
            r++
        }

        normalized = y
        preActivation = y
        output = out
        return out
    }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        return preForward(input, isTraining)
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val input = currentInput ?: error("BatchNormalizationLayer.backward: currentInput is null")
        val rows = delta.rows
        val cols = delta.cols
        require(cols == size && input.cols == size && input.rows == rows) {
            "Backward shape mismatch: delta=(${rows}x$cols), input=(${input.rows}x${input.cols}), size=$size"
        }

        val y = normalized ?: error("BatchNormalizationLayer.backward: normalized not computed")
        val varr = variance ?: error("BatchNormalizationLayer.backward: variance not computed (training only)")

        // Gradients w.r.t. gamma and beta
        val gGamma = FloatArray(size)
        val gBeta  = FloatArray(size)

        var r = 0
        while (r < rows) {
            var c = 0
            while (c < cols) {
                gGamma[c] += delta[r, c] * y[r, c]
                gBeta[c]  += delta[r, c]
                c++
            }
            r++
        }
        gradGamma = gGamma
        gradBeta = gBeta

        // Per-feature aggregates for BN backward
        val N = rows.toFloat()
        val sum_dY = FloatArray(size)         // Σ_i dY[i,j]
        val sum_dY_xhat = FloatArray(size)    // Σ_i dY[i,j] * x̂[i,j]
        r = 0
        while (r < rows) {
            var c2 = 0
            while (c2 < cols) {
                val dY = delta[r, c2]
                sum_dY[c2] += dY
                sum_dY_xhat[c2] += dY * y[r, c2]
                c2++
            }
            r++
        }

        // dX[i,j] = gamma[j]*invStd[j]*( dY[i,j] - Σ dY / N - x̂[i,j]*Σ(dY*x̂)/N )
        val gradInput = createTensor(rows, cols)
        var c = 0
        val invStd = FloatArray(size)
        while (c < cols) {
            invStd[c] = 1f / sqrt(varr[c] + EPSILON)
            c++
        }

        r = 0
        while (r < rows) {
            c = 0
            while (c < cols) {
                val dY = delta[r, c]
                val term = dY - (sum_dY[c] / N) - (y[r, c] * (sum_dY_xhat[c] / N))
                gradInput[r, c] = gamma[c] * invStd[c] * term
                c++
            }
            r++
        }

        return gradInput
    }

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

        var j = 0
        while (j < size) {
            val gG = gradGamma?.get(j) ?: 0f
            val gB = gradBeta?.get(j) ?: 0f

            momentGamma[j]   = adamBeta1 * momentGamma[j]   + (1f - adamBeta1) * gG
            velocityGamma[j] = adamBeta2 * velocityGamma[j] + (1f - adamBeta2) * gG * gG
            gamma[j] = gamma[j] - learningRate * (correctMoment(momentGamma[j]) /
                    (sqrt(correctVelocity(velocityGamma[j])) + EPSILON))

            momentBeta[j]    = adamBeta1 * momentBeta[j]    + (1f - adamBeta1) * gB
            velocityBeta[j]  = adamBeta2 * velocityBeta[j]  + (1f - adamBeta2) * gB * gB
            beta[j] = beta[j] - learningRate * (correctMoment(momentBeta[j]) /
                    (sqrt(correctVelocity(velocityBeta[j])) + EPSILON))
            j++
        }
    }

    override fun clone(): Layer {
        return BatchNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta  = beta.copyOf()
            copy.mean = mean?.copyOf()
            copy.variance = variance?.copyOf()
            copy.normalized = normalized?.copy()
            copy.output = output?.copy()
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
