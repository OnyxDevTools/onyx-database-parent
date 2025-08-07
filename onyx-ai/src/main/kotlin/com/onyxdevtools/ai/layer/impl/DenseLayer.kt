package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.FlexibleMatrix
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream
import kotlin.math.sqrt
import kotlin.random.Random

// Temporary typealias for conversion process
typealias Matrix = Array<DoubleArray>

/**
 * A fully connected (dense) neural network layer with dropout regularization and Adam optimization.
 *
 * Dense layers implement the fundamental building block of feedforward neural networks, performing
 * an affine transformation followed by a non-linear activation function:
 * 
 * **Mathematical Operation:**
 * output = activation(input × weights + biases)
 *
 * **Key Features:**
 * - **Adaptive Weight Initialization**: Uses Xavier/Glorot or He initialization based on activation function
 * - **Dropout Regularization**: Optional dropout during training to prevent overfitting
 * - **Adam Optimization**: Built-in support for adaptive moment estimation optimizer
 * - **Parallel Processing**: Optimized dropout implementation using parallel streams
 * - **Memory Efficient**: Reuses dropout masks to minimize allocations
 *
 * **Weight Initialization Strategy:**
 * - ReLU/LeakyReLU: He initialization (√(2/inputSize))
 * - Other activations: Xavier initialization (√(6/(inputSize + outputSize)))
 *
 * **Dropout Implementation:**
 * During training, randomly sets a fraction of input units to 0 at each update,
 * which helps prevent overfitting. The remaining units are scaled by 1/(1-dropoutRate)
 * to maintain the expected sum of activations.
 *
 * This layer is commonly used in:
 * - Feedforward neural networks
 * - Final classification/regression layers
 * - Hidden layers in transformer feedforward blocks
 * - Multi-layer perceptrons (MLPs)
 *
 * @param inputSize The number of input features/neurons from the previous layer
 * @param outputSize The number of output neurons/features this layer produces
 * @param activation The activation function applied after the linear transformation
 * @param dropoutRate The probability of setting each input to zero during training.
 *                    Range: [0.0, 1.0] where 0.0 = no dropout, 0.5 = 50% dropout
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 * @see Layer
 * @see Activation
 */
class DenseLayer(
    private val inputSize: Int,
    private val outputSize: Int,
    override val activation: Activation,
    val dropoutRate: Double = 0.0,
    private val precision: MatrixPrecision = MatrixPrecision.SINGLE
) : Layer {

    override var preActivation: FlexibleMatrix? = null
    override var output: FlexibleMatrix? = null

    var weights: FlexibleMatrix
    private var biases: FlexibleMatrix

    private var momentWeights: FlexibleMatrix
    private var velocityWeights: FlexibleMatrix
    private var momentBiases: FlexibleMatrix
    private var velocityBiases: FlexibleMatrix

    private var dropoutMask: FlexibleMatrix? = null
    private var gradientWeights: FlexibleMatrix? = null
    private var gradientBiases: FlexibleMatrix? = null
    
    // Pre-allocated working buffers to avoid constant allocations
    private var workingBuffer1: FlexibleMatrix? = null
    private var workingBuffer2: FlexibleMatrix? = null
    private var gradWeightBuffer: FlexibleMatrix? = null

    init {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        val weightInitLimit = when (activation) {
            Activation.RELU, Activation.LEAKY_RELU -> sqrt(2.0 / inputSize)
            else -> sqrt(6.0 / (inputSize + outputSize))
        }
        
        weights = createMatrix(inputSize, outputSize, isSinglePrecision) { _, _ ->
            Random.nextDouble(-weightInitLimit, weightInitLimit)
        }
        
        biases = createMatrix(1, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        
        momentWeights = createMatrix(inputSize, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityWeights = createMatrix(inputSize, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        momentBiases = createMatrix(1, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        velocityBiases = createMatrix(1, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        
        // Pre-allocate gradient matrices
        gradientWeights = createMatrix(inputSize, outputSize, isSinglePrecision) { _, _ -> 0.0 }
        gradientBiases = createMatrix(1, outputSize, isSinglePrecision) { _, _ -> 0.0 }
    }

    /**
     * High-performance dropout using in-place operations
     */
    private fun applyDropout() {
        val activations = output ?: error("Layer output must not be null before applying dropout.")
        if (dropoutRate == 0.0) return

        val rows = activations.rows
        val cols = activations.cols
        val keepProbability = 1.0 - dropoutRate
        val scaleFactor = 1.0 / keepProbability

        // Apply dropout directly in-place
        for (i in 0 until rows) {
            val rng = random.get()
            for (j in 0 until cols) {
                if (rng.nextDouble() < keepProbability) {
                    activations[i, j] *= scaleFactor
                } else {
                    activations[i, j] = 0.0
                }
            }
        }
    }

    /**
     * High-performance forward pass using buffer reuse and in-place operations
     */
    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        val batchSize = input.rows
        
        // Ensure working buffers are allocated and sized correctly
        if (workingBuffer1 == null || workingBuffer1!!.rows != batchSize || workingBuffer1!!.cols != outputSize) {
            workingBuffer1 = createMatrix(batchSize, outputSize, input.isSinglePrecision)
            workingBuffer2 = createMatrix(batchSize, outputSize, input.isSinglePrecision)
        }
        
        val linearOutput = workingBuffer1!!
        val activatedOutput = workingBuffer2!!
        
        // High-performance matrix multiplication: input × weights
        val matMulResult = com.onyxdevtools.ai.extensions.matrixMultiply(input, weights)
        
        // Add biases efficiently in single pass
        for (i in 0 until batchSize) {
            for (j in 0 until outputSize) {
                linearOutput[i, j] = matMulResult[i, j] + biases[0, j]
            }
        }
        
        val nextInput = nextLayer?.preForward(linearOutput.toMatrix(), isTraining)?.toFlexibleMatrix() ?: linearOutput
        this.preActivation = nextInput
        
        // Apply activation function efficiently
        for (i in 0 until nextInput.rows) {
            for (j in 0 until nextInput.cols) {
                activatedOutput[i, j] = activation.activate(nextInput[i, j])
            }
        }
        
        output = activatedOutput
        
        if (isTraining) applyDropout()
        return output!!
    }

    /**
     * Performs the forward pass of the layer (Matrix version for backward compatibility).
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()
    }

    /**
     * Updates weights and biases using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        fun correctMoment(m: Double) = m / (1.0 - adamBeta1Power)
        fun correctVelocity(v: Double) = v / (1.0 - adamBeta2Power)

        // Update weights
        for (i in 0 until inputSize) {
            for (j in 0 until outputSize) {
                val gradient = gradientWeights!![i, j]
                momentWeights[i, j] = adamBeta1 * momentWeights[i, j] + (1 - adamBeta1) * gradient
                velocityWeights[i, j] = adamBeta2 * velocityWeights[i, j] + (1 - adamBeta2) * gradient * gradient
                weights[i, j] -= learningRate *
                        correctMoment(momentWeights[i, j]) / (sqrt(correctVelocity(velocityWeights[i, j])) + EPSILON)
            }
        }

        // Update biases
        for (j in 0 until outputSize) {
            val gradient = gradientBiases!![0, j]
            momentBiases[0, j] = adamBeta1 * momentBiases[0, j] + (1 - adamBeta1) * gradient
            velocityBiases[0, j] = adamBeta2 * velocityBiases[0, j] + (1 - adamBeta2) * gradient * gradient
            biases[0, j] -= learningRate *
                    correctMoment(momentBiases[0, j]) / (sqrt(correctVelocity(velocityBiases[0, j])) + EPSILON)
        }
    }

    /**
     * High-performance backward pass using pre-allocated buffers and in-place operations
     */
    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        val preActivationMatrix = preActivation!!
        val batchSize = delta.rows
        
        // Allocate working buffer for current delta if needed
        if (gradWeightBuffer == null || gradWeightBuffer!!.rows != batchSize || gradWeightBuffer!!.cols != outputSize) {
            gradWeightBuffer = createMatrix(batchSize, outputSize, delta.isSinglePrecision)
        }
        
        val currentDelta = gradWeightBuffer!!
        
        // Apply activation derivative in-place
        for (i in 0 until batchSize) {
            for (j in 0 until outputSize) {
                currentDelta[i, j] = delta[i, j] * activation.derivative(preActivationMatrix[i, j])
            }
        }

        val previousOutput = if (previousLayer?.output != null) {
            previousLayer.output!!
        } else {
            currentInput!!
        }

        // Compute weight gradients with regularization - reuse existing gradient matrix
        val weightGrad = com.onyxdevtools.ai.extensions.matrixMultiply(previousOutput.transpose(), currentDelta)
        val featureSizeInv = 1.0 / featureSize
        for (i in 0 until weights.rows) {
            for (j in 0 until weights.cols) {
                gradientWeights!![i, j] = weightGrad[i, j] * featureSizeInv + lambda * weights[i, j]
            }
        }
        
        // Compute bias gradients efficiently
        for (j in 0 until outputSize) {
            var sum = 0.0
            for (i in 0 until batchSize) {
                sum += currentDelta[i, j]
            }
            gradientBiases!![0, j] = sum * featureSizeInv
        }

        // Return gradient for previous layer
        return com.onyxdevtools.ai.extensions.matrixMultiply(currentDelta, weights.transpose())
    }

    /**
     * Performs the backward pass of the layer (Matrix version for backward compatibility).
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        return backward(
            currentInput?.toFlexibleMatrix(),
            delta.toFlexibleMatrix(),
            featureSize,
            nextLayer,
            previousLayer,
            lambda
        ).toMatrix()
    }

    /**
     * Creates a deep copy of the dense layer, including weights, biases, and optimizer states.
     */
    override fun clone(): DenseLayer {
        return DenseLayer(inputSize, outputSize, activation, dropoutRate, precision).also { copy ->
            copy.weights = weights.deepCopy()
            copy.biases = biases.deepCopy()
            copy.momentWeights = momentWeights.deepCopy()
            copy.velocityWeights = velocityWeights.deepCopy()
            copy.momentBiases = momentBiases.deepCopy()
            copy.velocityBiases = velocityBiases.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
            copy.dropoutMask = dropoutMask?.deepCopy()
            copy.gradientWeights = gradientWeights?.deepCopy()
            copy.gradientBiases = gradientBiases?.deepCopy()
        }
    }

    companion object {
        private val random = ThreadLocal.withInitial {
            SplittableRandom(ThreadLocalRandom.current().nextLong())
        }

        private const val EPSILON = 1e-8
        private const val serialVersionUID = 1L
    }
}
