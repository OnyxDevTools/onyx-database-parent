package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.extensions.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * Incremental Max-Abs scaler (keeps range in **[-1 , 1]**).
 *
 * Every call to [fit] **widens** the stored `maxAbs` so that no value seen
 * in any batch will ever scale outside ±1.
 */
class MaxAbsScaler : ColumnTransform, Serializable {

    private var maxAbs: Float = 0.0f
    private var fitted = false

    /* ---------- contract ---------- */

    override fun isFitted(): Boolean = fitted

    /* ---------- incremental fit ---------- */

    override fun fit(values: FloatArray) {
        val batchMax = values.maxOfOrNull { abs(it) } ?: 0.0f
        maxAbs = max(maxAbs, batchMax)
        fitted = true
    }

    /* ---------- forward transform ---------- */

    override fun apply(values: FloatArray): FloatArray {
        if (!fitted) fit(values)        // lazy first-fit

        // near-constant column → map to zeros
        if (maxAbs < EPSILON) return FloatArray(values.size)

        return FloatArray(values.size) { i -> values[i] / maxAbs }
    }

    /* ---------- inverse transform ---------- */

    override fun inverse(values: FloatArray): FloatArray {
        if (!fitted) throw IllegalStateException("inverse() before fit/apply")

        if (maxAbs < EPSILON) return FloatArray(values.size)

        return FloatArray(values.size) { i -> values[i] * maxAbs }
    }

    /**
     * Clone the transform so that if the state is mutated during stream fitting,
     * it does not impact the best model.
     *
     * @return An identical deep copy of the ColumnTransform
     */
    override fun clone(): ColumnTransform = MaxAbsScaler().also { copy ->
        copy.maxAbs = this.maxAbs
        copy.fitted = this.fitted
    }
}
