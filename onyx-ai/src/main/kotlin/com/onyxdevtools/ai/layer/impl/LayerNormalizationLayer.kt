package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
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
 * @see Layer
 * @see MultiHeadAttentionLayer
 */
class LayerNormalizationLayer(private val size: Int) : Layer {

    override var output: Matrix? = null
    override var preActivation: Matrix? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta = FloatArray(size) { 0.0f }
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null
    private var normalized: Matrix? = null

    private var gradGamma: FloatArray? = null
    private var gradBeta: FloatArray? = null

    private var momentGamma = FloatArray(size) { 0.0f }
    private var velocityGamma = FloatArray(size) { 0.0f }
    private var momentBeta = FloatArray(size) { 0.0f }
    private var velocityBeta = FloatArray(size) { 0.0f }

    /**
     * Normalizes the input using layer statistics and applies learned affine transformation.
     */
    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        val batchSize = input.size
        mean = FloatArray(batchSize) { i -> input[i].average().toFloat() }
        variance = FloatArray(batchSize) { i ->
            input[i].sumOf { (it - mean!![i]).pow(2).toDouble() }.toFloat() / size
        }

        normalized = Array(batchSize) { i ->
            FloatArray(size) { j -> (input[i][j] - mean!![i]) / sqrt(variance!![i] + EPSILON) }
        }

        output = Array(batchSize) { i ->
            FloatArray(size) { j -> gamma[j] * normalized!![i][j] + beta[j] }
        }

        return output!!
    }

    /**
     * Computes the backward pass of the layer normalization layer.
     */
    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Matrix {
        val batchSize = delta.size
        val input = currentInput!!

        // Gradients w.r.t. gamma and beta
        gradGamma = FloatArray(size) { j ->
            (0 until batchSize).sumOf { i -> (delta[i][j] * normalized!![i][j]).toDouble() }.toFloat()
        }
        gradBeta = FloatArray(size) { j ->
            (0 until batchSize).sumOf { i -> delta[i][j].toDouble() }.toFloat()
        }

        // Gradient w.r.t. normalized input
        val gradNormalized = Array(batchSize) { i ->
            FloatArray(size) { j -> delta[i][j] * gamma[j] }
        }

        // Gradient w.r.t. variance
        val gradVariance = FloatArray(batchSize) { i ->
            val sum = (0 until size).sumOf { j -> (gradNormalized[i][j] * (input[i][j] - mean!![i])).toDouble() }.toFloat()
            -0.5f * sum / (variance!![i] + EPSILON).pow(1.5f)
        }

        // Gradient w.r.t. mean
        val gradMean = FloatArray(batchSize) { i ->
            val sum1 = (0 until size).sumOf { j -> gradNormalized[i][j].toDouble() }.toFloat()
            -sum1 / sqrt(variance!![i] + EPSILON)
        }

        // Gradient w.r.t. input
        val gradInput = Array(batchSize) { i ->
            FloatArray(size) { j ->
                val term1 = gradNormalized[i][j] / sqrt(variance!![i] + EPSILON)
                val term2 = gradMean[i] / size
                val term3 = gradVariance[i] * 2.0f * (input[i][j] - mean!![i]) / size
                term1 + term2 + term3
            }
        }

        return gradInput
    }

    /**
     * Updates gamma and beta parameters using the Adam optimizer.
     */
    @Suppress("DuplicatedCode")
    override fun updateParameters(
        adamBeta1Power: Float,
        adamBeta2Power: Float,
        adamBeta1: Float,
        adamBeta2: Float,
        learningRate: Float
    ) {
        fun correctMoment(moment: Float) = moment / (1.0f - adamBeta1Power)
        fun correctVelocity(velocity: Float) = velocity / (1.0f - adamBeta2Power)

        for (j in 0 until size) {
            val gradientGamma = gradGamma!![j]
            val gradientBeta = gradBeta!![j]

            momentGamma[j] = adamBeta1 * momentGamma[j] + (1.0f - adamBeta1) * gradientGamma
            velocityGamma[j] = adamBeta2 * velocityGamma[j] + (1.0f - adamBeta2) * gradientGamma * gradientGamma
            gamma[j] = gamma[j] - learningRate * correctMoment(momentGamma[j]) / (sqrt(correctVelocity(velocityGamma[j])) + EPSILON)

            momentBeta[j] = adamBeta1 * momentBeta[j] + (1.0f - adamBeta1) * gradientBeta
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1.0f - adamBeta2) * gradientBeta * gradientBeta
            beta[j] = beta[j] - learningRate * correctMoment(momentBeta[j]) / (sqrt(correctVelocity(velocityBeta[j])) + EPSILON)
        }
    }

    /**
     * Creates a deep copy of the layer normalization layer.
     */
    override fun clone(): Layer {
        return LayerNormalizationLayer(size).also { copy ->
            copy.gamma = gamma.copyOf()
            copy.beta = beta.copyOf()
            copy.momentGamma = momentGamma.copyOf()
            copy.velocityGamma = velocityGamma.copyOf()
            copy.momentBeta = momentBeta.copyOf()
            copy.velocityBeta = velocityBeta.copyOf()
        }
    }

    companion object {
        private const val EPSILON = 1e-8f
    }
}
