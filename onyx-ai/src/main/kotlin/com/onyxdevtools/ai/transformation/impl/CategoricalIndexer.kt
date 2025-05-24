package com.onyxdevtools.ai.transformation.impl

import com.onyxdevtools.ai.transformation.ColumnTransform
import java.io.Serializable

/**
 * Incremental label-encoder (single column → integer index).
 *
 *  * **fit(values)** may be called many times – each call *adds* unseen
 *    categories to the mapping; it never forgets previous ones.
 *  * **isFitted()** returns true as soon as at least one category
 *    has been observed.
 *
 * @param handleUnknown Behaviour when `apply()` meets an unseen category
 *        * `"error"` (default) – throw `IllegalArgumentException`
 *        * `"nan"`            – emit `NaN`
 *        * `"new"`            – create a new index on the fly
 */
class CategoricalIndexer(
    private val handleUnknown: String = "new"   // "error" | "nan" | "new"
) : ColumnTransform, Serializable {

    private val categories = mutableListOf<Double>()      // keeps insertion order
    private val catToIndex = hashMapOf<Double, Int>()
    private var fitted = false

    /* ---------- contract ---------- */

    override fun isFitted(): Boolean = fitted

    /* ---------- incremental fit ---------- */

    override fun fit(values: DoubleArray) {
        values.forEach { v ->
            if (v !in catToIndex) {
                val newIdx = categories.size
                categories += v
                catToIndex[v] = newIdx
            }
        }
        fitted = categories.isNotEmpty()
    }

    /* ---------- forward transform ---------- */

    override fun apply(values: DoubleArray): DoubleArray {
        if (!fitted) fit(values)                     // lazy first-fit

        return DoubleArray(values.size) { idx ->
            val v = values[idx]
            val idxKnown = catToIndex[v] ?: when (handleUnknown) {
                "error" -> throw IllegalArgumentException("Unknown category $v")
                "nan"   -> return@DoubleArray Double.NaN
                "new"   -> {
                    val newIdx = categories.size
                    categories += v
                    catToIndex[v] = newIdx
                    newIdx
                }
                else   -> error("handleUnknown must be 'error', 'nan', or 'new'")
            }
            idxKnown.toDouble()
        }
    }

    /* ---------- inverse transform ---------- */

    override fun inverse(values: DoubleArray): DoubleArray =
        DoubleArray(values.size) { idx ->
            val code = values[idx].toInt()
            if (code in categories.indices) categories[code] else Double.NaN
        }

    /**
     * Clone the transform so that if the state is mutated during stream fitting,
     * it does not impact the best model.
     *
     * @return An identical deep copy of the ColumnTransform
     */
    override fun clone(): ColumnTransform = CategoricalIndexer(handleUnknown).also { copy ->
        copy.categories.addAll(this.categories)
        copy.catToIndex.putAll(this.catToIndex)
        copy.fitted = this.fitted
    }
}
