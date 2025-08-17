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
    @kotlin.jvm.Transient private var ctx = computeContext
    override var preActivation: Tensor? = null
    override var output: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        var x = input
        // apply sub-layers sequentially
        for ((i, layer) in layers.withIndex()) {
            val nl = if (i < layers.lastIndex) layers[i + 1] else null
            x = layer.forward(x, isTraining, nl)
        }
        // residual add
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
        // backprop through sub-layers
        var grad = delta
        for (i in layers.indices.reversed()) {
            val layer = layers[i]
            val prevOut = if (i > 0) layers[i - 1].output else currentInput
            val nextL = if (i < layers.lastIndex) layers[i + 1] else null
            grad = layer.backward(prevOut, grad, featureSize, nextL, if (i > 0) layers[i - 1] else previousLayer, lambda)
        }
        // gradient from skip connection
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

    /**
     * Releases backend resources.
     */
    fun dispose() {
        layers.forEach {
            when (it) {
                is ResidualLayer -> it.dispose()
                is SwiGLULayer    -> it.dispose()
                else              -> {/* assume layer manages its own disposal */}
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
