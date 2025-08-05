package com.onyxdevtools.ai.extensions

import com.onyxdevtools.ai.Matrix
import java.util.stream.IntStream
import kotlin.math.*

/** Small constant added to denominator in Adam updates for numerical stability. */
const val EPSILON = 1e-8
const val BLOCK_SIZE_TRANSPOSED = 128

/**
 * Multiplies two matrices using a block-based, cache-friendly algorithm with parallelism.
 * Falls back to a simpler implementation for small matrices.
 *
 * @param matrixA The left-hand side matrix.
 * @param matrixB The right-hand side matrix.
 * @return A matrix representing the product of matrixA × matrixB.
 */
fun matrixMultiply(matrixA: Matrix, matrixB: Matrix): Matrix {
    val rowsA = matrixA.size
    val colsA = matrixA[0].size
    val colsB = matrixB[0].size

    if (rowsA > BLOCK_SIZE_TRANSPOSED) {
        val matrixBTransposed = transpose(matrixB)
        val result = Array(rowsA) { DoubleArray(colsB) }

        val numRowBlocks = (rowsA + BLOCK_SIZE_TRANSPOSED - 1) / BLOCK_SIZE_TRANSPOSED

        IntStream.range(0, numRowBlocks).parallel().forEach { blockIndex ->
            val rowStart = blockIndex * BLOCK_SIZE_TRANSPOSED
            val rowEnd = min(rowStart + BLOCK_SIZE_TRANSPOSED, rowsA)

            for (colStart in 0 until colsB step BLOCK_SIZE_TRANSPOSED) {
                val colEnd = min(colStart + BLOCK_SIZE_TRANSPOSED, colsB)

                for (kStart in 0 until colsA step BLOCK_SIZE_TRANSPOSED) {
                    val kEnd = min(kStart + BLOCK_SIZE_TRANSPOSED, colsA)

                    for (rowIndex in rowStart until rowEnd) {
                        val rowA = matrixA[rowIndex]
                        val resultRow = result[rowIndex]
                        for (colIndex in colStart until colEnd) {
                            var dotProduct = 0.0
                            val rowBTransposed = matrixBTransposed[colIndex]
                            for (kIndex in kStart until kEnd) {
                                dotProduct += rowA[kIndex] * rowBTransposed[kIndex]
                            }
                            resultRow[colIndex] += dotProduct
                        }
                    }
                }
            }
        }

        return result
    } else {
        return matrixMultiplySimple(matrixA, matrixB)
    }
}

/**
 * Multiplies two matrices using a naive nested-loop approach.
 *
 * @param matrixA The left-hand side matrix.
 * @param matrixB The right-hand side matrix.
 * @return A matrix representing the product of matrixA × matrixB.
 */
fun matrixMultiplySimple(matrixA: Matrix, matrixB: Matrix): Matrix {
    val numRows = matrixA.size
    val sharedDim = matrixA[0].size
    val numCols = matrixB[0].size
    val result = Matrix(numRows) { DoubleArray(numCols) }

    for (row in 0 until numRows) {
        val resultRow = result[row]
        for (sharedIndex in 0 until sharedDim) {
            val valueA = matrixA[row][sharedIndex]
            val rowB = matrixB[sharedIndex]
            for (col in 0 until numCols) {
                resultRow[col] += valueA * rowB[col]
            }
        }
    }
    return result
}

/**
 * Adds a vector to each row of the matrix.
 *
 * @param matrix The input matrix.
 * @param vector The vector to add.
 * @return A new matrix where the vector is added to each row.
 */
fun addVectorToRows(matrix: Matrix, vector: DoubleArray): Matrix =
    matrix.map { row -> DoubleArray(row.size) { colIndex -> row[colIndex] + vector[colIndex] } }.toTypedArray()

/**
 * Applies a function to each element of the matrix.
 *
 * @param matrix The input matrix.
 * @param transform The function to apply to each element.
 * @return A new matrix with transformed elements.
 */
inline fun applyElementWise(matrix: Matrix, transform: (Double) -> Double): Matrix =
    matrix.map { row -> DoubleArray(row.size) { colIndex -> transform(row[colIndex]) } }.toTypedArray()

/**
 * Performs element-wise multiplication of two matrices.
 *
 * @param matrixA The first matrix.
 * @param matrixB The second matrix.
 * @return A new matrix resulting from element-wise multiplication.
 */
fun elementWiseMultiply(matrixA: Matrix, matrixB: Matrix): Matrix =
    matrixA.mapIndexed { rowIndex, row ->
        DoubleArray(row.size) { colIndex -> row[colIndex] * matrixB[rowIndex][colIndex] }
    }.toTypedArray()

/**
 * Performs element-wise subtraction of two matrices.
 *
 * @param matrixA The minuend matrix.
 * @param matrixB The subtrahend matrix.
 * @return A new matrix representing matrixA - matrixB.
 */
fun subtract(matrixA: Matrix, matrixB: Matrix): Matrix =
    matrixA.mapIndexed { rowIndex, row ->
        DoubleArray(row.size) { colIndex -> row[colIndex] - matrixB[rowIndex][colIndex] }
    }.toTypedArray()

/**
 * Performs element-wise addition of two matrices.
 *
 * @param matrixA The first matrix.
 * @param matrixB The second matrix.
 * @return A new matrix representing matrixA + matrixB.
 */
fun add(matrixA: Matrix, matrixB: Matrix): Matrix =
    matrixA.mapIndexed { rowIndex, row ->
        DoubleArray(row.size) { colIndex -> row[colIndex] + matrixB[rowIndex][colIndex] }
    }.toTypedArray()

/**
 * Multiplies each element in the matrix by a scalar.
 *
 * @param matrix The input matrix.
 * @param scalar The scalar value.
 * @return A new matrix scaled by the given scalar.
 */
fun scalarMultiply(matrix: Matrix, scalar: Double): Matrix =
    matrix.map { row -> DoubleArray(row.size) { colIndex -> row[colIndex] * scalar } }.toTypedArray()

/**
 * Transposes the matrix (swaps rows and columns).
 *
 * @param matrix The input matrix.
 * @return A transposed version of the matrix.
 */
fun transpose(matrix: Matrix): Matrix =
    if (matrix.isEmpty()) arrayOf()
    else Array(matrix[0].size) { colIndex ->
        DoubleArray(matrix.size) { rowIndex -> matrix[rowIndex][colIndex] }
    }

/**
 * Computes the sum of each column in the matrix.
 *
 * @param matrix The input matrix.
 * @return A vector containing the sum of each column.
 */
fun sumColumns(matrix: Matrix): DoubleArray =
    DoubleArray(if (matrix.isEmpty()) 0 else matrix[0].size).also { columnSums ->
        for (row in matrix) {
            for (colIndex in row.indices) {
                columnSums[colIndex] += row[colIndex]
            }
        }
    }

/**
 * Creates a deep copy of the matrix.
 *
 * @receiver The matrix to copy.
 * @return A new matrix with the same values.
 */
fun Matrix.deepCopy(): Matrix = map { it.copyOf() }.toTypedArray()

/**
 * Flattens a matrix into a one-dimensional array in row-major order.
 *
 * @receiver The matrix to flatten.
 * @return A flat array of all elements in the matrix.
 */
fun Matrix.flatten(): DoubleArray =
    buildList { for (row in this@flatten) addAll(row.asList()) }.toDoubleArray()

/**
 * Calculates mean standard error
 * Predicted results is the receiver
 *
 * @param actual Actual results
 */
fun Matrix.meanStandardError(actual: Matrix): Double {
    var sum = 0.0
    var total = 0

    val rows = minOf(this.size, actual.size)
    for (i in 0 until rows) {
        val cols = minOf(this[i].size, actual[i].size)
        for (j in 0 until cols) {
            sum += (this[i][j] - actual[i][j]).pow(2)
            total++
        }
    }
    return if (total > 0) sum / total else 0.0
}


/**
 * Constant for small value comparisons to handle floating-point inaccuracies.
 */
private const val EPSILON_EXT = 1e-9 // Use a different name if EPSILON is already defined in scope

// --- Basic Properties and Access ---

/**
 * Returns the number of rows in the matrix.
 * @receiver The Matrix.
 * @return The number of rows.
 */
fun Matrix.rowCount(): Int = this.size

/**
 * Returns the number of columns in the matrix.
 * Assumes the matrix is not empty and is rectangular (all rows have the same length).
 * @receiver The Matrix.
 * @return The number of columns, or 0 if the matrix has no rows.
 * @throws IllegalArgumentException if the matrix is jagged (rows have different lengths).
 */
fun Matrix.colCount(): Int {
    if (this.isEmpty()) return 0
    val firstRowSize = this[0].size
    // Optional: Add check for jagged arrays if necessary
    // require(this.all { it.size == firstRowSize }) { "Matrix must be rectangular." }
    return firstRowSize
}

/**
 * Creates a deep copy of the matrix.
 * @receiver The Matrix to copy.
 * @return A new Matrix instance with the same dimensions and values.
 */
fun Matrix.copy(): Matrix {
    return Array(rowCount()) { i -> this[i].clone() }
}

/**
 * Retrieves a specific column from the matrix as a list.
 * @receiver The Matrix.
 * @param colIndex The index of the column to retrieve (0-based).
 * @return A List<Double> containing the elements of the specified column.
 * @throws IndexOutOfBoundsException if colIndex is out of bounds.
 */
fun Matrix.getColumn(colIndex: Int): List<Double> {
    require(colIndex >= 0 && colIndex < colCount()) { "Column index out of bounds: $colIndex" }
    return List(rowCount()) { rowIndex -> this[rowIndex][colIndex] }
}

/**
 * Retrieves a specific row from the matrix as a list.
 * Note: The underlying row is already a DoubleArray, this converts it to List<Double>.
 * @receiver The Matrix.
 * @param rowIndex The index of the row to retrieve (0-based).
 * @return A List<Double> containing the elements of the specified row.
 * @throws IndexOutOfBoundsException if rowIndex is out of bounds.
 */
fun Matrix.getRow(rowIndex: Int): List<Double> {
    // No bounds check needed here as Array access does it, but could add for consistency
    // require(rowIndex >= 0 && rowIndex < rowCount()) { "Row index out of bounds: $rowIndex" }
    return this[rowIndex].toList()
}

// --- Mapping Operations ---

/**
 * Applies a given transformation function to each column of the matrix, returning a new matrix.
 * The transformation function receives the column index and the column data as a List<Double>,
 * and should return the transformed column data as a List<Double> of the same size.
 *
 * @receiver The original Matrix.
 * @param transform A function that takes a column index (Int) and column data (List<Double>)
 * and returns the transformed column data (List<Double>).
 * @return A new Matrix containing the transformed columns.
 * @throws IllegalArgumentException if the transform function returns lists of inconsistent sizes.
 */
fun Matrix.mapColumnsIndexed(transform: (colIndex: Int, List<Double>) -> List<Double>): Matrix {
    if (this.isEmpty()) return emptyArray() // Handle empty matrix

    val numRows = rowCount()
    val numCols = colCount()
    val resultMatrix = Array(numRows) { DoubleArray(numCols) }
    var expectedRowCount = -1 // To check consistency

    for (j in 0 until numCols) {
        val originalColumn = this.getColumn(j)
        val transformedColumn = transform(j, originalColumn)

        if (expectedRowCount == -1) {
            expectedRowCount = transformedColumn.size
            require(expectedRowCount == numRows) {
                "Transformation function must return a list with the same number of rows ($numRows). " +
                        "Column $j returned ${transformedColumn.size} rows."
            }
        } else {
            require(transformedColumn.size == expectedRowCount) {
                "Transformation function returned inconsistent list sizes. Expected $expectedRowCount, got ${transformedColumn.size} for column $j."
            }
        }


        for (i in 0 until numRows) {
            resultMatrix[i][j] = transformedColumn[i]
        }
    }
    return resultMatrix
}

/**
 * Applies a given transformation function to each row of the matrix, returning a new matrix.
 * The transformation function receives the row index and the row data as a List<Double>,
 * and should return the transformed row data as a List<Double> of the same size.
 *
 * @receiver The original Matrix.
 * @param transform A function that takes a row index (Int) and row data (List<Double>)
 * and returns the transformed row data (List<Double>).
 * @return A new Matrix containing the transformed rows.
 * @throws IllegalArgumentException if the transform function returns lists of inconsistent sizes.
 */
fun Matrix.mapRowsIndexed(transform: (rowIndex: Int, List<Double>) -> List<Double>): Matrix {
    if (this.isEmpty()) return emptyArray() // Handle empty matrix

    val numRows = rowCount()
    val numCols = colCount()
    val resultMatrix = Array(numRows) { DoubleArray(numCols) }
    var expectedColCount = -1 // To check consistency

    for (i in 0 until numRows) {
        val originalRow = this.getRow(i) // Gets List<Double>
        val transformedRow = transform(i, originalRow)

        if (expectedColCount == -1) {
            expectedColCount = transformedRow.size
            require(expectedColCount == numCols) {
                "Transformation function must return a list with the same number of columns ($numCols). " +
                        "Row $i returned ${transformedRow.size} columns."
            }
        } else {
            require(transformedRow.size == expectedColCount) {
                "Transformation function returned inconsistent list sizes. Expected $expectedColCount, got ${transformedRow.size} for row $i."
            }
        }

        for (j in 0 until numCols) {
            resultMatrix[i][j] = transformedRow[j]
        }
    }
    return resultMatrix
}

/**
 * Data class to hold calculated statistics for a column (used by helper functions).
 * Note: This is defined locally here but could be the same as the one in the transformer file.
 */
data class ColumnStats(
    val min: Double = Double.NaN,
    val max: Double = Double.NaN,
    val median: Double = Double.NaN,
    val q1: Double = Double.NaN, // 25th percentile
    val q3: Double = Double.NaN, // 75th percentile
    val maxAbs: Double = Double.NaN
)

/**
 * Calculates basic statistics (min, max) for each column.
 * Used by MinMaxScaler.
 * @receiver The Matrix.
 * @return A List of ColumnStatsExt objects, one for each column, containing min and max.
 */
fun Matrix.calculateColumnStats(): List<ColumnStats> {
    if (this.isEmpty()) return emptyList()
    val statsList = mutableListOf<ColumnStats>()
    for (j in 0 until colCount()) {
        val column = this.getColumn(j)
        statsList.add(
            ColumnStats(
                min = column.minOrNull() ?: Double.NaN,
                max = column.maxOrNull() ?: Double.NaN
            )
        )
    }
    return statsList
}

/**
 * Calculates robust statistics (median, Q1, Q3) for each column.
 * Uses simple percentile calculation (linear interpolation might be more robust).
 * Used by RobustScaler.
 * @receiver The Matrix.
 * @return A List of ColumnStatsExt objects, one for each column, containing median, q1, and q3.
 */
fun Matrix.calculateColumnStatsWithMedianIQR(): List<ColumnStats> {
    if (this.isEmpty()) return emptyList()
    val statsList = mutableListOf<ColumnStats>()
    for (j in 0 until colCount()) {
        val column = this.getColumn(j).sorted()
        val n = column.size
        if (n == 0) {
            statsList.add(ColumnStats()) // All NaN for empty column
            continue
        }

        // Median (Q2)
        val median = if (n % 2 == 0) (column[n / 2 - 1] + column[n / 2]) / 2.0 else column[n / 2]

        // Simple Percentile Calculation (Method: R-7, Nearest Rank - commonly used default)
        // More sophisticated methods exist (e.g., linear interpolation)
        fun getPercentile(p: Double): Double {
            if (n == 0) return Double.NaN
            val index = p * (n - 1) // 0-based index
            val lowerIndex = floor(index).toInt()
            val upperIndex = ceil(index).toInt()
            if (lowerIndex == upperIndex) {
                return column[lowerIndex]
            }
            return column[round(index).toInt()] // Round to nearest index
        }

        val q1 = getPercentile(0.25)
        val q3 = getPercentile(0.75)

        statsList.add(
            ColumnStats(
                median = median,
                q1 = q1,
                q3 = q3
            )
        )
    }
    return statsList
}

/**
 * Calculates the maximum absolute value for each column.
 * Used by MaxAbsScaler.
 * @receiver The Matrix.
 * @return A List of Doubles, where each element is the max absolute value of the corresponding column.
 */
fun Matrix.calculateColumnMaxAbs(): List<Double> {
    if (this.isEmpty()) return emptyList()
    val maxAbsList = mutableListOf<Double>()
    for (j in 0 until colCount()) {
        val maxAbs = this.getColumn(j).maxOfOrNull { abs(it) } ?: 0.0
        maxAbsList.add(maxAbs)
    }
    return maxAbsList
}

/**
 * Calculates the L2 norm (Euclidean length) for each row.
 * Used by UnitVectorNormalizer.
 * @receiver The Matrix.
 * @return A List of Doubles, where each element is the L2 norm of the corresponding row.
 */
fun Matrix.calculateRowNorms(): List<Double> {
    if (this.isEmpty()) return emptyList()
    val norms = mutableListOf<Double>()
    for (i in 0 until rowCount()) {
        val row = this[i] // Direct access to DoubleArray is efficient here
        val norm = sqrt(row.sumOf { it * it })
        norms.add(norm)
    }
    return norms
}

fun softmax(matrix: Matrix): Matrix {
    return matrix.map { logits ->
        val max = logits.maxOrNull() ?: 0.0  // For numerical stability
        val expLogits = logits.map { exp(it - max) }
        val sumExp = expLogits.sum()
        expLogits.map { it / sumExp }.toDoubleArray()
    }.toTypedArray()
}