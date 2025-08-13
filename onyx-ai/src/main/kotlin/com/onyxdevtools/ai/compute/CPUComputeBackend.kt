package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Matrix
import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies
import java.util.stream.IntStream

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
open class CPUComputeBackend : BasicCPUComputeBackend() {
    
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
        // Threshold for using basic sequential multiplication (total operations)
        const val SIMPLE_WORK_MAX = 4_000_000L

        /**
         * Check if the Java Vector API is available on this platform
         */
        val isVectorAPIAvailable: Boolean by lazy {
            return@lazy try {
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
        return if (work <= SIMPLE_WORK_MAX) {
            matrixMultiplyBasic(a, b)
        } else {
            if (isVectorAPIAvailable) {
                matrixMultiplySkinnyVectorizedParallel(a,b)
            } else {
                // For larger matrices, always use the parallel JVM implementation
                // This implicitly leverages vectorization via the Java Vector API when available
                matrixMultiplySkinnyVectorizedParallel(a, b)
            }
        }
    }

    internal fun matrixMultiplyParallelJVM(A: Matrix, B: Matrix): Matrix {
        val m = A.size
        val k = A[0].size
        val n = B[0].size

        val result = Array(m) { FloatArray(n) }

        val blockSize = 64
        // Always parallelize if work is above SIMPLE_WORK_MAX
        // The transposeParallelTiled is already parallelized
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
    }

    internal fun transposeParallelTiled(B: Array<FloatArray>, tile: Int = 64): Array<FloatArray> {
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

    internal fun matrixMultiplySkinnyVectorizedParallel(A: Matrix, B: Matrix): Matrix {
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
