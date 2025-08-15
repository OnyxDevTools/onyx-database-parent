package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.compute.*
import com.onyxdevtools.ai.createTensor
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
    private val inputSize: Int,
    private val outputSize: Int,
    override val activation: Activation,
    private val dropoutRate: Float = 0.0f,
    private val computeContext: ComputeContext = DefaultComputeContext()
) : Layer {

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
        val weightInitLimit: Float = when (activation) {
            Activation.RELU, Activation.LEAKY_RELU -> sqrt((2.0f / inputSize.toFloat()).toDouble()).toFloat()
            else -> sqrt((6.0f / (inputSize.toFloat() + outputSize.toFloat())).toDouble()).toFloat()
        }
        
        // Initialize weights using the compute context
        weights = createTensor(inputSize, outputSize)
        for (i in 0 until inputSize) {
            for (j in 0 until outputSize) {
                weights[i][j] = (Random.nextFloat() * 2.0f - 1.0f) * weightInitLimit
            }
        }
        
        momentWeights = createTensor(inputSize, outputSize) { _, _ -> 0.0f }
        velocityWeights = createTensor(inputSize, outputSize) { _, _ -> 0.0f }
    }

    /**
     * Applies dropout using the compute backend for future optimization
     */
    private fun applyDropout() {
        val activations = output ?: error("Layer output must not be null before applying dropout.")
        if (dropoutRate == 0.0f) return

        val rows = activations.size
        val cols = activations[0].size
        val keepProbability = 1.0f - dropoutRate
        val scaleFactor = 1.0f / keepProbability

        if (dropoutMask == null || dropoutMask!!.size != rows || dropoutMask!![0].size != cols) {
            dropoutMask = createTensor(rows, cols)
        }

        val mask = dropoutMask!!

        // Generate dropout mask (for now using CPU, but this could be GPU-accelerated)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                mask[i][j] = if (Random.nextFloat() < keepProbability) scaleFactor else 0.0f
            }
        }

        // Use compute backend for element-wise multiplication
        output = computeContext.backend.elementWiseMultiply(activations, mask)
    }

    /**
     * Performs the forward pass using the compute backend
     */
    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        // Use compute backend for matrix operations. Release intermediate
        // results immediately to avoid holding on to large temporary tensors
        // across iterations which can lead to native memory leaks on GPU
        // backends.
        val mm = computeContext.backend.matrixMultiply(input, weights)
        val linearOutput = computeContext.backend.addVectorToRows(mm, biases)
        computeContext.releaseMatrix(mm)
        
        val nextInput = nextLayer?.preForward(linearOutput, isTraining) ?: linearOutput
        this.preActivation = nextInput
        
        // Apply activation using compute backend
        output = computeContext.backend.applyElementWise(nextInput, activation::activate)
        
        if (isTraining) applyDropout()
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
        // Constants reused across operations
        val oneMinusBeta1 = 1f - adamBeta1
        val oneMinusBeta2 = 1f - adamBeta2
        val biasCorrect1 = 1f / (1f - adamBeta1Power)
        val biasCorrect2 = 1f / (1f - adamBeta2Power)

        // ----- Weight updates on compute backend (GPU capable) -----
        val gradients = gradientWeights
            ?: error("Gradient weights must be calculated before updating parameters")

        // m_t = beta1 * m_{t-1} + (1 - beta1) * g_t
        val mScaled = computeContext.backend.scalarMultiply(momentWeights, adamBeta1)
        val gScaled = computeContext.backend.scalarMultiply(gradients, oneMinusBeta1)
        val newMoment = computeContext.backend.add(mScaled, gScaled)
        computeContext.releaseMatrix(momentWeights)
        computeContext.releaseMatrix(mScaled)
        computeContext.releaseMatrix(gScaled)
        momentWeights = newMoment

        // v_t = beta2 * v_{t-1} + (1 - beta2) * (g_t ⊙ g_t)
        val vScaled = computeContext.backend.scalarMultiply(velocityWeights, adamBeta2)
        val gSquared = computeContext.backend.elementWiseMultiply(gradients, gradients)
        val gSquaredScaled = computeContext.backend.scalarMultiply(gSquared, oneMinusBeta2)
        val newVelocity = computeContext.backend.add(vScaled, gSquaredScaled)
        computeContext.releaseMatrix(velocityWeights)
        computeContext.releaseMatrix(vScaled)
        computeContext.releaseMatrix(gSquared)
        computeContext.releaseMatrix(gSquaredScaled)
        velocityWeights = newVelocity

        // Compute bias-corrected moments
        val mHat = computeContext.backend.scalarMultiply(momentWeights, biasCorrect1)
        val vHat = computeContext.backend.scalarMultiply(velocityWeights, biasCorrect2)

        // weights = weights - lr * mHat / (sqrt(vHat) + eps)
        val sqrtVHat = computeContext.backend.applyElementWise(vHat) { sqrt(it) }
        val denom = computeContext.backend.applyElementWise(sqrtVHat) { it + EPSILON }
        val invDenom = computeContext.backend.applyElementWise(denom) { 1f / it }
        val update = computeContext.backend.elementWiseMultiply(mHat, invDenom)
        val scaledUpdate = computeContext.backend.scalarMultiply(update, learningRate)
        val newWeights = computeContext.backend.subtract(weights, scaledUpdate)
        computeContext.releaseMatrix(weights)
        weights = newWeights
        computeContext.releaseMatrix(mHat)
        computeContext.releaseMatrix(vHat)
        computeContext.releaseMatrix(sqrtVHat)
        computeContext.releaseMatrix(denom)
        computeContext.releaseMatrix(invDenom)
        computeContext.releaseMatrix(update)
        computeContext.releaseMatrix(scaledUpdate)

        // gradients are no longer needed after weight update
        computeContext.releaseMatrix(gradients)
        gradientWeights = null

        // ----- Bias updates (kept on CPU due to small size) -----
        fun correctMoment(m: Float) = m / (1f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1f - adamBeta2Power)

        for (j in 0 until outputSize) {
            val gradient = gradientBiases!![j]
            momentBiases[j] = adamBeta1 * momentBiases[j] + oneMinusBeta1 * gradient
            velocityBiases[j] = adamBeta2 * velocityBiases[j] + oneMinusBeta2 * gradient * gradient
            biases[j] = biases[j] - learningRate *
                    correctMoment(momentBiases[j]) /
                    (sqrt(correctVelocity(velocityBiases[j])) + EPSILON)
        }
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
        // Use compute backend for element-wise operations and release temporaries
        val derivative = computeContext.backend.applyElementWise(preActivation!!, activation::derivative)
        val currentDelta = computeContext.backend.elementWiseMultiply(delta, derivative)
        computeContext.releaseMatrix(derivative)

        val previousOutput = previousLayer?.output ?: currentInput!!

        // Use compute backend for gradient calculations
        val prevOutputT = computeContext.backend.transpose(previousOutput)
        val gradWeights = computeContext.backend.matrixMultiply(prevOutputT, currentDelta)
        computeContext.releaseMatrix(prevOutputT)

        val scaledGradWeights = computeContext.backend.scalarMultiply(gradWeights, 1.0f / featureSize)
        computeContext.releaseMatrix(gradWeights)
        val regularization = computeContext.backend.scalarMultiply(weights, lambda)

        gradientWeights?.let { computeContext.releaseMatrix(it) }
        gradientWeights = computeContext.backend.add(scaledGradWeights, regularization)
        computeContext.releaseMatrix(scaledGradWeights)
        computeContext.releaseMatrix(regularization)
        gradientBiases = computeContext.backend.sumColumns(currentDelta).map { it / featureSize }.toFloatArray()

        val weightT = computeContext.backend.transpose(weights)
        val prevDelta = computeContext.backend.matrixMultiply(currentDelta, weightT)
        computeContext.releaseMatrix(weightT)
        computeContext.releaseMatrix(currentDelta)

        return prevDelta
    }

    /**
     * Creates a deep copy of the layer with the same compute context
     */
    override fun clone(): DenseLayer {
        return DenseLayer(inputSize, outputSize, activation, dropoutRate, computeContext).also { copy ->
            copy.weights = computeContext.backend.deepCopy(weights)
            copy.biases = biases.copyOf()
            copy.momentWeights = computeContext.backend.deepCopy(momentWeights)
            copy.velocityWeights = computeContext.backend.deepCopy(velocityWeights)
            copy.momentBiases = momentBiases.copyOf()
            copy.velocityBiases = velocityBiases.copyOf()
            copy.preActivation = preActivation?.let { computeContext.backend.deepCopy(it) }
            copy.output = output?.let { computeContext.backend.deepCopy(it) }
            copy.dropoutMask = dropoutMask?.let { computeContext.backend.deepCopy(it) }
            copy.gradientWeights = gradientWeights?.let { computeContext.backend.deepCopy(it) }
            copy.gradientBiases = gradientBiases?.copyOf()
        }
    }

    /**
     * Gets information about the compute backend being used
     */
    fun getBackendInfo(): String {
        return "Using ${computeContext.backend.backendType} backend for computations"
    }

    /**
     * Releases any backend-specific resources
     */
    fun dispose() {
        weights.let { computeContext.releaseMatrix(it) }
        momentWeights.let { computeContext.releaseMatrix(it) }
        velocityWeights.let { computeContext.releaseMatrix(it) }
        preActivation?.let { computeContext.releaseMatrix(it) }
        output?.let { computeContext.releaseMatrix(it) }
        dropoutMask?.let { computeContext.releaseMatrix(it) }
        gradientWeights?.let { computeContext.releaseMatrix(it) }
        computeContext.dispose()
    }

    companion object {
        private const val EPSILON = 1e-8f
    }
}
