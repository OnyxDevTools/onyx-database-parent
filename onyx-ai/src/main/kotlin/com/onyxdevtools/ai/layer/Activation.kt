package com.onyxdevtools.ai.layer

import java.io.Serializable

enum class Activation : Serializable {
    LEAKY_RELU {
        override fun f(x: Double) = if (x > 0) x else 0.01 * x
        override fun d(x: Double) = if (x > 0) 1.0 else 0.01
    },
    LINEAR {
        override fun f(x: Double) = x
        override fun d(x: Double) = 1.0
    };

    abstract fun f(x: Double): Double
    abstract fun d(x: Double): Double
}
