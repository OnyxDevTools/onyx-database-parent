@file:Suppress("DuplicatedCode")

package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class QuantileTransformer : ColumnTransform, Serializable {

    /* ── state ─────────────────────────────────────────────────── */
    private val values = mutableListOf<Float>()      // unique, ascending
    private var sortedVals = FloatArray(0)
    private var quantiles  = FloatArray(0)
    private var fitted = false

    /* ── contract ──────────────────────────────────────────────── */
    override fun isFitted(): Boolean = fitted

    /* ── incremental fit ───────────────────────────────────────── */
    override fun fit(values: FloatArray) {
        if (values.isEmpty()) return

        if (!fitted) {
            this.values += values.toSet()
            this.values.sort()
        } else {
            // merge new uniques into the sorted list (O(n+m))
            val uniques = values.toSet()
            val merged  = FloatArray(this.values.size + uniques.size)  // upper bound
            var i = 0
            var k = 0
            val itUni = uniques.sorted().iterator()
            var nxtUni = if (itUni.hasNext()) itUni.next() else null

            while (i < this.values.size || nxtUni != null) {
                val vCur = if (i < this.values.size) this.values[i] else Float.POSITIVE_INFINITY
                val vNew = nxtUni ?: Float.POSITIVE_INFINITY
                when {
                    vCur < vNew - EPSILON -> merged[k++] = this.values[i++]
                    vNew < vCur - EPSILON -> { merged[k++] = vNew; nxtUni = if (itUni.hasNext()) itUni.next() else null }
                    else -> { merged[k++] = vCur; i++; nxtUni = if (itUni.hasNext()) itUni.next() else null }
                }
            }
            this.values.clear()
            this.values += merged.copyOf(k).asList()
        }

        rebuildTables()
        fitted = true
    }

    private fun rebuildTables() {
        sortedVals = values.toFloatArray()
        val k = sortedVals.size
        quantiles  = FloatArray(k) { idx ->
            if (k == 1) 0.5f else idx.toFloat() / (k - 1)
        }
    }

    /* ── forward transform (value → uniform) ───────────────────── */
    override fun apply(values: FloatArray): FloatArray {
        if (!fitted) fit(values)

        val k = sortedVals.size
        if (k == 1) return FloatArray(values.size) { 0.5f }

        return FloatArray(values.size) { idx ->
            val x = values[idx]

            // binary search for insertion point
            var lo = 0
            var hi = k - 1
            while (lo <= hi) {
                val mid = (lo + hi).ushr(1)
                when {
                    abs(sortedVals[mid] - x) < EPSILON -> { lo = mid; break }
                    sortedVals[mid] < x -> lo = mid + 1
                    else -> hi = mid - 1
                }
            }
            val upper = min(lo, k - 1)
            val lower = max(upper - 1, 0)

            if (upper == lower) return@FloatArray quantiles[upper]

            val xLow = sortedVals[lower];  val xHigh = sortedVals[upper]
            val qLow = quantiles[lower];   val qHigh = quantiles[upper]

            if (abs(xHigh - xLow) < EPSILON) qLow
            else {
                val t = (x - xLow) / (xHigh - xLow)
                qLow + t * (qHigh - qLow)
            }
        }
    }

    /* ── inverse transform (uniform → value) ───────────────────── */
    override fun inverse(values: FloatArray): FloatArray {
        if (!fitted) throw IllegalStateException("QuantileTransformer.inverse() before fit/apply")

        val k = sortedVals.size
        if (k == 1) return FloatArray(values.size) { sortedVals[0] }

        return FloatArray(values.size) { idx ->
            val q = values[idx].coerceIn(0.0f, 1.0f)

            val pos = (q * (k - 1)).roundToInt()
            val upper = min(max(pos, 1), k - 1)
            val lower = upper - 1

            val qLow = quantiles[lower]; val qHigh = quantiles[upper]
            val xLow = sortedVals[lower]; val xHigh = sortedVals[upper]

            if (abs(qHigh - qLow) < EPSILON) xLow
            else {
                val t = (q - qLow) / (qHigh - qLow)
                xLow + t * (xHigh - xLow)
            }
        }
    }

    /**
     * Clone the transformer and all its state so future fits on one copy
     * do not affect the other.
     *
     * @return A deep copy of this QuantileTransformer.
     */
    override fun clone(): ColumnTransform = QuantileTransformer().also { copy ->
        copy.values.addAll(this.values)
        copy.sortedVals = this.sortedVals.copyOf()
        copy.quantiles  = this.quantiles.copyOf()
        copy.fitted     = this.fitted
    }
}
