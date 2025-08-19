package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext
import com.onyxdevtools.ai.layer.Layer


/**
 * SwiGLU layer: gated feed-forward unit using the Swish (SiLU) activation.
 *
 * Performs two linear projections, applies Swish gating, and a final output projection:
 *   x1 = Dense(input -> hidden)
 *   x2 = Dense(input -> hidden)
 *   gated = Swish(x1) ⊙ x2
 *   output = Dense(gated -> output)
 */
class SwiGLULayer(
    private val inputSize: Int,
    private val hiddenSize: Int,
    private val outputSize: Int,
    @kotlin.jvm.Transient private var computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
    @kotlin.jvm.Transient
    private var ctx = computeContext
    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var proj1 = DenseLayer(inputSize, hiddenSize, Activation.LINEAR, 0.0f, computeContext)
    private var proj2 = DenseLayer(inputSize, hiddenSize, Activation.LINEAR, 0.0f, computeContext)
    private var projOut = DenseLayer(hiddenSize, outputSize, Activation.LINEAR, 0.0f, computeContext)

    private var gateTensor: Tensor? = null

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        val x1 = proj1.forward(input, isTraining, null)
        val x2 = proj2.forward(input, isTraining, null)

        val gate = ctx.backend.applyElementWise(x1) { v -> swishf(v) }
        gateTensor = gate
        val gated = ctx.backend.elementWiseMultiply(gate, x2)
        val out = projOut.forward(gated, isTraining, nextLayer)
        preActivation = out
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
        // dHidden through final linear, using gate as the "input" to projOut
        val dHidden = projOut.backward(gateTensor, delta, featureSize, nextLayer, null, lambda)

        // mask = d swish(x1) / d x1
        val mask = ctx.backend.applyElementWise(proj1.output!!) { v -> dswishf(v) }

        // d x1 = dHidden ⊙ x2 ⊙ swish'(x1)
        val d1 = ctx.backend.elementWiseMultiply(dHidden, ctx.backend.elementWiseMultiply(proj2.output!!, mask))

        // d x2 = dHidden ⊙ swish(x1)  (== gate)
        val d2 = ctx.backend.elementWiseMultiply(dHidden, gateTensor!!)
        val dx1 = proj1.backward(currentInput, d1, featureSize, this, previousLayer, lambda)
        val dx2 = proj2.backward(currentInput, d2, featureSize, this, previousLayer, lambda)
        return ctx.backend.add(dx1, dx2)
    }

    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        proj1.updateParameters(adamBeta1Power, adamBeta2Power, adamBeta1, adamBeta2, learningRate)
        proj2.updateParameters(adamBeta1Power, adamBeta2Power, adamBeta1, adamBeta2, learningRate)
        projOut.updateParameters(adamBeta1Power, adamBeta2Power, adamBeta1, adamBeta2, learningRate)
    }

    override fun clone(): Layer = SwiGLULayer(
        inputSize,
        hiddenSize,
        outputSize,
        computeContext
    ).also { copy ->
        copy.proj1 = proj1.clone() as DenseLayer
        copy.proj2 = proj2.clone() as DenseLayer
        copy.projOut = projOut.clone() as DenseLayer

        copy.gateTensor = gateTensor?.deepCopy()
        copy.preActivation = preActivation?.deepCopy()
        copy.output = output?.deepCopy()
    }

    /**
     * Releases backend resources.
     */
    fun dispose() {
        proj1.dispose()
        proj2.dispose()
        projOut.dispose()
        ctx.dispose()
    }

    private fun sigmoidf(x: Float): Float {
        // stable sigmoid using Float math (via Double then back)
        return if (x >= 0f) {
            val z = kotlin.math.exp(-x.toDouble()).toFloat()
            1f / (1f + z)
        } else {
            val z = kotlin.math.exp(x.toDouble()).toFloat()
            z / (1f + z)
        }
    }

    private fun swishf(x: Float): Float {
        val s = sigmoidf(x)
        return x * s
    }

    private fun dswishf(x: Float): Float {
        // swish'(x) = s + x*s*(1 - s)
        val s = sigmoidf(x)
        return s * (1f + x * (1f - s))
    }

    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
        ctx = computeContext
    }

    override fun scaleAccumulatedGradients(f: Float) {
        // Average the accumulated grads once per optimizer step (Recipe A).
        // DenseLayer.scaleAccumulatedGradients handles null buffers safely.
        proj1.scaleAccumulatedGradients(f)
        proj2.scaleAccumulatedGradients(f)
        projOut.scaleAccumulatedGradients(f)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
