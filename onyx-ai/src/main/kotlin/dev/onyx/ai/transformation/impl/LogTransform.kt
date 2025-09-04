package dev.onyx.ai.transformation.impl

import dev.onyx.ai.Constants.EPSILON
import dev.onyx.ai.transformation.ColumnTransform
import kotlin.math.exp
import kotlin.math.ln
import java.io.Serializable

/**
 * Natural-log transform for a **single feature column**.
 *
 * A tiny constant [epsilon] is added to every element so that `ln(0)` and
 * negative inputs are avoided.
 * The inverse simply applies `exp(x) – epsilon`.
 *
 * @param epsilon Value added before taking the log (default = `EPSILON`).
 */
class LogTransform(
    private val epsilon: Float = EPSILON
) : ColumnTransform, Serializable {

    /** Forward pass: `ln(x + ε)` */
    override fun apply(values: FloatArray): FloatArray =
        FloatArray(values.size) { idx -> ln(values[idx] + epsilon) }

    /** Reverse pass: `exp(x) - ε` */
    override fun inverse(values: FloatArray): FloatArray =
        FloatArray(values.size) { idx -> exp(values[idx]) - epsilon }

    /**
     * Clone the transform so that if the state is mutated elsewhere,
     * it does not affect this instance.
     *
     * @return An identical deep copy of the ColumnTransform
     */
    override fun clone(): ColumnTransform = LogTransform(epsilon)
}
