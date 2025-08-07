package com.onyxdevtools.ai.extensions

import com.onyxdevtools.ai.*
import jdk.incubator.vector.*
import java.util.stream.IntStream
import kotlin.math.*

// Vector API Specifications
private val DOUBLE_SPEC: VectorSpecies<Double> = DoubleVector.SPECIES_PREFERRED
private val FLOAT_SPEC: VectorSpecies<Float> = FloatVector.SPECIES_PREFERRED

// Performance constants
private const val SIMPLE_WORK_MAX = 50_000
private const val SKINNY_DIM = 128
private const val PARALLEL_ROWS_MIN = 64
private const val PARALLEL_COLS_MIN = 256

/**
 * High-performance matrix multiplication that automatically chooses the best algorithm
 * based on matrix dimensions and precision type.
 */
fun matrixMultiply(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows.toLong()
    val k = A.cols.toLong()
    val n = B.cols.toLong()
    
    require(A.cols == B.rows) { "Matrix dimensions don't match for multiplication: ${A.rows}x${A.cols} * ${B.rows}x${B.cols}" }
    
    val work = m * k * n
    if (work <= SIMPLE_WORK_MAX) return matrixMultiplySimple(A, B)
    
    val skinny = min(m, n) < SKINNY_DIM
    
    return if (skinny) {
        val parallel = (m >= PARALLEL_ROWS_MIN) || (n >= PARALLEL_COLS_MIN)
        if (parallel) matrixMultiplySkinnyVectorizedParallel(A, B)
        else matrixMultiplySkinnyVectorized(A, B)
    } else {
        matrixMultiplyParallelJVM(A, B)
    }
}

/**
 * Optimized matrix multiplication for large matrices using block-based parallel processing
 */
private fun matrixMultiplyParallelJVM(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    
    // Use the same precision as input matrices (prefer double if mixed)
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    val result = createMatrix(m, n, useSinglePrecision)
    
    if (m > BLOCK_SIZE_TRANSPOSED) {
        val BTransposed = transpose(B)
        val numRowBlocks = (m + BLOCK_SIZE_TRANSPOSED - 1) / BLOCK_SIZE_TRANSPOSED
        
        IntStream.range(0, numRowBlocks).parallel().forEach { blockIndex ->
            val rowStart = blockIndex * BLOCK_SIZE_TRANSPOSED
            val rowEnd = min(rowStart + BLOCK_SIZE_TRANSPOSED, m)
            
            for (colStart in 0 until n step BLOCK_SIZE_TRANSPOSED) {
                val colEnd = min(colStart + BLOCK_SIZE_TRANSPOSED, n)
                
                for (kStart in 0 until k step BLOCK_SIZE_TRANSPOSED) {
                    val kEnd = min(kStart + BLOCK_SIZE_TRANSPOSED, k)
                    
                    for (rowIndex in rowStart until rowEnd) {
                        for (colIndex in colStart until colEnd) {
                            var dotProduct = 0.0
                            for (kIndex in kStart until kEnd) {
                                dotProduct += A[rowIndex, kIndex] * BTransposed[colIndex, kIndex]
                            }
                            result[rowIndex, colIndex] = result[rowIndex, colIndex] + dotProduct
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
private fun matrixMultiplySkinnyVectorized(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    val result = createMatrix(m, n, useSinglePrecision)
    
    if (useSinglePrecision && A is FloatMatrix && B is FloatMatrix && result is FloatMatrix) {
        return matrixMultiplySkinnyVectorizedFloat(A, B, result)
    } else {
        return matrixMultiplySkinnyVectorizedDouble(A, B, result)
    }
}

/**
 * Float-optimized vectorized multiplication
 */
private fun matrixMultiplySkinnyVectorizedFloat(A: FloatMatrix, B: FloatMatrix, result: FloatMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    val tile = 8
    
    // Transpose B for better cache locality
    val Bt = Array(n) { j -> FloatArray(k) { i -> B.getFloatArray(i)[j] } }
    
    for (i in 0 until m) {
        val aRow = A.getFloatArray(i)
        val resultRow = result.getFloatArray(i)
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
 * Double-precision vectorized multiplication (fallback for mixed precision)
 */
private fun matrixMultiplySkinnyVectorizedDouble(A: FlexibleMatrix, B: FlexibleMatrix, result: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    val tile = 8
    
    // Transpose B for better cache locality
    val Bt = Array(n) { j -> DoubleArray(k) { i -> B[i, j] } }
    
    for (i in 0 until m) {
        val aRow = A[i]
        var j = 0
        
        while (j + tile <= n) {
            var acc0 = 0.0; var acc1 = 0.0; var acc2 = 0.0; var acc3 = 0.0
            var acc4 = 0.0; var acc5 = 0.0; var acc6 = 0.0; var acc7 = 0.0
            
            var p = 0
            val upper = DOUBLE_SPEC.loopBound(k)
            
            while (p < upper) {
                val va = DoubleVector.fromArray(DOUBLE_SPEC, aRow, p)
                val vb0 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 0], p)
                val vb1 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 1], p)
                val vb2 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 2], p)
                val vb3 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 3], p)
                val vb4 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 4], p)
                val vb5 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 5], p)
                val vb6 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 6], p)
                val vb7 = DoubleVector.fromArray(DOUBLE_SPEC, Bt[j + 7], p)
                
                acc0 += va.mul(vb0).reduceLanes(VectorOperators.ADD)
                acc1 += va.mul(vb1).reduceLanes(VectorOperators.ADD)
                acc2 += va.mul(vb2).reduceLanes(VectorOperators.ADD)
                acc3 += va.mul(vb3).reduceLanes(VectorOperators.ADD)
                acc4 += va.mul(vb4).reduceLanes(VectorOperators.ADD)
                acc5 += va.mul(vb5).reduceLanes(VectorOperators.ADD)
                acc6 += va.mul(vb6).reduceLanes(VectorOperators.ADD)
                acc7 += va.mul(vb7).reduceLanes(VectorOperators.ADD)
                
                p += DOUBLE_SPEC.length()
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
            
            result[i, j + 0] = acc0; result[i, j + 1] = acc1; result[i, j + 2] = acc2; result[i, j + 3] = acc3
            result[i, j + 4] = acc4; result[i, j + 5] = acc5; result[i, j + 6] = acc6; result[i, j + 7] = acc7
            j += tile
        }
        
        while (j < n) {
            var acc = 0.0
            for (p in 0 until k) {
                acc += A[i, p] * Bt[j][p]
            }
            result[i, j] = acc
            j++
        }
    }
    
    return result
}

/**
 * Parallel version of skinny vectorized multiplication
 */
private fun matrixMultiplySkinnyVectorizedParallel(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    val result = createMatrix(m, n, useSinglePrecision)
    
    // Transpose B for better cache locality
    val Bt = Array(n) { j -> DoubleArray(k) { i -> B[i, j] } }
    val tile = 8
    
    IntStream.range(0, m).parallel().forEach { i ->
        val aRow = A[i]
        var j = 0
        
        while (j + tile <= n) {
            var acc0 = 0.0; var acc1 = 0.0; var acc2 = 0.0; var acc3 = 0.0
            var acc4 = 0.0; var acc5 = 0.0; var acc6 = 0.0; var acc7 = 0.0
            
            for (p in 0 until k) {
                val a = aRow[p]
                acc0 += a * Bt[j + 0][p]
                acc1 += a * Bt[j + 1][p]
                acc2 += a * Bt[j + 2][p]
                acc3 += a * Bt[j + 3][p]
                acc4 += a * Bt[j + 4][p]
                acc5 += a * Bt[j + 5][p]
                acc6 += a * Bt[j + 6][p]
                acc7 += a * Bt[j + 7][p]
            }
            
            result[i, j + 0] = acc0; result[i, j + 1] = acc1; result[i, j + 2] = acc2; result[i, j + 3] = acc3
            result[i, j + 4] = acc4; result[i, j + 5] = acc5; result[i, j + 6] = acc6; result[i, j + 7] = acc7
            j += tile
        }
        
        while (j < n) {
            var acc = 0.0
            for (p in 0 until k) {
                acc += A[i, p] * Bt[j][p]
            }
            result[i, j] = acc
            j++
        }
    }
    
    return result
}

/**
 * Simple matrix multiplication for small matrices
 */
private fun matrixMultiplySimple(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    val m = A.rows
    val k = A.cols
    val n = B.cols
    
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    val result = createMatrix(m, n, useSinglePrecision)
    
    for (i in 0 until m) {
        for (j in 0 until n) {
            var sum = 0.0
            for (p in 0 until k) {
                sum += A[i, p] * B[p, j]
            }
            result[i, j] = sum
        }
    }
    
    return result
}

/**
 * Transposes a FlexibleMatrix
 */
fun transpose(matrix: FlexibleMatrix): FlexibleMatrix {
    if (matrix.rows == 0) return createMatrix(0, 0, matrix.isSinglePrecision)
    return createMatrix(matrix.cols, matrix.rows, matrix.isSinglePrecision) { r, c -> matrix[c, r] }
}

/**
 * Element-wise operations
 */
fun add(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    require(A.rows == B.rows && A.cols == B.cols) { "Matrix dimensions must match for addition" }
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    return createMatrix(A.rows, A.cols, useSinglePrecision) { i, j -> A[i, j] + B[i, j] }
}

fun subtract(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    require(A.rows == B.rows && A.cols == B.cols) { "Matrix dimensions must match for subtraction" }
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    return createMatrix(A.rows, A.cols, useSinglePrecision) { i, j -> A[i, j] - B[i, j] }
}

fun elementWiseMultiply(A: FlexibleMatrix, B: FlexibleMatrix): FlexibleMatrix {
    require(A.rows == B.rows && A.cols == B.cols) { "Matrix dimensions must match for element-wise multiplication" }
    val useSinglePrecision = A.isSinglePrecision && B.isSinglePrecision
    return createMatrix(A.rows, A.cols, useSinglePrecision) { i, j -> A[i, j] * B[i, j] }
}

fun scalarMultiply(matrix: FlexibleMatrix, scalar: Double): FlexibleMatrix {
    return createMatrix(matrix.rows, matrix.cols, matrix.isSinglePrecision) { i, j -> matrix[i, j] * scalar }
}

/**
 * Adds a vector to each row of the matrix
 */
fun addVectorToRows(matrix: FlexibleMatrix, vector: DoubleArray): FlexibleMatrix {
    require(matrix.cols == vector.size) { "Vector size must match matrix column count" }
    return createMatrix(matrix.rows, matrix.cols, matrix.isSinglePrecision) { i, j -> matrix[i, j] + vector[j] }
}

/**
 * Applies a function element-wise to the matrix
 */
inline fun applyElementWise(matrix: FlexibleMatrix, crossinline transform: (Double) -> Double): FlexibleMatrix {
    return createMatrix(matrix.rows, matrix.cols, matrix.isSinglePrecision) { i, j -> transform(matrix[i, j]) }
}

/**
 * Computes column sums
 */
fun sumColumns(matrix: FlexibleMatrix): DoubleArray {
    val result = DoubleArray(matrix.cols)
    for (i in 0 until matrix.rows) {
        for (j in 0 until matrix.cols) {
            result[j] += matrix[i, j]
        }
    }
    return result
}

/**
 * Softmax activation function
 */
fun softmax(matrix: FlexibleMatrix): FlexibleMatrix {
    val result = createMatrix(matrix.rows, matrix.cols, matrix.isSinglePrecision)
    
    for (i in 0 until matrix.rows) {
        // Find max value in the row for numerical stability
        var max = if (matrix.cols > 0) matrix[i, 0] else 0.0
        for (j in 1 until matrix.cols) {
            val value = matrix[i, j]
            if (value > max) max = value
        }
        
        var sumExp = 0.0
        
        // First pass: compute exp values and sum
        val expValues = DoubleArray(matrix.cols)
        for (j in 0 until matrix.cols) {
            expValues[j] = exp(matrix[i, j] - max)
            sumExp += expValues[j]
        }
        
        // Second pass: normalize
        for (j in 0 until matrix.cols) {
            result[i, j] = expValues[j] / sumExp
        }
    }
    
    return result
}


/**
 * Extension function to flatten FlexibleMatrix to DoubleArray
 */
fun FlexibleMatrix.flatten(): DoubleArray {
    val result = DoubleArray(rows * cols)
    var index = 0
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            result[index++] = this[i, j]
        }
    }
    return result
}

/**
 * Mean Standard Error calculation
 */
fun FlexibleMatrix.meanStandardError(actual: FlexibleMatrix): Double {
    require(this.rows == actual.rows && this.cols == actual.cols) { "Matrix dimensions must match" }
    var sum = 0.0
    var total = 0
    
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            sum += (this[i, j] - actual[i, j]).pow(2)
            total++
        }
    }
    return if (total > 0) sum / total else 0.0
}
