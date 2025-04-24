@file:Suppress("unused")

import java.io.Serializable
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
        override fun activate(x: Double): Double = if (x > 0) x else 0.01 * x
        override fun derivative(x: Double): Double = if (x > 0) 1.0 else 0.01
    },

    /**
     * ReLU (Rectified Linear Unit).
     * Sets all negative values to zero, commonly used in hidden layers.
     *
     * f(x) = x if x > 0, else 0
     * d(x) = 1 if x > 0, else 0
     */
    RELU {
        override fun activate(x: Double): Double = if (x > 0) x else 0.0
        override fun derivative(x: Double): Double = if (x > 0) 1.0 else 0.0
    },

    /**
     * Tanh (Hyperbolic Tangent).
     * Squashes input to range (-1, 1). Often used in recurrent networks.
     *
     * f(x) = tanh(x)
     * d(x) = 1 - tanh²(x)
     */
    TANH {
        override fun activate(x: Double): Double = tanh(x)
        override fun derivative(x: Double): Double {
            val tanhValue = tanh(x)
            return 1.0 - tanhValue * tanhValue
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
        override fun activate(x: Double): Double = x
        override fun derivative(x: Double): Double = 1.0
    };

    /**
     * Computes the output of the activation function.
     *
     * @param x The input value.
     * @return The result of applying the activation function.
     */
    abstract fun activate(x: Double): Double

    /**
     * Computes the derivative of the activation function at a given point.
     *
     * @param x The input value.
     * @return The derivative of the activation function at x.
     */
    abstract fun derivative(x: Double): Double

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
