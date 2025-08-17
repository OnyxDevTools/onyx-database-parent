package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.compute.ComputeContext
import com.onyxdevtools.ai.compute.DefaultComputeContext
import com.onyxdevtools.ai.layer.Layer

/**
 * Residual layer that applies a sequence of sub-layers and adds the original input.
 * Useful for transformer-style Add & Norm blocks.
 *
 * @param layers The sequence of layers to apply within the residual branch.
 * @param computeContext The compute context for element-wise add operations.
 */
class ResidualLayer(
    private val layers: List<Layer>,
    @kotlin.jvm.Transient private var computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
    @kotlin.jvm.Transient
    private var ctx = computeContext
    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        var x = input
        for (i in layers.indices) {
            val layer = layers[i]
            // For the last inner layer, surface the *outer* nextLayer
            val nl = if (i < layers.lastIndex) layers[i + 1] else nextLayer
            x = layer.forward(x, isTraining, nl)
        }
        // Residual add: require shape match
        require(x.rows == input.rows && x.columnSize == input.columnSize) {
            "Residual add shape mismatch: input=${input.rows}x${input.columnSize}, F(x)=${x.rows}x${x.columnSize}"
        }
        val res = ctx.backend.add(input, x)
        preActivation = res
        output = res
        return res
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        var grad = delta
        // Backprop through inner stack
        for (i in layers.indices.reversed()) {
            val layer = layers[i]
            val prevOut = if (i > 0) layers[i - 1].output else currentInput
            // For the last inner layer, surface the *outer* nextLayer
            val nl = if (i < layers.lastIndex) layers[i + 1] else nextLayer
            val pl = if (i > 0) layers[i - 1] else previousLayer
            grad = layer.backward(prevOut, grad, featureSize, nl, pl, lambda)
        }
        // Skip connection gradient: ∂L/∂x = grad + delta
        require(grad.rows == delta.rows && grad.columnSize == delta.columnSize) {
            "Residual grad shape mismatch: grad=${grad.rows}x${grad.columnSize}, delta=${delta.rows}x${delta.columnSize}"
        }
        return ctx.backend.add(grad, delta)
    }

    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        layers.forEach { it.updateParameters(adamBeta1Power, adamBeta2Power, adamBeta1, adamBeta2, learningRate) }
    }

    override fun clone(): Layer {
        return ResidualLayer(layers.map { it.clone() }, computeContext).also { copy ->
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
        }
    }

    fun dispose() {
        layers.forEach {
            when (it) {
                is ResidualLayer -> it.dispose()
                is SwiGLULayer -> it.dispose()
                else -> {/* no-op */
                }
            }
        }
        ctx.dispose()
    }

    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
        ctx = computeContext
    }
}
