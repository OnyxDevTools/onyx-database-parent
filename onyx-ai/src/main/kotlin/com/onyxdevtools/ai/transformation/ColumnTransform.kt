package com.onyxdevtools.ai.transformation

import com.onyxdevtools.ai.Tensor

typealias ColumnTransforms = List<ColumnTransform?>

interface ColumnTransform : java.io.Serializable {
    fun fit(values: FloatArray) = Unit
    fun apply(values: FloatArray): FloatArray = values
    fun inverse(values: FloatArray): FloatArray = values
    fun isFitted(): Boolean = true
    fun clone(): ColumnTransform
}

fun ColumnTransforms.fitAndTransform(tensor: Tensor): Tensor {
    val rows = tensor.rows
    val cols = tensor.cols
    require(this.size == cols) { "Must supply one ColumnTransform (or null) per column" }

    // start from a copy of the input
    val result = tensor.copy()

    var c = 0
    while (c < cols) {
        val t = this[c]
        if (t != null) {
            // extract column c
            val col = FloatArray(rows)
            var r = 0
            while (r < rows) {
                col[r] = result[r, c]
                r++
            }

            // fit + transform column
            t.fit(col)
            val transformed = t.apply(col)
            require(transformed.size == rows) { "Transformed column $c has wrong length ${transformed.size}, expected $rows" }

            // write back
            r = 0
            while (r < rows) {
                result[r, c] = transformed[r]
                r++
            }
        }
        c++
    }
    return result
}

/** Apply already-fitted transforms to a new tensor (no fitting). */
fun ColumnTransforms.apply(tensor: Tensor): Tensor {
    val rows = tensor.rows
    val cols = tensor.cols
    require(this.size == cols) { "Must supply one ColumnTransform (or null) per column" }

    val result = tensor.copy()

    var c = 0
    while (c < cols) {
        val t = this[c]
        if (t != null) {
            val col = FloatArray(rows)
            var r = 0
            while (r < rows) {
                col[r] = result[r, c]
                r++
            }
            val transformed = t.apply(col)
            require(transformed.size == rows) { "Transformed column $c has wrong length ${transformed.size}, expected $rows" }
            r = 0
            while (r < rows) {
                result[r, c] = transformed[r]
                r++
            }
        }
        c++
    }
    return result
}

/** Restore original scale column-by-column. */
fun ColumnTransforms.inverse(tensor: Tensor): Tensor {
    val rows = tensor.rows
    val cols = tensor.cols
    require(this.size == cols) { "Must supply one ColumnTransform (or null) per column" }

    val result = tensor.copy()

    var c = 0
    while (c < cols) {
        val t = this[c]
        if (t != null) {
            val col = FloatArray(rows)
            var r = 0
            while (r < rows) {
                col[r] = result[r, c]
                r++
            }
            val restored = t.inverse(col)
            require(restored.size == rows) { "Inverse column $c has wrong length ${restored.size}, expected $rows" }
            r = 0
            while (r < rows) {
                result[r, c] = restored[r]
                r++
            }
        }
        c++
    }
    return result
}
