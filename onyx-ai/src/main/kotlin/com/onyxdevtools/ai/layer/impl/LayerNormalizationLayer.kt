package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Layer Normalization for tensors.
 * Expects each row to be a sample of length `size` (feature dim).
 */
class LayerNormalizationLayer(private val size: Int) : Layer {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta  = FloatArray(size) { 0.0f }

    // cached from forward (by row)
    private var mean:     FloatArray? = null      // length = rows
    private var variance: FloatArray? = null      // length = rows
    private var normalized: Tensor? = null        // same shape as input

    // grads for params
    private var gradGamma: FloatArray? = null
    private var gradBeta:  FloatArray? = null

    // Adam stats
    private var momentGamma   = FloatArray(size) { 0.0f }
    private var velocityGamma = FloatArray(size) { 0.0f }
    private var momentBeta    = FloatArray(size) { 0.0f }
    private var velocityBeta  = FloatArray(size) { 0.0f }

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        if (input.rows == 0 || input.cols == 0) {
            output = input
            preActivation = input
            normalized = input
            mean = FloatArray(0)
            variance = FloatArray(0)
            return input
        }

        require(input.cols == size) {
            "LayerNormalizationLayer expected feature size=$size, got input.cols=${input.cols}"
        }

        val rows = input.rows
        val cols = input.cols

        val mu  = FloatArray(rows)
        val varr = FloatArray(rows)
        val xRow = FloatArray(cols)

        // compute per-row mean and variance
        var r = 0
        while (r < rows) {
            input.readRowInto(r, xRow)

            var sum = 0f
            var c = 0
            while (c < cols) { sum += xRow[c]; c++ }
            val m = sum / cols
            mu[r] = m

            var s2 = 0f
            c = 0
            while (c < cols) {
                val d = xRow[c] - m
                s2 += d * d
                c++
            }
            varr[r] = s2 / cols
            r++
        }

        // normalized output y = (x - mu)/sqrt(var+eps)
        val y = createTensor(rows, cols)

        r = 0
        while (r < rows) {
            input.readRowInto(r, xRow)
            val invStd = 1f / sqrt(varr[r] + EPSILON)
            var c = 0
            while (c < cols) {
                y[r, c] = (xRow[c] - mu[r]) * invStd
                c++
            }
            r++
        }

        // affine: z = gamma ⊙ y + beta
        val out = createTensor(rows, cols)
        r = 0
        while (r < rows) {
            var c = 0
            while (c < cols) {
                out[r, c] = gamma[c] * y[r, c] + beta[c]
                c++
            }
            r++
        }

        // cache for backward
        mean = mu
        variance = varr
        normalized = y

        preActivation = y
        output = out
        return out
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val input = currentInput ?: error("LayerNormalizationLayer.backward: currentInput is null")
        val rows = delta.rows
        val cols = delta.cols
        require(cols == size && input.cols == size && input.rows == rows) {
            "Backward shape mismatch: delta=(${rows}x$cols), input=(${input.rows}x${input.cols}), size=$size"
        }

        val mu = mean ?: error("LayerNormalizationLayer.backward: mean not computed")
        val varr = variance ?: error("LayerNormalizationLayer.backward: variance not computed")
        val y = normalized ?: error("LayerNormalizationLayer.backward: normalized not computed")

        // grads for gamma/beta
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

        // grad wrt normalized input: dY = delta * gamma
        // and then backprop through normalization per-row
        val gradInput = createTensor(rows, cols)
        val xRow = FloatArray(cols)
        val dYRow = FloatArray(cols)

        r = 0
        while (r < rows) {
            input.readRowInto(r, xRow)

            // dY = delta * gamma
            var c = 0
            while (c < cols) {
                dYRow[c] = delta[r, c] * gamma[c]
                c++
            }

            val invStd = 1f / sqrt(varr[r] + EPSILON)
            val invDen3 = 1f / (varr[r] + EPSILON).pow(1.5f)

            // sums needed for formula
            var sum_dY = 0f
            var sum_dY_xmu = 0f
            c = 0
            while (c < cols) {
                sum_dY += dYRow[c]
                sum_dY_xmu += dYRow[c] * (xRow[c] - mu[r])
                c++
            }

            val dVar  = -0.5f * sum_dY_xmu * invDen3
            val dMean = -sum_dY * invStd

            c = 0
            while (c < cols) {
                val xmu = xRow[c] - mu[r]
                val term1 = dYRow[c] * invStd
                val term2 = dMean / cols
                val term3 = dVar * 2f * xmu / cols
                gradInput[r, c] = term1 + term2 + term3
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
        fun correctMoment(m: Float) = m / (1f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1f - adamBeta2Power)

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

    override fun clone(): Layer =
        LayerNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta  = beta.copyOf()
            copy.momentGamma   = momentGamma.copyOf()
            copy.velocityGamma = velocityGamma.copyOf()
            copy.momentBeta    = momentBeta.copyOf()
            copy.velocityBeta  = velocityBeta.copyOf()
        }

    companion object { private const val EPSILON = 1e-8f }
}
