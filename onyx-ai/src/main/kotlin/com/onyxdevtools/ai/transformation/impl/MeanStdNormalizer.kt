package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.extensions.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mean / Standard-Deviation normalizer for **one feature column**.
 *
 * Formula (forward):   `x_norm = (x – μ) / σ`
 * Formula (inverse):   `x      = x_norm * σ + μ`
 *
 * A tiny floor (`1 e-12`) is applied to σ to avoid division-by-zero.
 */
class MeanStdNormalizer(private val unbiased: Boolean = false) : ColumnTransform {
    private var n = 0L
    private var mean = 0.0
    private var m2   = 0.0          // Σ(x-μ)²
    override fun isFitted() = n > 0          // optional helper

    private fun currentStd(): Double =
        sqrt((m2 / if (unbiased) max(1, n - 1) else n).coerceAtLeast(EPSILON))

    /* ── incremental fit ─────────────────────────────────────── */
    override fun fit(values: DoubleArray) {
        values.forEach { v ->
            n++
            val delta = v - mean
            mean += delta / n
            m2   += delta * (v - mean)
        }
    }

    /* ── forward / inverse ───────────────────────────────────── */
    override fun apply(values: DoubleArray): DoubleArray {
        val std = currentStd()
        return DoubleArray(values.size) { i -> (values[i] - mean) / std }
    }

    override fun inverse(values: DoubleArray): DoubleArray {
        val std = currentStd()
        return DoubleArray(values.size) { i -> values[i] * std + mean }
    }

    /**
     * Clone the normalizer so that mutating one instance doesn’t affect another.
     *
     * @return A deep copy of this ColumnTransform
     */
    override fun clone(): ColumnTransform = MeanStdNormalizer(unbiased).also { copy ->
        copy.n     = this.n
        copy.mean  = this.mean
        copy.m2    = this.m2
    }
}
