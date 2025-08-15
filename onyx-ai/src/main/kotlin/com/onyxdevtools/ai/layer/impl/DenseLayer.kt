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
        // Use compute backend for matrix operations
        val linearOutput = computeContext.backend.addVectorToRows(
            computeContext.backend.matrixMultiply(input, weights), 
            biases
        )
        
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
        fun correctMoment(m: Float) = m / (1.0f - adamBeta1Power)
        fun correctVelocity(v: Float) = v / (1.0f - adamBeta2Power)

        for (i in 0 until inputSize) {
            for (j in 0 until outputSize) {
                val gradient = gradientWeights!![i][j]
                momentWeights[i][j] = adamBeta1 * momentWeights[i][j] + (1.0f - adamBeta1) * gradient
                velocityWeights[i][j] = adamBeta2 * velocityWeights[i][j] + (1.0f - adamBeta2) * gradient * gradient
                weights[i][j] = weights[i][j] - learningRate *
                        correctMoment(momentWeights[i][j]) / (sqrt(correctVelocity(velocityWeights[i][j])) + EPSILON)
            }
        }

        for (j in 0 until outputSize) {
            val gradient = gradientBiases!![j]
            momentBiases[j] = adamBeta1 * momentBiases[j] + (1.0f - adamBeta1) * gradient
            velocityBiases[j] = adamBeta2 * velocityBiases[j] + (1.0f - adamBeta2) * gradient * gradient
            biases[j] = biases[j] - learningRate *
                    correctMoment(momentBiases[j]) / (sqrt(correctVelocity(velocityBiases[j])) + EPSILON)
        }
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
        // Use compute backend for element-wise operations
        val currentDelta = computeContext.backend.elementWiseMultiply(
            delta,
            computeContext.backend.applyElementWise(preActivation!!, activation::derivative)
        )

        val previousOutput = previousLayer?.output ?: currentInput!!

        // Use compute backend for gradient calculations
        val gradWeights = computeContext.backend.matrixMultiply(
            computeContext.backend.transpose(previousOutput), 
            currentDelta
        )
        
        val scaledGradWeights = computeContext.backend.scalarMultiply(gradWeights, 1.0f / featureSize)
        val regularization = computeContext.backend.scalarMultiply(weights, lambda)
        
        gradientWeights = computeContext.backend.add(scaledGradWeights, regularization)
        gradientBiases = computeContext.backend.sumColumns(currentDelta).map { it / featureSize }.toFloatArray()

        return computeContext.backend.matrixMultiply(
            currentDelta, 
            computeContext.backend.transpose(weights)
        )
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
