@file:Suppress("unused")

import java.io.Serializable
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Enum representing common activation functions used in neural networks.
 * Each function is serializable and implements both the forward function `f(x)` and its derivative `d(x)`,
 * used during backpropagation.
 */
enum class Activation : Serializable {

    /**
     * Leaky ReLU (Rectified Linear Unit).
     * Uses a small slope (0.01) for negative input values to avoid dying neurons.
     *
     * f(x) = x if x > 0, else 0.01 * x
     * d(x) = 1 if x > 0, else 0.01
     */
    LEAKY_RELU {
        override fun activate(x: Float): Float = if (x > 0) x else 0.01f * x
        override fun derivative(x: Float): Float = if (x > 0) 1.0f else 0.01f
    },

    /**
     * ReLU (Rectified Linear Unit).
     * Sets all negative values to zero, commonly used in hidden layers.
     *
     * f(x) = x if x > 0, else 0
     * d(x) = 1 if x > 0, else 0
     */
    RELU {
        override fun activate(x: Float): Float = if (x > 0) x else 0.0f
        override fun derivative(x: Float): Float = if (x > 0) 1.0f else 0.0f
    },

    /**
     * ELU (Exponential Linear Unit).
     * Smooth alternative to ReLU. Returns x if x > 0, else α*(exp(x)-1).
     *
     * f(x) = x if x > 0, else α * (exp(x) - 1)
     * d(x) = 1 if x > 0, else f(x) + α
     */
    ELU {
        override fun activate(x: Float): Float = if (x > 0) x else ALPHA.toFloat() * (exp(x) - 1)
        override fun derivative(x: Float): Float = if (x > 0) 1.0f else activate(x) + ALPHA.toFloat()
    },

    /**
     * Tanh (Hyperbolic Tangent).
     * Squashes input to range (-1, 1). Often used in recurrent networks.
     *
     * f(x) = tanh(x)
     * d(x) = 1 - tanh²(x)
     */
    TANH {
        override fun activate(x: Float): Float = tanh(x)
        override fun derivative(x: Float): Float {
            val tanhValue = tanh(x)
            return 1.0f - tanhValue * tanhValue
        }
    },

    /**
     * Linear activation (identity function).
     * Returns the input as-is. Used in output layers for regression problems.
     *
     * f(x) = x
     * d(x) = 1
     */
    LINEAR {
        override fun activate(x: Float): Float = x
        override fun derivative(x: Float): Float = 1.0f
    };

    /**
     * Computes the output of the activation function.
     *
     * @param x The input value.
     * @return The result of applying the activation function.
     */
    abstract fun activate(x: Float): Float

    /**
     * Computes the derivative of the activation function at a given point.
     *
     * @param x The input value.
     * @return The derivative of the activation function at x.
     */
    abstract fun derivative(x: Float): Float

    companion object {
        private const val ALPHA = 1.0

        private const val serialVersionUID: Long = 1L
    }
}
