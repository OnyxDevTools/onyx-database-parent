package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.extensions.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/**
 * Streaming-friendly Min-Max scaler (single column).
 *
 * Every call to [fit] updates `dataMin` / `dataMax` with the batch extrema:
 * ```
 * dataMin = min(dataMin, batchMin)
 * dataMax = max(dataMax, batchMax)
 * ```
 * Therefore **apply()** always maps into the same target interval for all
 * batches seen so far.
 */
class MinMaxScaler(
    private val minRange: Double = 0.0,
    private val maxRange: Double = 1.0
) : ColumnTransform, Serializable {

    init { require(minRange < maxRange) { "minRange must be < maxRange" } }

    private val targetRange = maxRange - minRange

    private var dataMin = Double.POSITIVE_INFINITY
    private var dataMax = Double.NEGATIVE_INFINITY
    private var fitted  = false

    /* ---------- contract ---------- */

    override fun isFitted(): Boolean = fitted

    /* ---------- incremental fit ---------- */

    override fun fit(values: DoubleArray) {
        if (values.isEmpty()) return
        dataMin = min(dataMin, values.minOrNull() ?: dataMin)
        dataMax = max(dataMax, values.maxOrNull() ?: dataMax)
        fitted = true
    }

    /* ---------- forward ---------- */

    override fun apply(values: DoubleArray): DoubleArray {
        if (!fitted) fit(values)            // lazy first-fit

        val scale = dataMax - dataMin
        if (abs(scale) < EPSILON) {         // constant column
            val mid = minRange + targetRange / 2.0
            return DoubleArray(values.size) { mid }
        }

        return DoubleArray(values.size) { i ->
            minRange + (values[i] - dataMin) * targetRange / scale
        }
    }

    /* ---------- inverse ---------- */

    override fun inverse(values: DoubleArray): DoubleArray {
        if (!fitted) throw IllegalStateException("inverse() before fit/apply")

        val scale = dataMax - dataMin
        if (abs(scale) < EPSILON)           // constant column
            return DoubleArray(values.size) { dataMin }

        return DoubleArray(values.size) { i ->
            dataMin + (values[i] - minRange) * scale / targetRange
        }
    }

    /**
     * Clone the scaler so that if the state is mutated during stream fitting,
     * it does not impact the best model.
     *
     * @return A deep copy of this ColumnTransform
     */
    override fun clone(): ColumnTransform = MinMaxScaler(minRange, maxRange).also { copy ->
        copy.dataMin = this.dataMin
        copy.dataMax = this.dataMax
        copy.fitted  = this.fitted
    }
}
