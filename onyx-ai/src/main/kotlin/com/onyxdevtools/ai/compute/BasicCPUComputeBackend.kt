package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix
import kotlin.math.*

/**
 * Basic CPU compute backend that works on all platforms including Android.
 * This implementation uses only standard Kotlin/Java features without any 
 * platform-specific optimizations like Java Vector API.
 * 
 * This serves as the base implementation that other CPU backends can extend
 * with platform-specific optimizations.
 */
open class BasicCPUComputeBackend : ComputeBackend {
    
    override val backendType: ComputeBackendType = ComputeBackendType.CPU
    
    override fun matrixMultiply(a: Matrix, b: Matrix): Matrix {
        require(a[0].size == b.size) { 
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}" 
        }
        
        return matrixMultiplyBasic(a, b)
    }
    
    override fun add(a: Matrix, b: Matrix): Matrix {
        require(a.size == b.size) { 
            "Matrix row dimensions don't match for addition: ${a.size} vs ${b.size}" 
        }
        
        if (a.isEmpty()) return arrayOf()
        
        return a.mapIndexed { rowIndex, row ->
            val rowB = b[rowIndex]
            require(row.size == rowB.size) { 
                "Matrix column dimensions don't match at row $rowIndex: ${row.size} vs ${rowB.size}" 
            }
            FloatArray(row.size) { colIndex -> row[colIndex] + rowB[colIndex] }
        }.toTypedArray()
    }
    
    override fun subtract(a: Matrix, b: Matrix): Matrix {
        return a.mapIndexed { rowIndex, row ->
            FloatArray(row.size) { colIndex -> row[colIndex] - b[rowIndex][colIndex] }
        }.toTypedArray()
    }
    
    override fun elementWiseMultiply(a: Matrix, b: Matrix): Matrix {
        return a.mapIndexed { rowIndex, row ->
            FloatArray(row.size) { colIndex -> row[colIndex] * b[rowIndex][colIndex] }
        }.toTypedArray()
    }
    
    override fun transpose(matrix: Matrix): Matrix {
        return if (matrix.isEmpty()) arrayOf()
        else Array(matrix[0].size) { colIndex ->
            FloatArray(matrix.size) { rowIndex -> matrix[rowIndex][colIndex] }
        }
    }
    
    override fun scalarMultiply(matrix: Matrix, scalar: Float): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> row[colIndex] * scalar } 
        }.toTypedArray()
    }
    
    override fun addVectorToRows(matrix: Matrix, vector: FloatArray): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> row[colIndex] + vector[colIndex] } 
        }.toTypedArray()
    }
    
    override fun applyElementWise(matrix: Matrix, transform: (Float) -> Float): Matrix {
        return matrix.map { row -> 
            FloatArray(row.size) { colIndex -> transform(row[colIndex]) } 
        }.toTypedArray()
    }
    
    override fun sumColumns(matrix: Matrix): FloatArray {
        return FloatArray(if (matrix.isEmpty()) 0 else matrix[0].size).also { columnSums ->
            for (row in matrix) {
                for (colIndex in row.indices) {
                    columnSums[colIndex] += row[colIndex]
                }
            }
        }
    }
    
    override fun softmax(matrix: Matrix): Matrix {
        return matrix.map { logits ->
            val max = logits.maxOrNull() ?: 0.0f  // For numerical stability
            val expLogits = logits.map { exp(it - max) }
            val sumExp = expLogits.sum()
            expLogits.map { it / sumExp }.toFloatArray()
        }.toTypedArray()
    }
    
    override fun meanStandardError(predicted: Matrix, actual: Matrix): Float {
        var sum = 0.0f
        var total = 0

        val rows = minOf(predicted.size, actual.size)
        for (i in 0 until rows) {
            val cols = minOf(predicted[i].size, actual[i].size)
            for (j in 0 until cols) {
                sum += (predicted[i][j] - actual[i][j]).pow(2)
                total++
            }
        }
        return if (total > 0) sum / total else 0.0f
    }
    
    override fun deepCopy(matrix: Matrix): Matrix {
        return matrix.map { it.copyOf() }.toTypedArray()
    }
    
    override fun flatten(matrix: Matrix): FloatArray {
        return buildList { 
            for (row in matrix) {
                addAll(row.asList())
            }
        }.toFloatArray()
    }

    /**
     * Basic matrix multiplication implementation that works on all platforms.
     * This is the foundation that optimized implementations can build upon.
     */
    internal fun matrixMultiplyBasic(matrixA: Matrix, matrixB: Matrix): Matrix {
        val numRows = matrixA.size
        val sharedDim = matrixA[0].size
        val numCols = matrixB[0].size
        val result = Array(numRows) { FloatArray(numCols) }

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
}
