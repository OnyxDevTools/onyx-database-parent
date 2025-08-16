package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Tensor
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
 * @see CachedMultiHeadAttentionLayer
 */
class LayerNormalizationLayer(private val size: Int) : Layer {

    override var output: Tensor? = null
    override var preActivation: Tensor? = null
    override val activation: Activation = Activation.LINEAR

    private var gamma = FloatArray(size) { 1.0f }
    private var beta = FloatArray(size) { 0.0f }
    private var mean: FloatArray? = null
    private var variance: FloatArray? = null
    private var normalized: Tensor? = null

    private var gradGamma: FloatArray? = null
    private var gradBeta: FloatArray? = null

    private var momentGamma = FloatArray(size) { 0.0f }
    private var velocityGamma = FloatArray(size) { 0.0f }
    private var momentBeta = FloatArray(size) { 0.0f }
    private var velocityBeta = FloatArray(size) { 0.0f }

    /**
     * Normalizes the input using layer statistics and applies learned affine transformation.
     */
    override fun forward(input: Tensor, isTraining: Boolean, nextLayer: Layer?): Tensor {
        val batchSize = input.size
        mean = FloatArray(batchSize) { i -> input[i].average().toFloat() }
        variance = FloatArray(batchSize) { i ->
            input[i].sumOf { (it - mean!![i]).pow(2).toDouble() }.toFloat() / size
        }

        normalized = Tensor(batchSize, size)
        output = Tensor(batchSize, size)

        val cols = size
        for (i in 0 until batchSize) {
            val mu  = mean!![i]
            val inv = 1f / sqrt(variance!![i] + EPSILON)  // computed once per row

            var j = 0
            while (j < cols) {
                val z = (input[i, j] - mu) * inv
                normalized!![i, j] = z
                output!![i, j] = z * gamma[j] + beta[j]
                j++
            }
        }

        return output!!
    }

    override fun backward(
        currentInput: Tensor?,
        delta: Tensor,
        featureSize: Float,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Float
    ): Tensor {
        val x = requireNotNull(currentInput) { "currentInput required" }

        val batchSize = delta.size          // rows
        val width     = this.size           // cols (feature size)

        // --- outputs of this backward step ---
        val gradInput = Tensor(batchSize, width)

        // --- grads wrt gamma/beta (per feature) ---
        val gradGammaLocal = FloatArray(width) // sum_i delta * normalized
        val gradBetaLocal  = FloatArray(width) // sum_i delta

        // --- needed intermediates (per row) ---
        val gradMean      = FloatArray(batchSize)
        val gradVariance  = FloatArray(batchSize)

        // raw buffers for speed
        val xData   = x.data
        val dData   = delta.data
        val nData   = normalized!!.data      // from forward: (x - mu) / sqrt(var + eps)
        val outData = gradInput.data
        val gammaW  = gamma                  // FloatArray[length = width]
        val meanV   = mean!!                  // FloatArray[length = batchSize]
        val varV    = variance!!              // FloatArray[length = batchSize]

        var base = 0
        var row  = 0
        while (row < batchSize) {
            val mu      = meanV[row]
            val varEps  = varV[row] + EPSILON
            val inv     = 1f / sqrt(varEps)         // (var+eps)^(-1/2)
            val inv3    = inv * inv * inv           // (var+eps)^(-3/2)

            var sum1 = 0f                            // Σ_j (delta * gamma)
            var sum2 = 0f                            // Σ_j (delta * gamma * (x - mu))

            // ---- Pass 1 over row: accumulate grads for gamma/beta and row sums ----
            var j = 0
            while (j < width) {
                val d   = dData[base + j]
                val xij = xData[base + j]
                val gnj = d * gammaW[j]             // gradNormalized(i,j)
                sum1 += gnj
                sum2 += gnj * (xij - mu)

                gradBetaLocal[j]  += d
                gradGammaLocal[j] += d * nData[base + j] // use cached normalized
                j++
            }

            gradMean[row]     = -sum1 * inv
            gradVariance[row] = -0.5f * sum2 * inv3

            // ---- Pass 2 over row: write gradInput ----
            val term2    = gradMean[row] / width
            val gvScaled = (2f * gradVariance[row]) / width

            j = 0
            while (j < width) {
                val d   = dData[base + j]
                val xij = xData[base + j]
                val gnj = d * gammaW[j]
                outData[base + j] = inv * gnj + term2 + gvScaled * (xij - mu)
                j++
            }

            base += width
            row++
        }

        // publish parameter grads
        gradGamma = gradGammaLocal
        gradBeta  = gradBetaLocal

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
            gamma[j] =
                gamma[j] - learningRate * correctMoment(momentGamma[j]) / (sqrt(correctVelocity(velocityGamma[j])) + EPSILON)

            momentBeta[j] = adamBeta1 * momentBeta[j] + (1.0f - adamBeta1) * gradientBeta
            velocityBeta[j] = adamBeta2 * velocityBeta[j] + (1.0f - adamBeta2) * gradientBeta * gradientBeta
            beta[j] =
                beta[j] - learningRate * correctMoment(momentBeta[j]) / (sqrt(correctVelocity(velocityBeta[j])) + EPSILON)
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
