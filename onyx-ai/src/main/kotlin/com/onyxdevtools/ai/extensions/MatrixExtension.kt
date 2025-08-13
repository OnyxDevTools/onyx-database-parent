package com.onyxdevtools.ai.extensions

import com.onyxdevtools.ai.Matrix
import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies
import java.util.stream.IntStream
import kotlin.math.*

// Vector API Specifications
private val FLOAT_SPEC: VectorSpecies<Float> by lazy { FloatVector.SPECIES_PREFERRED }

// Performance constants
private const val SIMPLE_WORK_MAX = 50_000
private const val SKINNY_DIM = 64
private const val PARALLEL_ROWS_MIN = 64
private const val PARALLEL_COLS_MIN = 256

/**
 * High-performance matrix multiplication that automatically chooses the best algorithm
 * based on matrix dimensions and precision type.
 */
fun matrixMultiply(A: Matrix, B: Matrix): Matrix {
    val m = A.size.toLong()
    val k = A[0].size.toLong()
    val n = B[0].size.toLong()

    require(A[0].size == B.size) { "Matrix dimensions don't match for multiplication: ${A.size}x${A[0].size} * ${B.size}x${B[0].size}" }

    val work = m * k * n
    if (work <= SIMPLE_WORK_MAX) return matrixMultiplySimple(A, B)

    val skinny = minOf(m, n) < SKINNY_DIM
    val shouldParallelize = (m >= PARALLEL_ROWS_MIN) || (n >= PARALLEL_COLS_MIN)

    return if (skinny) {
        // For skinny matrices, vectorization is very effective
        if (shouldParallelize) matrixMultiplySkinnyVectorizedParallel(A, B)
        else matrixMultiplySkinnyVectorized(A, B)
    } else {
        // For larger matrices, use block-based parallel approach for best performance
        matrixMultiplyParallelJVM(A, B)
    }
}

/**
 * Optimized matrix multiplication for large matrices using block-based parallel processing
 */
private fun matrixMultiplyParallelJVM(A: Matrix, B: Matrix): Matrix {
    val m = A.size
    val k = A[0].size
    val n = B[0].size

    // Create result matrix
    val result = Array(m) { FloatArray(n) }

    val blockSize = 64  // Local block size for this implementation
    if (m > blockSize) {
        val BTransposed = transpose(B)
        val numRowBlocks = (m + blockSize - 1) / blockSize

        IntStream.range(0, numRowBlocks).parallel().forEach { blockIndex ->
            val rowStart = blockIndex * blockSize
            val rowEnd = minOf(rowStart + blockSize, m)

            for (colStart in 0 until n step blockSize) {
                val colEnd = minOf(colStart + blockSize, n)

                for (kStart in 0 until k step blockSize) {
                    val kEnd = minOf(kStart + blockSize, k)

                    for (rowIndex in rowStart until rowEnd) {
                        for (colIndex in colStart until colEnd) {
                            var dotProduct = 0.0f
                            for (kIndex in kStart until kEnd) {
                                dotProduct += A[rowIndex][kIndex] * BTransposed[colIndex][kIndex]
                            }
                            result[rowIndex][colIndex] = result[rowIndex][colIndex] + dotProduct
                        }
                    }
                }
            }
        }
        return result
    } else {
        return matrixMultiplySimple(A, B)
    }
}

/**
 * Vectorized matrix multiplication optimized for skinny matrices
 */
private fun matrixMultiplySkinnyVectorized(A: Matrix, B: Matrix): Matrix {
    val m = A.size
    val k = A[0].size
    val n = B[0].size

    val result = Array(m) { FloatArray(n) }
    return matrixMultiplySkinnyVectorizedFloat(A, B, result)
}

fun transposeParallelTiled(B: Array<FloatArray>, tile: Int = 64): Array<FloatArray> {
    val k = B.size
    val n = B[0].size
    val Bt = Array(n) { FloatArray(k) }

    val tCols = (n + tile - 1) / tile
    val tRows = (k + tile - 1) / tile

    // Parallelize over tile columns (good balance, avoids too many tiny tasks)
    IntStream.range(0, tCols).parallel().forEach { tj ->
        val j0 = tj * tile
        val j1 = min(n, j0 + tile)
        var ti = 0
        while (ti < tRows) {
            val i0 = ti * tile
            val i1 = min(k, i0 + tile)
            var i = i0
            while (i < i1) {
                val src = B[i]
                var j = j0
                while (j < j1) {
                    Bt[j][i] = src[j]
                    j++
                }
                i++
            }
            ti++
        }
    }
    return Bt
}

/**
 * Float-optimized vectorized multiplication
 */
private fun matrixMultiplySkinnyVectorizedFloat(A: Matrix, B: Matrix, result: Matrix): Matrix {
    val m = A.size
    val k = A[0].size
    val n = B[0].size
    val tile = 8

    // Transpose B for better cache locality
    val Bt = transposeParallelTiled(B, 64)

    for (i in 0 until m) {
        val aRow = A[i]
        val resultRow = result[i]
        var j = 0

        while (j + tile <= n) {
            var acc0 = 0.0f; var acc1 = 0.0f; var acc2 = 0.0f; var acc3 = 0.0f
            var acc4 = 0.0f; var acc5 = 0.0f; var acc6 = 0.0f; var acc7 = 0.0f

            var p = 0
            val upper = FLOAT_SPEC.loopBound(k)

            while (p < upper) {
                val va = FloatVector.fromArray(FLOAT_SPEC, aRow, p)
                val vb0 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 0], p)
                val vb1 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 1], p)
                val vb2 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 2], p)
                val vb3 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 3], p)
                val vb4 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 4], p)
                val vb5 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 5], p)
                val vb6 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 6], p)
                val vb7 = FloatVector.fromArray(FLOAT_SPEC, Bt[j + 7], p)

                acc0 += va.mul(vb0).reduceLanes(VectorOperators.ADD)
                acc1 += va.mul(vb1).reduceLanes(VectorOperators.ADD)
                acc2 += va.mul(vb2).reduceLanes(VectorOperators.ADD)
                acc3 += va.mul(vb3).reduceLanes(VectorOperators.ADD)
                acc4 += va.mul(vb4).reduceLanes(VectorOperators.ADD)
                acc5 += va.mul(vb5).reduceLanes(VectorOperators.ADD)
                acc6 += va.mul(vb6).reduceLanes(VectorOperators.ADD)
                acc7 += va.mul(vb7).reduceLanes(VectorOperators.ADD)

                p += FLOAT_SPEC.length()
            }

            while (p < k) {
                val a = aRow[p]
                acc0 += a * Bt[j + 0][p]
                acc1 += a * Bt[j + 1][p]
                acc2 += a * Bt[j + 2][p]
                acc3 += a * Bt[j + 3][p]
                acc4 += a * Bt[j + 4][p]
                acc5 += a * Bt[j + 5][p]
                acc6 += a * Bt[j + 6][p]
                acc7 += a * Bt[j + 7][p]
                p++
            }

            resultRow[j + 0] = acc0; resultRow[j + 1] = acc1; resultRow[j + 2] = acc2; resultRow[j + 3] = acc3
            resultRow[j + 4] = acc4; resultRow[j + 5] = acc5; resultRow[j + 6] = acc6; resultRow[j + 7] = acc7
            j += tile
        }

        while (j < n) {
            var acc = 0.0f
            for (p in 0 until k) {
                acc += aRow[p] * Bt[j][p]
            }
            resultRow[j] = acc
            j++
        }
    }

    return result
}

/**
 * Parallel version of skinny vectorized multiplication
 */
private fun matrixMultiplySkinnyVectorizedParallel(A: Matrix, B: Matrix): Matrix {
    val m = A.size
    val k = A[0].size
    val n = B[0].size

    // Parallel tiled transpose tends to help large shapes
    val Bt = transposeParallelTiled(B, tile = 64)

    val result = Array(m) { FloatArray(n) }
    val species = FloatVector.SPECIES_PREFERRED
    val L = species.length()

    IntStream.range(0, m).parallel().forEach { i ->
        val aRow = A[i]
        var j = 0
        while (j < n) {
            val btRow = Bt[j]
            var p = 0
            var accVec = FloatVector.zero(species)
            while (p + L <= k) {
                val av = FloatVector.fromArray(species, aRow, p)
                val bv = FloatVector.fromArray(species, btRow, p)
                accVec = av.fma(bv, accVec) // (a*b)+acc with FMA if supported
                p += L
            }
            var acc = accVec.reduceLanes(VectorOperators.ADD)
            while (p < k) {
                acc += aRow[p] * btRow[p]
                p++
            }
            result[i][j] = acc
            j++
        }
    }
    return result
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
    val result = Matrix(numRows) { FloatArray(numCols) }

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
fun addVectorToRows(matrix: Matrix, vector: FloatArray): Matrix =
    matrix.map { row -> FloatArray(row.size) { colIndex -> row[colIndex] + vector[colIndex] } }.toTypedArray()

/**
 * Applies a function to each element of the matrix.
 *
 * @param matrix The input matrix.
 * @param transform The function to apply to each element.
 * @return A new matrix with transformed elements.
 */
inline fun applyElementWise(matrix: Matrix, transform: (Float) -> Float): Matrix =
    matrix.map { row -> FloatArray(row.size) { colIndex -> transform(row[colIndex]) } }.toTypedArray()

/**
 * Performs element-wise multiplication of two matrices.
 *
 * @param matrixA The first matrix.
 * @param matrixB The second matrix.
 * @return A new matrix resulting from element-wise multiplication.
 */
fun elementWiseMultiply(matrixA: Matrix, matrixB: Matrix): Matrix =
    matrixA.mapIndexed { rowIndex, row ->
        FloatArray(row.size) { colIndex -> row[colIndex] * matrixB[rowIndex][colIndex] }
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
        FloatArray(row.size) { colIndex -> row[colIndex] - matrixB[rowIndex][colIndex] }
    }.toTypedArray()

/**
 * Performs element-wise addition of two matrices.
 *
 * @param matrixA The first matrix.
 * @param matrixB The second matrix.
 * @return A new matrix representing matrixA + matrixB.
 */
fun add(matrixA: Matrix, matrixB: Matrix): Matrix {
    require(matrixA.size == matrixB.size) { 
        "Matrix row dimensions don't match for addition: ${matrixA.size} vs ${matrixB.size}" 
    }
    
    if (matrixA.isEmpty()) return arrayOf()
    
    return matrixA.mapIndexed { rowIndex, row ->
        val rowB = matrixB[rowIndex]
        require(row.size == rowB.size) { 
            "Matrix column dimensions don't match at row $rowIndex: ${row.size} vs ${rowB.size}" 
        }
        FloatArray(row.size) { colIndex -> row[colIndex] + rowB[colIndex] }
    }.toTypedArray()
}

/**
 * Multiplies each element in the matrix by a scalar.
 *
 * @param matrix The input matrix.
 * @param scalar The scalar value.
 * @return A new matrix scaled by the given scalar.
 */
fun scalarMultiply(matrix: Matrix, scalar: Float): Matrix =
    matrix.map { row -> FloatArray(row.size) { colIndex -> row[colIndex] * scalar } }.toTypedArray()

/**
 * Transposes the matrix (swaps rows and columns).
 *
 * @param matrix The input matrix.
 * @return A transposed version of the matrix.
 */
fun transpose(matrix: Matrix): Matrix =
    if (matrix.isEmpty()) arrayOf()
    else Array(matrix[0].size) { colIndex ->
        FloatArray(matrix.size) { rowIndex -> matrix[rowIndex][colIndex] }
    }

/**
 * Computes the sum of each column in the matrix.
 *
 * @param matrix The input matrix.
 * @return A vector containing the sum of each column.
 */
fun sumColumns(matrix: Matrix): FloatArray =
    FloatArray(if (matrix.isEmpty()) 0 else matrix[0].size).also { columnSums ->
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
fun Matrix.flatten(): FloatArray =
    buildList { for (row in this@flatten) addAll(row.asList()) }.toFloatArray()

/**
 * Calculates mean standard error
 * Predicted results is the receiver
 *
 * @param actual Actual results
 */
fun Matrix.meanStandardError(actual: Matrix): Float {
    var sum = 0.0f
    var total = 0

    val rows = minOf(this.size, actual.size)
    for (i in 0 until rows) {
        val cols = minOf(this[i].size, actual[i].size)
        for (j in 0 until cols) {
            sum += (this[i][j] - actual[i][j]).pow(2)
            total++
        }
    }
    return if (total > 0) sum / total else 0.0f
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
 * @return A List<Float> containing the elements of the specified column.
 * @throws IndexOutOfBoundsException if colIndex is out of bounds.
 */
fun Matrix.getColumn(colIndex: Int): List<Float> {
    require(colIndex >= 0 && colIndex < colCount()) { "Column index out of bounds: $colIndex" }
    return List(rowCount()) { rowIndex -> this[rowIndex][colIndex] }
}

/**
 * Retrieves a specific row from the matrix as a list.
 * Note: The underlying row is already a FloatArray, this converts it to List<Float>.
 * @receiver The Matrix.
 * @param rowIndex The index of the row to retrieve (0-based).
 * @return A List<Float> containing the elements of the specified row.
 * @throws IndexOutOfBoundsException if rowIndex is out of bounds.
 */
fun Matrix.getRow(rowIndex: Int): List<Float> {
    // No bounds check needed here as Array access does it, but could add for consistency
    // require(rowIndex >= 0 && rowIndex < rowCount()) { "Row index out of bounds: $rowIndex" }
    return this[rowIndex].toList()
}

// --- Mapping Operations ---

/**
 * Applies a given transformation function to each column of the matrix, returning a new matrix.
 * The transformation function receives the column index and the column data as a List<Float>,
 * and should return the transformed column data as a List<Float> of the same size.
 *
 * @receiver The original Matrix.
 * @param transform A function that takes a column index (Int) and column data (List<Float>)
 * and returns the transformed column data (List<Float>).
 * @return A new Matrix containing the transformed columns.
 * @throws IllegalArgumentException if the transform function returns lists of inconsistent sizes.
 */
fun Matrix.mapColumnsIndexed(transform: (colIndex: Int, List<Float>) -> List<Float>): Matrix {
    if (this.isEmpty()) return emptyArray() // Handle empty matrix

    val numRows = rowCount()
    val numCols = colCount()
    val resultMatrix = Array(numRows) { FloatArray(numCols) }
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
 * The transformation function receives the row index and the row data as a List<Float>,
 * and should return the transformed row data as a List<Float> of the same size.
 *
 * @receiver The original Matrix.
 * @param transform A function that takes a row index (Int) and row data (List<Float>)
 * and returns the transformed row data (List<Float>).
 * @return A new Matrix containing the transformed rows.
 * @throws IllegalArgumentException if the transform function returns lists of inconsistent sizes.
 */
fun Matrix.mapRowsIndexed(transform: (rowIndex: Int, List<Float>) -> List<Float>): Matrix {
    if (this.isEmpty()) return emptyArray() // Handle empty matrix

    val numRows = rowCount()
    val numCols = colCount()
    val resultMatrix = Array(numRows) { FloatArray(numCols) }
    var expectedColCount = -1 // To check consistency

    for (i in 0 until numRows) {
        val originalRow = this.getRow(i) // Gets List<Float>
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
    val min: Float = Float.NaN,
    val max: Float = Float.NaN,
    val median: Float = Float.NaN,
    val q1: Float = Float.NaN, // 25th percentile
    val q3: Float = Float.NaN, // 75th percentile
    val maxAbs: Float = Float.NaN
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
                min = column.minOrNull() ?: Float.NaN,
                max = column.maxOrNull() ?: Float.NaN
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
        val median = if (n % 2 == 0) (column[n / 2 - 1] + column[n / 2]) / 2.0f else column[n / 2]

        // Simple Percentile Calculation (Method: R-7, Nearest Rank - commonly used default)
        // More sophisticated methods exist (e.g., linear interpolation)
        fun getPercentile(p: Float): Float {
            if (n == 0) return Float.NaN
            val index = p * (n - 1) // 0-based index
            val lowerIndex = floor(index).toInt()
            val upperIndex = ceil(index).toInt()
            if (lowerIndex == upperIndex) {
                return column[lowerIndex]
            }
            return column[round(index).toInt()] // Round to nearest index
        }

        val q1 = getPercentile(0.25f)
        val q3 = getPercentile(0.75f)

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
 * @return A List of Floats, where each element is the max absolute value of the corresponding column.
 */
fun Matrix.calculateColumnMaxAbs(): List<Float> {
    if (this.isEmpty()) return emptyList()
    val maxAbsList = mutableListOf<Float>()
    for (j in 0 until colCount()) {
        val maxAbs = this.getColumn(j).maxOfOrNull { abs(it) } ?: 0.0f
        maxAbsList.add(maxAbs)
    }
    return maxAbsList
}

/**
 * Calculates the L2 norm (Euclidean length) for each row.
 * Used by UnitVectorNormalizer.
 * @receiver The Matrix.
 * @return A List of Floats, where each element is the L2 norm of the corresponding row.
 */
fun Matrix.calculateRowNorms(): List<Float> {
    if (this.isEmpty()) return emptyList()
    val norms = mutableListOf<Float>()
    for (i in 0 until rowCount()) {
        val row = this[i] // Direct access to FloatArray is efficient here
        var sumSquares = 0.0
        for (value in row) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares).toFloat()
        norms.add(norm)
    }
    return norms
}

fun softmax(matrix: Matrix): Matrix {
    return matrix.map { logits ->
        val max = logits.maxOrNull() ?: 0.0f  // For numerical stability
        val expLogits = logits.map { exp(it - max) }
        val sumExp = expLogits.sum()
        expLogits.map { it / sumExp }.toFloatArray()
    }.toTypedArray()
}
