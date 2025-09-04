package dev.onyx.ai.layer.impl

import Activation
import dev.onyx.ai.Tensor
import dev.onyx.ai.layer.Layer
import dev.onyx.ai.compute.*
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A compute-aware dense layer that demonstrates integration with the compute abstraction layer.
 * This layer can seamlessly switch between CPU and GPU backends through the ComputeContext.
 *
 * This is an example implementation showing how to migrate existing layers to use
 * the compute abstraction layer for future GPU acceleration.
 *
 * @param inputSize The number of input features/neurons from the previous layer
 * @param outputSize The number of output neurons/features this layer produces
 * @param activation The activation function applied after the linear transformation
 * @param dropoutRate The probability of setting each input to zero during training
 * @param computeContext The compute context that manages backend operations
 */
class DenseLayer(
    val inputSize: Int,
    val outputSize: Int,
    override val activation: Activation,
    private val dropoutRate: Float = 0.0f,
    @kotlin.jvm.Transient private var computeContext: ComputeContext = DefaultComputeContext()
) : Layer {
    @kotlin.jvm.Transient
    private var ctx = computeContext
    override var preActivation: Tensor? = null
    override var output: Tensor? = null

    var weights: Tensor
    private var biases = FloatArray(outputSize) { 0.0f }

    private var momentWeights: Tensor
    private var velocityWeights: Tensor
    private var momentBiases = FloatArray(outputSize)
    private var velocityBiases = FloatArray(outputSize)

    private var dropoutMask: Tensor? = null
    private var gradientWeights: Tensor? = null
    private var gradientBiases: FloatArray? = null

    init {
        val fanIn = inputSize.toFloat()
        val fanOut = outputSize.toFloat()
        val bound: Float = when (activation) {
            Activation.RELU, Activation.LEAKY_RELU -> kotlin.math.sqrt((6f / fanIn))
            else -> kotlin.math.sqrt((6f / (fanIn + fanOut)))
        }
        weights = ctx.createMatrix(inputSize, outputSize)
        for (i in 0 until inputSize) {
            for (j in 0 until outputSize) {
                weights[i][j] = (kotlin.random.Random.nextFloat() * 2f - 1f) * bound
            }
        }
        momentWeights = ctx.createMatrix(inputSize, outputSize, 0f)
        velocityWeights = ctx.createMatrix(inputSize, outputSize, 0f)
    }

    /**
     * Applies dropout using the compute backend for future optimization
     */
    private fun applyDropout() {
        if (dropoutRate == 0f) return
        val activations = output ?: error("output null before dropout")
        val rows = activations.rows
        val cols = activations.columnSize
        val keepP = 1f - dropoutRate
        val scale = 1f / keepP

        if (dropoutMask == null || dropoutMask!!.rows != rows || dropoutMask!!.columnSize != cols) {
            dropoutMask = ctx.createMatrix(rows, cols)
        }
        val mask = dropoutMask!!
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                mask[i,j] = if (kotlin.random.Random.nextFloat() < keepP) scale else 0f
            }
        }
        output = ctx.backend.elementWiseMultiply(activations, mask)
    }

    /**
     * Performs the forward pass using the compute backend
     */
    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        val z = ctx.backend.addVectorToRows(ctx.backend.matrixMultiply(input, weights), biases)
        preActivation = z
        output = ctx.backend.applyElementWise(z, activation::activate)
        if (isTraining) applyDropout()               // uses & sets dropoutMask
        return output!!
    }

    /**
     * Updates weights and biases using Adam optimizer with compute backend operations
     */
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        // If nothing accumulated yet, nothing to do.
        val GW = gradientWeights ?: return
        val gBias = gradientBiases ?: FloatArray(outputSize) // treat missing bias grads as zeros

        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        // Weights
        for (i in 0 until inputSize) {
            for (j in 0 until outputSize) {
                val g = GW[i][j]                         // accumulated gradient
                val m = adamBeta1 * momentWeights[i][j] + (1.0f - adamBeta1) * g
                val v = adamBeta2 * velocityWeights[i][j] + (1.0f - adamBeta2) * g * g
                momentWeights[i][j] = m
                velocityWeights[i][j] = v
                weights[i][j] -= learningRate * (correctMoment(m) / (sqrt(correctVelocity(v)) + EPSILON))
            }
        }

        // Biases
        for (j in 0 until outputSize) {
            val g = gBias[j]
            val m = adamBeta1 * momentBiases[j] + (1.0f - adamBeta1) * g
            val v = adamBeta2 * velocityBiases[j] + (1.0f - adamBeta2) * g * g
            momentBiases[j] = m
            velocityBiases[j] = v
            biases[j] -= learningRate * (correctMoment(m) / (sqrt(correctVelocity(v)) + EPSILON))
        }

        // Clear accumulated grads so the next micro-batch starts fresh.
        gradientWeights = null
        gradientBiases = null
    }


    /**
     * Performs backward pass using compute backend operations
     */
    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        // include dropout gradient
        val afterDropoutDelta =
            if (dropoutRate > 0f && dropoutMask != null)
                ctx.backend.elementWiseMultiply(delta, dropoutMask!!)
            else delta

        val currentDelta = ctx.backend.elementWiseMultiply(
            afterDropoutDelta,
            ctx.backend.applyElementWise(preActivation!!, activation::derivative)
        )

        val x = previousLayer?.output ?: currentInput!!
        val gradWeightsRaw = ctx.backend.matrixMultiply(ctx.backend.transpose(x), currentDelta)

        val scaledGradWeights = ctx.backend.scalarMultiply(gradWeightsRaw, 1f / featureSize)
        val regularization    = ctx.backend.scalarMultiply(weights, lambda)
        gradientWeights = ctx.backend.add(scaledGradWeights, regularization)

        gradientBiases = ctx.backend
            .sumColumns(currentDelta)               // length = outputSize
            .map { it / featureSize }.toFloatArray()

        // return dX
        return ctx.backend.matrixMultiply(currentDelta, ctx.backend.transpose(weights))
    }

    /**
     * Creates a deep copy of the layer with the same compute context
     */
    override fun clone(): DenseLayer {
        return DenseLayer(inputSize, outputSize, activation, dropoutRate, computeContext).also { copy ->
            copy.weights = weights.deepCopy()
            copy.biases = biases.copyOf()
            copy.momentWeights = momentWeights.deepCopy()
            copy.velocityWeights = velocityWeights.deepCopy()
            copy.momentBiases = momentBiases.copyOf()
            copy.velocityBiases = velocityBiases.copyOf()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.let { it.deepCopy() }
            copy.dropoutMask = dropoutMask?.deepCopy()
            copy.gradientWeights = gradientWeights?.deepCopy()
            copy.gradientBiases = gradientBiases?.copyOf()
        }
    }

    /**
     * Releases any backend-specific resources
     */
    fun dispose() {
        ctx.releaseMatrix(weights)
        ctx.releaseMatrix(momentWeights)
        ctx.releaseMatrix(velocityWeights)
        preActivation?.let { ctx.releaseMatrix(it) }
        output?.let { ctx.releaseMatrix(it) }
        dropoutMask?.let { ctx.releaseMatrix(it) }
        gradientWeights?.let { ctx.releaseMatrix(it) }
        ctx.dispose()
    }

    companion object {
        private const val serialVersionUID = 1L
        private const val EPSILON = 1e-8f
    }

    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        computeContext = DefaultComputeContext()
        ctx = computeContext
    }

    // add inside class
    override fun scaleAccumulatedGradients(f: Float) {
        gradientWeights = gradientWeights?.let { ctx.backend.scalarMultiply(it, f) }
        gradientBiases?.let { gb ->
            var i = 0; while (i < gb.size) { gb[i] *= f; i++ }
        }
    }

}
