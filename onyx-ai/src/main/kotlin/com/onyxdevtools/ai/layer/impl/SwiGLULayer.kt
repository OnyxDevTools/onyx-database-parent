package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.layer.impl.DenseLayer
import kotlin.math.exp

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
    private val computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
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
        val gate = computeContext.backend.applyElementWise(x1) { v -> v / (1f + exp(-v)) }
        gateTensor = gate
        val gated = computeContext.backend.elementWiseMultiply(gate, x2)
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
        // backprop through final projection
        val dHidden = projOut.backward(gateTensor, delta, featureSize, nextLayer, this, lambda)
        // distribute gradients through gating
        val mask = computeContext.backend.applyElementWise(
            proj1.output!!,
            { v ->
                val s = 1f / (1f + exp(-v))
                s * (1f + v * (1f - s))
            }
        )
        val d1 = computeContext.backend.elementWiseMultiply(dHidden, computeContext.backend.elementWiseMultiply(proj2.output!!, mask))
        val d2 = computeContext.backend.elementWiseMultiply(dHidden, gateTensor!!)
        val dx1 = proj1.backward(currentInput, d1, featureSize, this, previousLayer, lambda)
        val dx2 = proj2.backward(currentInput, d2, featureSize, this, previousLayer, lambda)
        return computeContext.backend.add(dx1, dx2)
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
    }

    /**
     * Releases backend resources.
     */
    fun dispose() {
        proj1.dispose()
        proj2.dispose()
        projOut.dispose()
        computeContext.dispose()
    }
}
