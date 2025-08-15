package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies
import java.util.stream.IntStream

open class CPUComputeBackend : BasicCPUComputeBackend() {

    // Vector API
    private val FLOAT_SPEC: VectorSpecies<Float>? by lazy {
        try { FloatVector.SPECIES_PREFERRED } catch (_: Throwable) { null }
    }

    private companion object {
        const val SIMPLE_WORK_MAX = 4_000_000L
        val isVectorAPIAvailable: Boolean by lazy {
            try { FloatVector.SPECIES_PREFERRED; true } catch (_: Throwable) { false }
        }
    }

    override fun matrixMultiply(a: Tensor, b: Tensor): Tensor {
        require(a.cols == b.rows) {
            "Matrix dimensions don't match for multiplication: ${a.rows}x${a.cols} * ${b.rows}x${b.cols}"
        }

        val m = a.rows.toLong()
        val totalOps = a.rows.toLong() * a.cols.toLong() * b.cols.toLong()

        // No Vector API available → choose basic vs parallel-JVM based on problem size
        if (FLOAT_SPEC == null) {
            return if (totalOps <= SIMPLE_WORK_MAX) {
                matrixMultiplyBasic(a, b)
            } else {
                matrixMultiplyParallelJVM(a, b)
            }
        }

        // Vector API present
        return if (!isVectorAPIAvailable || totalOps <= SIMPLE_WORK_MAX / 2) {
            matrixMultiplyBasic(a, b)
        } else {
            matrixMultiplySkinnyVectorizedParallel(a, b)
        }
    }

    /** Parallel JVM GEMM with tiled transpose of B for cache-friendly access. */
    internal fun matrixMultiplyParallelJVM(A: Tensor, B: Tensor): Tensor {
        val m = A.rows
        val k = A.cols
        val n = B.cols

        val out = createTensor(m, n)

        val Bt = transposeParallelTiled(B) // n x k (FloatArray rows)

        val blockSize = 64
        val numRowBlocks = (m + blockSize - 1) / blockSize

        IntStream.range(0, numRowBlocks).parallel().forEach { blockIndex ->
            val rowStart = blockIndex * blockSize
            val rowEnd = minOf(rowStart + blockSize, m)

            var colStart = 0
            while (colStart < n) {
                val colEnd = minOf(colStart + blockSize, n)

                var kStart = 0
                while (kStart < k) {
                    val kEnd = minOf(kStart + blockSize, k)

                    var i = rowStart
                    while (i < rowEnd) {
                        // pull A row segment as needed
                        var j = colStart
                        while (j < colEnd) {
                            var acc = 0.0f
                            var p = kStart
                            while (p < kEnd) {
                                acc += A[i, p] * Bt[j][p] // Bt[j] is FloatArray
                                p++
                            }
                            out[i, j] = out[i, j] + acc
                            j++
                        }
                        i++
                    }
                    kStart += blockSize
                }
                colStart += blockSize
            }
        }
        return out
    }

    /** Transpose B into FloatArray blocks for fast column access in GEMM/Vector API. */
    internal fun transposeParallelTiled(B: Tensor, tile: Int = 64): Array<FloatArray> {
        val k = B.rows
        val n = B.cols
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
                    // read one source row as array once
                    val src: FloatArray = B[i].toFloatArray()
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

    /** Vectorized (FloatVector) × parallel over rows; uses Bt rows (FloatArray) for fast loads. */
    internal fun matrixMultiplySkinnyVectorizedParallel(A: Tensor, B: Tensor): Tensor {
        val m = A.rows
        val k = A.cols
        val n = B.cols

        val Bt = transposeParallelTiled(B, tile = 64) // n x k

        val out = createTensor(m, n)
        val species = FLOAT_SPEC!!
        val L = species.length()

        IntStream.range(0, m).parallel().forEach { i ->
            val aRow: FloatArray = A[i].toFloatArray()
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
                out[i, j] = acc
                j++
            }
        }
        return out
    }
}
