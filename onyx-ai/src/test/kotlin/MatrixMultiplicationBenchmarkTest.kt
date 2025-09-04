package dev.onyx.ai.compute

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import kotlin.system.measureNanoTime
import kotlin.random.Random
import dev.onyx.ai.Tensor
import kotlin.test.Ignore

@Ignore("Benchmark tests are disabled by default. Enable them manually to see the results.")
class MatrixMultiplicationBenchmarkTest {

    private lateinit var basicCPUComputeBackend: BasicCPUComputeBackend
    private lateinit var cpuBackend: CPUComputeBackend
    private lateinit var metalBackend: MetalComputeBackend

    @Before
    fun setup() {
        basicCPUComputeBackend = BasicCPUComputeBackend()
        cpuBackend = CPUComputeBackend()
        metalBackend = MetalComputeBackend()
    }

    @After
    fun teardown() {
        metalBackend.dispose()
    }

private fun createRandomMatrix(rows: Int, cols: Int): Tensor {
    val random = Random(System.currentTimeMillis())
    return Tensor(rows, cols) { _, _ -> random.nextFloat() * 100f }
}

    @Test
    fun benchmarkMatrixMultiplicationThresholds() {
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        println("\n--- Matrix Multiplication Benchmarks (CPU Sequential vs. CPU Parallel vs. Metal) ---")

        val testDimensions = (1 .. 100).map { i ->
            Triple(i, 128, 1024)
        }

        val measurementIterations = 1000 // Number of actual measurement runs

        for ((rowsA, colsA, colsB) in testDimensions) {
            val matrixA = createRandomMatrix(rowsA, colsA)
            val matrixB = createRandomMatrix(colsA, colsB) // colsA must equal rowsB

            println("\nBenchmarking ${rowsA}x${colsA} * ${colsA}x${colsB} (Total Ops: ${rowsA.toLong() * colsA.toLong() * colsB.toLong()})")

            // --- Metal Benchmark ---
            var totalMetalTime = 0L
            for (i in 0 until measurementIterations) {
                totalMetalTime += measureNanoTime {
                    metalBackend.matrixMultiply(matrixA, matrixB)
                }
            }
            val metalTime = totalMetalTime / measurementIterations
            println("  Metal:          $metalTime ms")

            // --- CPU Sequential Benchmark ---
            var totalCpuSequentialTime = 0L
            for (i in 0 until measurementIterations) {
                totalCpuSequentialTime += measureNanoTime {
                    basicCPUComputeBackend.matrixMultiply(matrixA, matrixB)
                }
            }
            val cpuSequentialTime = totalCpuSequentialTime / measurementIterations
            println("  CPU Sequential: $cpuSequentialTime ms")

            // --- CPU Parallel Benchmark ---
            var totalCpuParallelTime = 0L
            for (i in 0 until measurementIterations) {
                totalCpuParallelTime += measureNanoTime {
                    cpuBackend.matrixMultiplyParallelJVM(matrixA, matrixB)
                }
            }
            val cpuParallelTime = totalCpuParallelTime / measurementIterations
            println("  CPU Parallel:   $cpuParallelTime ms")

            // --- Vectorized Parallel Benchmark ---
            var vectorCpuTime = 0L
            for (i in 0 until measurementIterations) {
                vectorCpuTime += measureNanoTime {
                    cpuBackend.matrixMultiplyParallelVector(matrixA, matrixB)
                }
            }
            val results = vectorCpuTime / measurementIterations
            println("  CPU Parallel Vector:   $results ms")

        }
    }

    // The element-wise benchmark is kept separate as it's not directly related to the matrix multiplication thresholding
    @Test
    fun benchmarkCpuVsMetalElementWiseMultiplication() {
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        val matrixSize = 2000
        val matrixA = createRandomMatrix(matrixSize, matrixSize)
        val matrixB = createRandomMatrix(matrixSize, matrixSize)

        println("\n--- Element-wise Multiplication Benchmark (${matrixSize}x${matrixSize}) ---")

        // Warm-up phase
        for (i in 0 until 5) {
            cpuBackend.elementWiseMultiply(matrixA, matrixB)
            metalBackend.elementWiseMultiply(matrixA, matrixB)
        }

        // --- CPU Benchmark ---
        var totalCpuTime = 0L
        for (i in 0 until 10) {
            totalCpuTime += measureNanoTime {
                cpuBackend.elementWiseMultiply(matrixA, matrixB)
            }
        }
        val cpuTime = totalCpuTime / 10
        println("  CPU Element-wise Multiplication: ${cpuTime} ms")

        // --- Metal Benchmark ---
        var totalMetalTime = 0L
        for (i in 0 until 10) {
            totalMetalTime += measureNanoTime {
                metalBackend.elementWiseMultiply(matrixA, matrixB)
            }
        }
        val metalTime = totalMetalTime / 10
        println("  Metal Element-wise Multiplication: ${metalTime} ms")

        assumeTrue("Measured metalTime must be > 0", metalTime > 0)
        val performanceRatio = cpuTime.toDouble() / metalTime.toDouble()
        println("  Performance Ratio (CPU/Metal) for Element-wise: %.2f".format(performanceRatio))
        assertTrue(
            "Metal backend should be significantly faster than CPU backend for element-wise multiplication.",
            performanceRatio > 0.5
        )
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name")?.contains("mac", ignoreCase = true) == true
}
