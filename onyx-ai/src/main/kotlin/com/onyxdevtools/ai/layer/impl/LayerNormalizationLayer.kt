package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Layer Normalization implementation for neural networks, particularly effective in transformer architectures.
 *
 * Layer normalization normalizes inputs across the feature dimension for each sample independently,
 * unlike batch normalization which normalizes across the batch dimension. This makes it particularly
 * suitable for sequence models where batch sizes may vary or be small.
 *
 * **Mathematical Formulation:**
 * Given input x, layer normalization computes:
 * - μ = mean(x) across feature dimension
 * - σ² = variance(x) across feature dimension
 * - x̂ = (x - μ) / √(σ² + ε)
 * - output = γ ⊙ x̂ + β
 *
 * Where γ (gamma) and β (beta) are learnable affine transformation parameters.
 *
 * **Key Benefits:**
 * - **Training stability**: Reduces internal covariate shift
 * - **Batch size independence**: Works with any batch size, including batch size of 1
 * - **Gradient flow**: Improves gradient propagation in deep networks
 * - **Convergence speed**: Often leads to faster training convergence
 *
 * **Usage in Transformers:**
 * Layer normalization is typically applied:
 * - Before or after multi-head attention layers
 * - Before or after feed-forward layers
 * - Often used in residual connections (Add & Norm)
 *
 * This implementation uses the Adam optimizer for updating the learnable parameters γ and β.
 *
 * @param size The number of features/dimensions to normalize. Must match the last dimension
 *             of input tensors passed to this layer.
 * @param precision The precision to use for internal computations (SINGLE or DOUBLE)
 * @see Layer
 * @see MultiHeadAttentionLayer
 */
class LayerNormalizationLayer(
    private val size: Int,
    private val precision: MatrixPrecision = MatrixPrecision.DOUBLE
) : Layer {

    override var output: FlexibleMatrix? = null
    override var preActivation: FlexibleMatrix? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma: FlexibleMatrix
    private var beta: FlexibleMatrix
    private var mean: FlexibleMatrix? = null
    private var variance: FlexibleMatrix? = null
    private var normalized: FlexibleMatrix? = null

    private var gradGamma: FlexibleMatrix? = null
    private var gradBeta: FlexibleMatrix? = null

    private var momentGamma: FlexibleMatrix
    private var velocityGamma: FlexibleMatrix
    private var momentBeta: FlexibleMatrix
    private var velocityBeta: FlexibleMatrix

    init {
        val isSinglePrecision = precision == MatrixPrecision.SINGLE
        gamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 1.0 }
        beta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        momentGamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        velocityGamma = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        momentBeta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
        velocityBeta = createMatrix(1, size, isSinglePrecision) { _, _ -> 0.0 }
    }

    override fun forward(input: FlexibleMatrix, isTraining: Boolean, nextLayer: Layer?): FlexibleMatrix {
        val batchSize = input.rows
        
        // Compute mean for each sample across features
        mean = createMatrix(batchSize, 1, input.isSinglePrecision) { i, _ -> 
            var sum = 0.0
            for (j in 0 until size) {
                sum += input[i, j]
            }
            sum / size
        }
        
        // Compute variance for each sample across features  
        variance = createMatrix(batchSize, 1, input.isSinglePrecision) { i, _ ->
            var sum = 0.0
            for (j in 0 until size) {
                sum += (input[i, j] - mean!![i, 0]).pow(2)
            }
            sum / size
        }

        // Normalize input
        normalized = createMatrix(batchSize, size, input.isSinglePrecision) { i, j ->
            (input[i, j] - mean!![i, 0]) / sqrt(variance!![i, 0] + EPSILON)
        }

        // Apply affine transformation
        output = createMatrix(batchSize, size, input.isSinglePrecision) { i, j ->
            gamma[0, j] * normalized!![i, j] + beta[0, j]
        }

        return output!!
    }

    /**
     * Normalizes the input using layer statistics and applies learned affine transformation.
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        return forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()
    }

    override fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix {
        val batchSize = delta.rows
        val input = currentInput!!

        // Gradients w.r.t. gamma and beta
        gradGamma = createMatrix(1, size, delta.isSinglePrecision) { _, j ->
            var sum = 0.0
            for (i in 0 until batchSize) {
                sum += delta[i, j] * normalized!![i, j]
            }
            sum
        }
        
        gradBeta = createMatrix(1, size, delta.isSinglePrecision) { _, j ->
            var sum = 0.0
            for (i in 0 until batchSize) {
                sum += delta[i, j]
            }
            sum
        }

        // Gradient w.r.t. normalized input
        val gradNormalized = createMatrix(batchSize, size, delta.isSinglePrecision) { i, j ->
            delta[i, j] * gamma[0, j]
        }

        // Gradient w.r.t. variance
        val gradVariance = createMatrix(batchSize, 1, delta.isSinglePrecision) { i, _ ->
            var sum = 0.0
            for (j in 0 until size) {
                sum += gradNormalized[i, j] * (input[i, j] - mean!![i, 0])
            }
            -0.5 * sum / (variance!![i, 0] + EPSILON).pow(1.5)
        }

        // Gradient w.r.t. mean
        val gradMean = createMatrix(batchSize, 1, delta.isSinglePrecision) { i, _ ->
            var sum1 = 0.0
            for (j in 0 until size) {
                sum1 += gradNormalized[i, j]
            }
            -sum1 / sqrt(variance!![i, 0] + EPSILON)
        }

        // Gradient w.r.t. input
        return createMatrix(batchSize, size, delta.isSinglePrecision) { i, j ->
            val term1 = gradNormalized[i, j] / sqrt(variance!![i, 0] + EPSILON)
            val term2 = gradMean[i, 0] / size
            val term3 = gradVariance[i, 0] * 2.0 * (input[i, j] - mean!![i, 0]) / size
            term1 + term2 + term3
        }
    }

    /**
     * Computes the backward pass of the layer normalization layer.
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
     * Updates gamma and beta parameters using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        fun correctMoment(moment: Double) = moment / (1.0 - adamBeta1Power)
        fun correctVelocity(velocity: Double) = velocity / (1.0 - adamBeta2Power)

        for (j in 0 until size) {
            val gradientGamma = gradGamma!![0, j]
            val gradientBeta = gradBeta!![0, j]

            momentGamma[0, j] = adamBeta1 * momentGamma[0, j] + (1 - adamBeta1) * gradientGamma
            velocityGamma[0, j] = adamBeta2 * velocityGamma[0, j] + (1 - adamBeta2) * gradientGamma * gradientGamma
            gamma[0, j] -= learningRate * correctMoment(momentGamma[0, j]) / (sqrt(correctVelocity(velocityGamma[0, j])) + EPSILON)

            momentBeta[0, j] = adamBeta1 * momentBeta[0, j] + (1 - adamBeta1) * gradientBeta
            velocityBeta[0, j] = adamBeta2 * velocityBeta[0, j] + (1 - adamBeta2) * gradientBeta * gradientBeta
            beta[0, j] -= learningRate * correctMoment(momentBeta[0, j]) / (sqrt(correctVelocity(velocityBeta[0, j])) + EPSILON)
        }
    }

    /**
     * Creates a deep copy of the layer normalization layer.
     */
    override fun clone(): Layer {
        return LayerNormalizationLayer(size, precision).also { copy ->
            copy.gamma = gamma.deepCopy()
            copy.beta = beta.deepCopy()
            copy.momentGamma = momentGamma.deepCopy()
            copy.velocityGamma = velocityGamma.deepCopy()
            copy.momentBeta = momentBeta.deepCopy()
            copy.velocityBeta = velocityBeta.deepCopy()
            copy.output = output?.deepCopy()
            copy.preActivation = preActivation?.deepCopy()
            copy.mean = mean?.deepCopy()
            copy.variance = variance?.deepCopy()
            copy.normalized = normalized?.deepCopy()
            copy.gradGamma = gradGamma?.deepCopy()
            copy.gradBeta = gradBeta?.deepCopy()
        }
    }

    companion object {
        private const val EPSILON = 1e-8
    }
}
