package dev.onyx.ai.transformation.impl

import dev.onyx.ai.transformation.ColumnTransform
import java.io.Serializable
import kotlin.math.exp

/**
 * **Exponential time-decay** for a single feature column.
 *
 * Row *i* gets multiplied by `exp(-λ · i)` so that newer samples (lower index)
 * carry more weight.
 * The inverse multiplies by `exp( λ · i)` to restore the original scale.
 *
 * @param lambda Decay rate (λ ≥ 0). Larger λ → faster decay.
 */
class TimeDecayTransform(
    private val lambda: Float
) : ColumnTransform, Serializable {

    init {
        require(lambda >= 0.0) { "λ (lambda) must be non-negative." }
    }

    /* ---------- forward ---------- */

    override fun apply(values: FloatArray): FloatArray =
        FloatArray(values.size) { i ->
            values[i] * exp(-lambda * i)
        }

    /* ---------- inverse ---------- */

    override fun inverse(values: FloatArray): FloatArray =
        FloatArray(values.size) { i ->
            values[i] * exp(lambda * i)
        }

    /**
     * Clone the transform so that mutating one instance doesn’t affect another.
     *
     * @return A deep copy with the same decay rate.
     */
    override fun clone(): ColumnTransform = TimeDecayTransform(lambda)
}
