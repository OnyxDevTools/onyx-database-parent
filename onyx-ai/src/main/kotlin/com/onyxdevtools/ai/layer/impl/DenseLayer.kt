package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream
import kotlin.math.sqrt
import kotlin.random.Random

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
 * @see Layer
 * @see Activation
 */
class DenseLayer(
    private val inputSize: Int,
    private val outputSize: Int,
    override val activation: Activation,
    val dropoutRate: Float = 0.0f
) : Layer {

    override var preActivation: Matrix? = null
    override var output: Matrix? = null

    var weights: Matrix
    private var biases = FloatArray(outputSize) { 0.0f }

    private var momentWeights: Matrix
    private var velocityWeights: Matrix
    private var momentBiases = FloatArray(outputSize)
    private var velocityBiases = FloatArray(outputSize)

    private var dropoutMask: Matrix? = null
    private var gradientWeights: Matrix? = null
    private var gradientBiases: FloatArray? = null

    init {
        val weightInitLimit: Float = when (activation) {
            Activation.RELU, Activation.LEAKY_RELU -> sqrt((2.0f / inputSize.toFloat()).toDouble()).toFloat()
            else -> sqrt((6.0f / (inputSize.toFloat() + outputSize.toFloat())).toDouble()).toFloat()
        }
        weights = Array(inputSize) { FloatArray(outputSize) { (Random.nextFloat() * 2.0f - 1.0f) * weightInitLimit } }
        momentWeights = Array(inputSize) { FloatArray(outputSize) }
        velocityWeights = Array(inputSize) { FloatArray(outputSize) }
    }

    /**
     * Applies dropout to the layer's output using a shared dropout mask and parallelized random number generation.
     */
    private fun applyDropout() {
        val activations = output ?: error("Layer output must not be null before applying dropout.")
        if (dropoutRate == 0.0f) return

        val rows = activations.size
        val cols = activations[0].size
        val keepProbability = 1.0f - dropoutRate
        val scaleFactor = 1.0f / keepProbability

        if (dropoutMask == null || dropoutMask!!.size != rows || dropoutMask!![0].size != cols) {
            dropoutMask = Array(rows) { FloatArray(cols) }
        }

        val mask = dropoutMask!!

        IntStream.range(0, rows).parallel().forEach { rowIndex ->
            val rng = random.get()
            val rowMask = mask[rowIndex]
            var colIndex = 0
            while (colIndex <= cols - 4) {
                val r1 = rng.nextFloat()
                val r2 = rng.nextFloat()
                val r3 = rng.nextFloat()
                val r4 = rng.nextFloat()
                rowMask[colIndex] = if (r1 < keepProbability) scaleFactor else 0.0f
                rowMask[colIndex + 1] = if (r2 < keepProbability) scaleFactor else 0.0f
                rowMask[colIndex + 2] = if (r3 < keepProbability) scaleFactor else 0.0f
                rowMask[colIndex + 3] = if (r4 < keepProbability) scaleFactor else 0.0f
                colIndex += 4
            }
            while (colIndex < cols) {
                rowMask[colIndex] = if (rng.nextFloat() < keepProbability) scaleFactor else 0.0f
                colIndex++
            }
        }

        output = elementWiseMultiply(activations, mask)
    }

    /**
     * Performs the forward pass of the layer.
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        val linearOutput = addVectorToRows(matrixMultiply(input, weights), biases)
        val nextInput = nextLayer?.preForward(linearOutput, isTraining) ?: linearOutput
        this.preActivation = nextInput
        output = applyElementWise(nextInput, activation::activate)
        if (isTraining) applyDropout()
        return output!!
    }

    /**
     * Updates weights and biases using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
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
     * Performs the backward pass of the layer.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
        // CHANGED: always apply activation derivative; no BN special-case
        val currentDelta = elementWiseMultiply(
            delta,
            applyElementWise(preActivation!!, activation::derivative)
        )

        val previousOutput = previousLayer?.output ?: currentInput!!

        gradientWeights = add(
            scalarMultiply(matrixMultiply(transpose(previousOutput), currentDelta), 1.0f / featureSize),
            scalarMultiply(weights, lambda)
        )
        gradientBiases = sumColumns(currentDelta).map { it / featureSize }.toFloatArray()

        return matrixMultiply(currentDelta, transpose(weights))
    }

    /**
     * Creates a deep copy of the dense layer, including weights, biases, and optimizer states.
     */
    override fun clone(): DenseLayer {
        return DenseLayer(inputSize, outputSize, activation, dropoutRate).also { copy ->
            copy.weights = weights.deepCopy()
            copy.biases = biases.copyOf()
            copy.momentWeights = momentWeights.deepCopy()
            copy.velocityWeights = velocityWeights.deepCopy()
            copy.momentBiases = momentBiases.copyOf()
            copy.velocityBiases = velocityBiases.copyOf()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
            copy.dropoutMask = dropoutMask?.deepCopy()
            copy.gradientWeights = gradientWeights?.deepCopy()
            copy.gradientBiases = gradientBiases?.copyOf()
        }
    }


    companion object {
        private val random = ThreadLocal.withInitial {
            SplittableRandom(ThreadLocalRandom.current().nextLong())
        }

        private const val EPSILON = 1e-8f
        private const val serialVersionUID = 1L
    }
}
