package com.onyxdevtools.ai.transformation

import java.io.Serializable

typealias ColumnTransforms = List<ColumnTransform?>

/**
 * Interface for transformations that operate on entire matrices.
 *
 * A [ColumnTransform] may optionally collect statistics from a dataset via [fit],
 * which are then used by [apply] or [inverse]. Examples include standardization,
 * normalization, or whitening transforms.
 *
 * Implementations are expected to be serializable so they can be persisted and reused.
 */
interface ColumnTransform : Serializable {

    /**
     * Computes any necessary statistics from the input column to prepare for transformation.
     *
     * This method is a no-op by default and can be overridden by stateful transforms.
     *
     * @param values The input column to fit on.
     */
    fun fit(values: DoubleArray) = Unit

    /**
     * Applies the transformation to the provided column.
     *
     * @param values The input column to transform.
     * @return A new column with the transformation applied.
     */
    fun apply(values: DoubleArray): DoubleArray = values

    /**
     * Reverses the transformation previously applied to a column.
     *
     * This method returns the original column unchanged by default, but can be overridden
     * for transforms where an inverse exists (e.g., standardization).
     *
     * @param values The transformed column to invert.
     * @return A column representing the inverse transformation, if supported.
     */
    fun inverse(values: DoubleArray): DoubleArray = values

    /**
     * Identify whether the transform has been fit
     *
     * @return Boolean true for whether it has been fit
     */
    fun isFitted(): Boolean = true

    /**
     * Clone the transform so that if the state is mutated during stream fitting, it does not impact the best model
     *
     * @return An identical deep copy of the ColumnTransform
     */
    fun clone(): ColumnTransform
}

fun ColumnTransforms.fitAndTransform(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val rows = matrix.size
    val cols = matrix[0].size
    require(size == cols) { "Must supply one ColumnTransform (or null) per column" }

    val result = Array(rows) { matrix[it].copyOf() }

    for (c in 0 until cols) {
        val t = this[c] ?: continue

        val col = DoubleArray(rows) { r -> result[r][c] }
        t.fit(col)
        val transformed = t.apply(col)

        for (r in 0 until rows) result[r][c] = transformed[r]
    }
    return result
}

/** Apply already-fitted transforms to a new matrix. */
fun ColumnTransforms.apply(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val rows = matrix.size
    val result = Array(rows) { matrix[it].copyOf() }

    forEachIndexed { c, t ->
        if (t == null) return@forEachIndexed
        val col = DoubleArray(rows) { r -> result[r][c] }
        val transformed = t.apply(col)
        for (r in 0 until rows) result[r][c] = transformed[r]
    }
    return result
}

/** Restore original scale column-by-column. */
fun ColumnTransforms.inverse(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val rows = matrix.size
    val result = Array(rows) { matrix[it].copyOf() }

    forEachIndexed { c, t ->
        if (t == null) return@forEachIndexed
        val col = DoubleArray(rows) { r -> result[r][c] }
        val restored = t.inverse(col)
        for (r in 0 until rows) result[r][c] = restored[r]
    }
    return result
}
