package com.onyxdevtools.ai.extensions

import java.util.stream.IntStream
import kotlin.math.min

/**
 * Multiplies two matrices in parallel using Java's parallel streams.
 *
 * @param a The left-hand matrix (dimensions: aRows x aCols).
 * @param b The right-hand matrix (dimensions: bRows x bCols).
 * @return The product of the two matrices (dimensions: aRows x bCols).
 * @throws IllegalArgumentException If the number of columns in [a] does not match the number of rows in [b].
 */
const val BLOCK_SIZE_TRANSPOSED = 32 // Example value, experiment with 16, 64, 128 etc.

fun matrixMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val aRows = a.size
    val aCols = a[0].size
    val bCols = b[0].size

    val bT = transpose(b)

    val result = Array(aRows) { DoubleArray(bCols) }

    val numRowBlocks = (aRows + BLOCK_SIZE_TRANSPOSED - 1) / BLOCK_SIZE_TRANSPOSED

    IntStream.range(0, numRowBlocks).parallel().forEach { blockIndex ->
        val ii = blockIndex * BLOCK_SIZE_TRANSPOSED
        val iEnd = min(ii + BLOCK_SIZE_TRANSPOSED, aRows)

        for (jj in 0 until bCols step BLOCK_SIZE_TRANSPOSED) {
            val jEnd = min(jj + BLOCK_SIZE_TRANSPOSED, bCols)

            for (kk in 0 until aCols step BLOCK_SIZE_TRANSPOSED) {
                val kEnd = min(kk + BLOCK_SIZE_TRANSPOSED, aCols)

                for (i in ii until iEnd) {
                    val rowA = a[i]
                    val resRow = result[i]
                    for (j in jj until jEnd) {
                        var sum = 0.0
                        val rowBT = bT[j]
                        for (k in kk until kEnd) {
                            sum += rowA[k] * rowBT[k]
                        }
                        resRow[j] += sum
                    }
                }
            }
        }
    }

    return result
}

/**
 * Adds a vector to each row of a matrix.
 *
 * @param matrix The input matrix (dimensions: rows x cols).
 * @param vector A vector of length cols to be added to each row.
 * @return A new matrix where [vector] has been added to each row of [matrix].
 * @throws IllegalArgumentException If the size of [vector] does not match the number of columns in [matrix].
 */
fun addVectorToRows(matrix: Array<DoubleArray>, vector: DoubleArray): Array<DoubleArray> {
    val numRows = matrix.size
    val numCols = matrix[0].size
    val result = Array(numRows) { DoubleArray(numCols) }

    for (i in 0 until numRows) {
        val row = matrix[i]
        for (j in 0 until numCols) {
            result[i][j] = row[j] + vector[j]
        }
    }
    return result
}

/**
 * Applies a function element-wise to a matrix.
 * This function is marked as inline to reduce call overhead in tight loops.
 *
 * @param matrix The input matrix.
 * @param func A lambda function that takes a [Double] and returns a [Double].
 * @return A new matrix where `func` has been applied to every element in [matrix].
 */
inline fun applyElementWise(
    matrix: Array<DoubleArray>,
    func: (Double) -> Double
): Array<DoubleArray> {
    val numRows = matrix.size
    val numCols = matrix[0].size
    val result = Array(numRows) { DoubleArray(numCols) }
    for (i in 0 until numRows) {
        for (j in 0 until numCols) {
            result[i][j] = func(matrix[i][j])
        }
    }
    return result
}

/**
 * Multiplies two matrices of the same dimensions element-wise.
 *
 * @param a The first matrix (dimensions: rows x cols).
 * @param b The second matrix (dimensions: rows x cols).
 * @return A new matrix where each element is '[a[i][j]' * b[i][j]].
 * @throws IllegalArgumentException If [a] and [b] are not the same dimensions.
 */
fun elementWiseMultiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val rows = a.size
    val cols = a[0].size
    val result = Array(rows) { DoubleArray(cols) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[i][j] = a[i][j] * b[i][j]
        }
    }
    return result
}

/**
 * Returns the transpose of a matrix.
 *
 * @param m The input matrix (dimensions: rows x cols).
 * @return A new matrix which is the transpose of [m] (dimensions: cols x rows).
 */
fun transpose(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val rows = matrix.size
    if (rows == 0) return Array(0) { DoubleArray(0) }
    val cols = matrix[0].size
    if (cols == 0) return Array(rows) { DoubleArray(0) }

    val result = Array(cols) { DoubleArray(rows) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[j][i] = matrix[i][j]
        }
    }
    return result
}

/**
 * Sums the columns of a matrix.
 *
 * @param m The input matrix (dimensions: rows x cols).
 * @return A [DoubleArray] of length cols, where each element is the sum of a column in [m].
 */
fun sumColumns(m: Array<DoubleArray>): DoubleArray {
    if (m.isEmpty()) return DoubleArray(0)
    val rows = m.size
    val cols = m[0].size
    val result = DoubleArray(cols)
    for (i in 0 until rows) {
        val row = m[i]
        for (j in 0 until cols) {
            result[j] += row[j]
        }
    }
    return result
}

/**
 * Subtracts matrix [b] from matrix [a], element-wise.
 *
 * @param a The left-hand matrix (dimensions: rows x cols).
 * @param b The right-hand matrix (dimensions: rows x cols).
 * @return A new matrix where each element is [a[i][j] - b[i][j]].
 * @throws IllegalArgumentException If [a] and [b] are not the same dimensions.
 */
fun subtract(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    @Suppress("DuplicatedCode")
    val rows = a.size
    val cols = a[0].size
    val result = Array(rows) { DoubleArray(cols) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[i][j] = a[i][j] - b[i][j]
        }
    }
    return result
}

/**
 * Multiplies every element of a matrix by a scalar value.
 *
 * @param m The input matrix (dimensions: rows x cols).
 * @param s The scalar value to multiply by.
 * @return A new matrix where each element is [m[i][j] * s].
 */
fun scalarMultiply(m: Array<DoubleArray>, s: Double): Array<DoubleArray> {
    val rows = m.size
    val cols = if (rows > 0) m[0].size else 0
    val result = Array(rows) { DoubleArray(cols) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[i][j] = m[i][j] * s
        }
    }
    return result
}

/**
 * Adds two matrices of the same dimensions, element-wise.
 *
 * @param a The first matrix (dimensions: rows x cols).
 * @param b The second matrix (dimensions: rows x cols).
 * @return A new matrix where each element is [a[i][j] + b[i][j]].
 * @throws IllegalArgumentException If [a] and [b] are not the same dimensions.
 */
fun add(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    @Suppress("DuplicatedCode")
    val rows = a.size
    val cols = a[0].size
    val result = Array(rows) { DoubleArray(cols) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[i][j] = a[i][j] + b[i][j]
        }
    }
    return result
}
