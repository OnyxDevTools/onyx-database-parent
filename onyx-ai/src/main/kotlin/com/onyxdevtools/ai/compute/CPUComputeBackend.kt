package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix
import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies
import java.util.stream.IntStream
import kotlin.math.*

/**
 * High-performance CPU compute backend that extends BasicCPUComputeBackend 
 * with SIMD vectorization and other optimizations for maximum performance 
 * on modern CPUs that support the Java Vector API.
 * 
 * This backend uses:
 * - Java Vector API for SIMD operations (falls back to basic implementation if not available)
 * - Cache-friendly algorithms
 * - Multi-threading for large operations
 * - Optimized memory access patterns
 * 
 * Falls back to BasicCPUComputeBackend implementation if Java Vector API is not available.
 */
class CPUComputeBackend : BasicCPUComputeBackend() {
    
    // Vector API Specifications
    private val FLOAT_SPEC: VectorSpecies<Float>? by lazy { 
        try {
            FloatVector.SPECIES_PREFERRED
        } catch (e: Throwable) {
            // Vector API not available, will use basic implementations
            null
        }
    }
    
    // Performance constants
    private companion object {
        const val SIMPLE_WORK_MAX = 50_000
        const val SKINNY_DIM = 64
        const val PARALLEL_ROWS_MIN = 64
        const val PARALLEL_COLS_MIN = 256
        
        /**
         * Check if the Java Vector API is available on this platform
         */
        fun isVectorAPIAvailable(): Boolean {
            return try {
                FloatVector.SPECIES_PREFERRED
                true
            } catch (e: Throwable) {
                false
            }
        }
    }
    
    override fun matrixMultiply(a: Matrix, b: Matrix): Matrix {
        require(a[0].size == b.size) { 
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}" 
        }
        
        // If Vector API is not available, fall back to basic implementation
        if (FLOAT_SPEC == null) {
            return super.matrixMultiply(a, b)
        }
        
        val m = a.size.toLong()
        val k = a[0].size.toLong()
        val n = b[0].size.toLong()

        val work = m * k * n
        if (work <= SIMPLE_WORK_MAX) return matrixMultiplyBasic(a, b)

        val skinny = minOf(m, n) < SKINNY_DIM
        val shouldParallelize = (m >= PARALLEL_ROWS_MIN) || (n >= PARALLEL_COLS_MIN)

        return if (skinny) {
            if (shouldParallelize) matrixMultiplySkinnyVectorizedParallel(a, b)
            else matrixMultiplySkinnyVectorized(a, b)
        } else {
            matrixMultiplyParallelJVM(a, b)
        }
    }
    
    // Override other operations with vectorized versions if beneficial
    override fun add(a: Matrix, b: Matrix): Matrix {
        return if (FLOAT_SPEC != null && a.isNotEmpty() && a[0].size > 16) {
            addVectorized(a, b)
        } else {
            super.add(a, b)
        }
    }
    
    override fun scalarMultiply(matrix: Matrix, scalar: Float): Matrix {
        return if (FLOAT_SPEC != null && matrix.isNotEmpty() && matrix[0].size > 16) {
            scalarMultiplyVectorized(matrix, scalar)
        } else {
            super.scalarMultiply(matrix, scalar)
        }
    }
    
    // Private optimized implementations
    
    private fun addVectorized(a: Matrix, b: Matrix): Matrix {
        val species = FLOAT_SPEC!!
        
        return a.mapIndexed { rowIndex, row ->
            val rowB = b[rowIndex]
            val result = FloatArray(row.size)
            
            var i = 0
            val loopBound = species.loopBound(row.size)
            
            // Vectorized loop
            while (i < loopBound) {
                val va = FloatVector.fromArray(species, row, i)
                val vb = FloatVector.fromArray(species, rowB, i)
                va.add(vb).intoArray(result, i)
                i += species.length()
            }
            
            // Handle remaining elements
            while (i < row.size) {
                result[i] = row[i] + rowB[i]
                i++
            }
            
            result
        }.toTypedArray()
    }
    
    private fun scalarMultiplyVectorized(matrix: Matrix, scalar: Float): Matrix {
        val species = FLOAT_SPEC!!
        val scalarVector = FloatVector.broadcast(species, scalar)
        
        return matrix.map { row ->
            val result = FloatArray(row.size)
            
            var i = 0
            val loopBound = species.loopBound(row.size)
            
            // Vectorized loop
            while (i < loopBound) {
                val va = FloatVector.fromArray(species, row, i)
                va.mul(scalarVector).intoArray(result, i)
                i += species.length()
            }
            
            // Handle remaining elements
            while (i < row.size) {
                result[i] = row[i] * scalar
                i++
            }
            
            result
        }.toTypedArray()
    }
    
    private fun matrixMultiplyParallelJVM(A: Matrix, B: Matrix): Matrix {
        val m = A.size
        val k = A[0].size
        val n = B[0].size

        val result = Array(m) { FloatArray(n) }

        val blockSize = 64
        if (m > blockSize) {
            val BTransposed = transposeParallelTiled(B)
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
            return matrixMultiplyBasic(A, B)
        }
    }
    
    private fun matrixMultiplySkinnyVectorized(A: Matrix, B: Matrix): Matrix {
        val m = A.size
        val k = A[0].size
        val n = B[0].size

        val result = Array(m) { FloatArray(n) }
        return matrixMultiplySkinnyVectorizedFloat(A, B, result)
    }

    private fun transposeParallelTiled(B: Array<FloatArray>, tile: Int = 64): Array<FloatArray> {
        val k = B.size
        val n = B[0].size
        val Bt = Array(n) { FloatArray(k) }

        val tCols = (n + tile - 1) / tile
        val tRows = (k + tile - 1) / tile

        IntStream.range(0, tCols).parallel().forEach { tj ->
            val j0 = tj * tile
            val j1 = minOf(n, j0 + tile)
            var ti = 0
            while (ti < tRows) {
                val i0 = ti * tile
                val i1 = minOf(k, i0 + tile)
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

    private fun matrixMultiplySkinnyVectorizedFloat(A: Matrix, B: Matrix, result: Matrix): Matrix {
        val m = A.size
        val k = A[0].size
        val n = B[0].size
        val tile = 8
        val species = FLOAT_SPEC!!

        val Bt = transposeParallelTiled(B, 64)

        for (i in 0 until m) {
            val aRow = A[i]
            val resultRow = result[i]
            var j = 0

            while (j + tile <= n) {
                var acc0 = 0.0f; var acc1 = 0.0f; var acc2 = 0.0f; var acc3 = 0.0f
                var acc4 = 0.0f; var acc5 = 0.0f; var acc6 = 0.0f; var acc7 = 0.0f

                var p = 0
                val upper = species.loopBound(k)

                while (p < upper) {
                    val va = FloatVector.fromArray(species, aRow, p)
                    val vb0 = FloatVector.fromArray(species, Bt[j + 0], p)
                    val vb1 = FloatVector.fromArray(species, Bt[j + 1], p)
                    val vb2 = FloatVector.fromArray(species, Bt[j + 2], p)
                    val vb3 = FloatVector.fromArray(species, Bt[j + 3], p)
                    val vb4 = FloatVector.fromArray(species, Bt[j + 4], p)
                    val vb5 = FloatVector.fromArray(species, Bt[j + 5], p)
                    val vb6 = FloatVector.fromArray(species, Bt[j + 6], p)
                    val vb7 = FloatVector.fromArray(species, Bt[j + 7], p)

                    acc0 += va.mul(vb0).reduceLanes(VectorOperators.ADD)
                    acc1 += va.mul(vb1).reduceLanes(VectorOperators.ADD)
                    acc2 += va.mul(vb2).reduceLanes(VectorOperators.ADD)
                    acc3 += va.mul(vb3).reduceLanes(VectorOperators.ADD)
                    acc4 += va.mul(vb4).reduceLanes(VectorOperators.ADD)
                    acc5 += va.mul(vb5).reduceLanes(VectorOperators.ADD)
                    acc6 += va.mul(vb6).reduceLanes(VectorOperators.ADD)
                    acc7 += va.mul(vb7).reduceLanes(VectorOperators.ADD)

                    p += species.length()
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

    private fun matrixMultiplySkinnyVectorizedParallel(A: Matrix, B: Matrix): Matrix {
        val m = A.size
        val k = A[0].size
        val n = B[0].size

        val Bt = transposeParallelTiled(B, tile = 64)

        val result = Array(m) { FloatArray(n) }
        val species = FLOAT_SPEC!!
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
                    accVec = av.fma(bv, accVec)
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
}
