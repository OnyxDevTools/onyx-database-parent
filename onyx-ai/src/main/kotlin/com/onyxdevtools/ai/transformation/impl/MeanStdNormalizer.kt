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
    private var mean = 0.0f
    private var m2   = 0.0f          // Σ(x-μ)²
    override fun isFitted() = n > 0          // optional helper

    private fun currentStd(): Float =
        sqrt((m2 / if (unbiased) max(1, n - 1) else n).coerceAtLeast(EPSILON.toFloat())).toFloat()

    /* ── incremental fit ─────────────────────────────────────── */
    override fun fit(values: FloatArray) {
        values.forEach { v ->
            n++
            val delta = v - mean
            mean += delta / n
            m2   += delta * (v - mean)
        }
    }

    /* ── forward / inverse ───────────────────────────────────── */
    override fun apply(values: FloatArray): FloatArray {
        val std = currentStd()
        return FloatArray(values.size) { i -> (values[i] - mean) / std }
    }

    override fun inverse(values: FloatArray): FloatArray {
        val std = currentStd()
        return FloatArray(values.size) { i -> values[i] * std + mean }
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
