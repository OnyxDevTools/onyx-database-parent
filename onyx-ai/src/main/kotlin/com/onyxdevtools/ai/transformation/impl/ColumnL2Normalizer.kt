package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable
import kotlin.math.sqrt

/**
 * Incremental L2 normaliser.
 *
 * Each call to [fit] **adds** `Σ xᵢ²` from the new batch to the running
 * total, so `apply()` always divides by the norm computed over *all* data
 * seen so far.
 */
class ColumnL2Normalizer : ColumnTransform, Serializable {

    private var sumSq = 0.0f               // running Σ x²
    private var norm  = 1.0f               // √sumSq (updated after each fit)
    private var fitted = false

    /* ---------- contract ---------- */

    override fun isFitted(): Boolean = fitted

    /* ---------- incremental fit ---------- */

    override fun fit(values: FloatArray) {
        // accumulate squared magnitude
        sumSq += values.sumOf { (it * it).toDouble() }.toFloat()
        norm   = sqrt(sumSq).coerceAtLeast(EPSILON)
        fitted = true
    }

    /* ---------- forward transform ---------- */

    override fun apply(values: FloatArray): FloatArray {
        if (!fitted) fit(values)          // lazy first-fit

        return if (norm < EPSILON * 10)
            FloatArray(values.size)      // constant/zero column → zeros
        else
            FloatArray(values.size) { i -> values[i] / norm }
    }

    /* ---------- inverse transform ---------- */

    override fun inverse(values: FloatArray): FloatArray {
        if (!fitted) throw IllegalStateException("inverse() before fit/apply")

        return if (norm < EPSILON * 10)
            FloatArray(values.size)
        else
            FloatArray(values.size) { i -> values[i] * norm }
    }

    /**
     * Clone the transform so that if the state is mutated during stream fitting,
     * it does not impact the best model.
     *
     * @return An identical deep copy of the ColumnTransform
     */
    override fun clone(): ColumnTransform = ColumnL2Normalizer().also { copy ->
        copy.sumSq   = this.sumSq
        copy.norm    = this.norm
        copy.fitted  = this.fitted
    }
}
