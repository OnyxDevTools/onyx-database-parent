package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorSpecies
import java.util.stream.IntStream
import kotlin.math.min

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

    // Performance constants
    private companion object {
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

    override fun matrixMultiply(a: Tensor, b: Tensor): Tensor {
        require(a[0].size == b.size) {
            "Matrix dimensions don't match for multiplication: ${a.size}x${a[0].size} * ${b.size}x${b[0].size}"
        }

        val m = a.size.toLong()
        val k = a[0].size.toLong()
        val n = b[0].size.toLong()
        val operations = m * k * n

        return when {
            operations <= 50_000 -> super.matrixMultiply(a, b)
            isVectorAPIAvailable -> matrixMultiplyParallelJVM(a, b)
            else -> matrixMultiplyParallelVector(a, b)
        }
    }

    /**
     * Parallel matrix multiplication using CPU cores.
     * Splits rows across threads and uses K-tiling to improve cache locality.
     */
    internal fun matrixMultiplyParallelJVM(
        A: Tensor,
        B: Tensor,
        cpus: Int = Runtime.getRuntime().availableProcessors()
    ): Tensor {
        val m = A.size
        val k = A[0].size
        val n = B[0].size
        require(k == B.size) { "Inner dimensions must match: A=${m}x${k}, B=${B.size}x${n}" }

        val C = Tensor(m, n) { _, _ -> 0f }

        val aArr = A.data
        val bArr = B.data
        val cArr = C.data

        val threads = cpus.coerceAtLeast(1)
        val rowsPerThread = (m + threads - 1) / threads

        IntStream.range(0, threads).parallel().forEach { t ->
            val rStart = t * rowsPerThread
            val rEnd = min(m, rStart + rowsPerThread)

            var r = rStart
            while (r < rEnd) {
                val aBase = r * k
                val cBase = r * n

                var s = 0
                while (s < k) {
                    val aVal = aArr[aBase + s]   // A[r, s]
                    var bIdx = s * n             // B[s, 0]
                    var cIdx = cBase             // C[r, 0]
                    var c = 0
                    while (c < n) {
                        cArr[cIdx] += aVal * bArr[bIdx]
                        c++; bIdx++; cIdx++
                    }
                    s++
                }
                r++
            }
        }

        return C
    }

    /**
     * Parallel + SIMD (Java Vector API) matrix multiply.
     * - Parallelized by rows using IntStream.parallel()
     * - Vectorized across the N (output columns) dimension with FloatVector
     *
     * Requires: --add-modules jdk.incubator.vector (JDK 21+).
     */
    internal fun matrixMultiplyParallelVector(
        A: Tensor,
        B: Tensor,
        cpus: Int = Runtime.getRuntime().availableProcessors()
    ): Tensor {
        val m = A.size
        val k = A[0].size
        val n = B[0].size
        require(k == B.size) { "Inner dimensions must match: A=${m}x${k}, B=${B.size}x${n}" }

        val tensor = Tensor(m, n) { _, _ -> 0f }

        val aArr: FloatArray = A.data
        val bArr: FloatArray = B.data
        val cArr: FloatArray = tensor.data

        val species: VectorSpecies<Float> = FloatVector.SPECIES_PREFERRED
        val VL = species.length()

        val threads = cpus.coerceAtLeast(1)
        val rowsPerThread = (m + threads - 1) / threads

        IntStream.range(0, threads).parallel().forEach { t ->
            val rStart = t * rowsPerThread
            val rEnd = min(m, rStart + rowsPerThread)

            var r = rStart
            while (r < rEnd) {
                val aBase = r * k
                val cBase = r * n

                val fullN = species.loopBound(n)
                val hasTail = fullN < n
                val tailMask = if (hasTail) species.indexInRange(fullN, n) else null

                var s = 0
                while (s < k) {
                    val aVal = aArr[aBase + s]
                    val vA = FloatVector.broadcast(species, aVal)
                    val bRowBase = s * n

                    var c = 0
                    while (c < fullN) {
                        val vB = FloatVector.fromArray(species, bArr, bRowBase + c)
                        val vC = FloatVector.fromArray(species, cArr, cBase + c)
                        vB.fma(vA, vC).intoArray(cArr, cBase + c)
                        c += VL
                    }
                    if (hasTail) {
                        val vB = FloatVector.fromArray(species, bArr, bRowBase + fullN, tailMask)
                        val vC = FloatVector.fromArray(species, cArr, cBase + fullN, tailMask)
                        vB.fma(vA, vC).intoArray(cArr, cBase + fullN, tailMask)
                    }
                    s++
                }
                r++
            }
        }

        return tensor
    }
}
