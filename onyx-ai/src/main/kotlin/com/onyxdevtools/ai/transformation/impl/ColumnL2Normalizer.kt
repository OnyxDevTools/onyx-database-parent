package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.extensions.EPSILON
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

    private var sumSq = 0.0               // running Σ x²
    private var norm  = 1.0               // √sumSq (updated after each fit)
    private var fitted = false

    /* ---------- contract ---------- */

    override fun isFitted(): Boolean = fitted

    /* ---------- incremental fit ---------- */

    override fun fit(values: DoubleArray) {
        // accumulate squared magnitude
        sumSq += values.sumOf { it * it }
        norm   = sqrt(sumSq).coerceAtLeast(EPSILON)
        fitted = true
    }

    /* ---------- forward transform ---------- */

    override fun apply(values: DoubleArray): DoubleArray {
        if (!fitted) fit(values)          // lazy first-fit

        return if (norm < EPSILON * 10)
            DoubleArray(values.size)      // constant/zero column → zeros
        else
            DoubleArray(values.size) { i -> values[i] / norm }
    }

    /* ---------- inverse transform ---------- */

    override fun inverse(values: DoubleArray): DoubleArray {
        if (!fitted) throw IllegalStateException("inverse() before fit/apply")

        return if (norm < EPSILON * 10)
            DoubleArray(values.size)
        else
            DoubleArray(values.size) { i -> values[i] * norm }
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
