package com.onyxdevtools.ai.compute

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import kotlin.system.measureTimeMillis
import kotlin.random.Random

class MatrixMultiplicationBenchmarkTest {

    private lateinit var cpuBackend: CPUComputeBackend
    private lateinit var metalBackend: MetalComputeBackend

    @Before
    fun setup() {
        cpuBackend = CPUComputeBackend()
        metalBackend = MetalComputeBackend()
    }

    @After
    fun teardown() {
        metalBackend.dispose()
    }

    private fun createRandomTensor(rows: Int, cols: Int): Tensor {
        val random = Random(System.currentTimeMillis())
        return createTensor(rows, cols) { r, c -> random.nextFloat() * 100f }
    }

    @Test
    fun benchmarkMatrixMultiplicationThresholds() {
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        println("\n--- Matrix Multiplication Benchmarks (CPU Sequential vs. CPU Parallel vs. Metal) ---")

        // Define matrix dimensions to test (rowsA, colsA, colsB)
        val testDimensions = listOf(
            Triple(32, 32, 32),
            Triple(64, 64, 64),
            Triple(128, 128, 128),
            Triple(200, 200, 200), // Intermediate size
            Triple(256, 256, 256),
            Triple(300, 300, 300), // Intermediate size
            Triple(400, 400, 400), // Intermediate size
            Triple(512, 512, 512),
            Triple(700, 700, 700), // Intermediate size
            Triple(1024, 1024, 1024),
            Triple(1500, 1500, 1500), // Intermediate size

            // Rectangular matrices
            Triple(20, 60, 60),
            Triple(40, 120, 120),
            Triple(80, 240, 240),
            Triple(160, 512, 512),

            Triple(40, 20, 60),
            Triple(120, 40, 120),
            Triple(240, 80, 240),
            Triple(512, 160, 512),


            Triple(100, 500, 100),
            Triple(500, 100, 500),
            Triple(100, 1000, 100),
            Triple(1000, 100, 1000),
            Triple(50, 2000, 50),
            Triple(50, 3000, 50),
//            Triple(2000, 50, 2000)
        )

        val warmUpIterations = 5 // Number of warm-up runs
        val measurementIterations = 10 // Number of actual measurement runs

        for ((rowsA, colsA, colsB) in testDimensions) {
            val matrixA = createRandomTensor(rowsA, colsA)
            val matrixB = createRandomTensor(colsA, colsB) // colsA must equal rowsB

            println("\nBenchmarking ${rowsA}x${colsA} * ${colsA}x${colsB} (Total Ops: ${rowsA.toLong() * colsA.toLong() * colsB.toLong()})")

            // Warm-up phase
            for (i in 0 until warmUpIterations) {
                cpuBackend.matrixMultiplyBasic(matrixA, matrixB)
                cpuBackend.matrixMultiplyParallelJVM(matrixA, matrixB)
                cpuBackend.matrixMultiplySkinnyVectorizedParallel(matrixA, matrixB)
                metalBackend.matrixMultiply(matrixA, matrixB)
            }

            // --- CPU Sequential Benchmark ---
            var totalCpuSequentialTime = 0L
            for (i in 0 until measurementIterations) {
                totalCpuSequentialTime += measureTimeMillis {
                    cpuBackend.matrixMultiplyBasic(matrixA, matrixB)
                }
            }
            val cpuSequentialTime = totalCpuSequentialTime / measurementIterations
            println("  CPU Sequential: ${cpuSequentialTime} ms")

            // --- CPU Parallel Benchmark ---
            var totalCpuParallelTime = 0L
            for (i in 0 until measurementIterations) {
                totalCpuParallelTime += measureTimeMillis {
                    cpuBackend.matrixMultiplyParallelJVM(matrixA, matrixB)
                }
            }
            val cpuParallelTime = totalCpuParallelTime / measurementIterations
            println("  CPU Parallel:   ${cpuParallelTime} ms")

            // --- Vectorized Parallel Benchmark ---
            var vectorCpuTime = 0L
            for (i in 0 until measurementIterations) {
                vectorCpuTime += measureTimeMillis {
                    cpuBackend.matrixMultiplySkinnyVectorizedParallel(matrixA, matrixB)
                }
            }
            val results = vectorCpuTime / measurementIterations
            println("  CPU Parallel Vector:   ${results} ms")

            // --- CPU Parallel Benchmark ---
            var totalCpuVectorzedParallelTime = 0L
            for (i in 0 until measurementIterations) {
                totalCpuVectorzedParallelTime += measureTimeMillis {
                    cpuBackend.matrixMultiplyParallelJVM(matrixA, matrixB)
                }
            }
            val cpuVectorzedParallelTime = totalCpuVectorzedParallelTime / measurementIterations
            println("  Vectorized CPU Parallel:   ${cpuVectorzedParallelTime} ms")

            // --- Metal Benchmark ---
            var totalMetalTime = 0L
            for (i in 0 until measurementIterations) {
                totalMetalTime += measureTimeMillis {
                    metalBackend.matrixMultiply(matrixA, matrixB)
                }
            }
            val metalTime = totalMetalTime / measurementIterations
            println("  Metal:          ${metalTime} ms")

            // Calculate and print ratios
            if (metalTime > 0) { // Avoid division by zero
                if (cpuSequentialTime > 0) {
                    println("  Ratio (CPU Seq / Metal): %.2f".format(cpuSequentialTime.toDouble() / metalTime.toDouble()))
                } else {
                    println("  Ratio (CPU Seq / Metal): N/A (CPU Seq time was 0)")
                }
                if (cpuParallelTime > 0) {
                    println("  Ratio (CPU Par / Metal): %.2f".format(cpuParallelTime.toDouble() / metalTime.toDouble()))
                } else {
                    println("  Ratio (CPU Par / Metal): N/A (CPU Par time was 0)")
                }
            } else {
                println("  Ratio (X / Metal): N/A (Metal time was 0)")
            }
            if (cpuParallelTime > 0) {
                if (cpuSequentialTime > 0) {
                    println("  Ratio (CPU Seq / CPU Par): %.2f".format(cpuSequentialTime.toDouble() / cpuParallelTime.toDouble()))
                } else {
                    println("  Ratio (CPU Seq / CPU Par): N/A (CPU Seq time was 0)")
                }
            } else {
                println("  Ratio (CPU Seq / CPU Par): N/A (CPU Par time was 0)")
            }
        }
    }

    // The element-wise benchmark is kept separate as it's not directly related to the matrix multiplication thresholding
    @Test
    fun benchmarkCpuVsMetalElementWiseMultiplication() {
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        val matrixSize = 2000
        val matrixA = createRandomTensor(matrixSize, matrixSize)
        val matrixB = createRandomTensor(matrixSize, matrixSize)

        println("\n--- Element-wise Multiplication Benchmark (${matrixSize}x${matrixSize}) ---")

        // Warm-up phase
        for (i in 0 until 5) {
            cpuBackend.elementWiseMultiply(matrixA, matrixB)
            metalBackend.elementWiseMultiply(matrixA, matrixB)
        }

        // --- CPU Benchmark ---
        var totalCpuTime = 0L
        for (i in 0 until 10) {
            totalCpuTime += measureTimeMillis {
                cpuBackend.elementWiseMultiply(matrixA, matrixB)
            }
        }
        val cpuTime = totalCpuTime / 10
        println("  CPU Element-wise Multiplication: ${cpuTime} ms")

        // --- Metal Benchmark ---
        var totalMetalTime = 0L
        for (i in 0 until 10) {
            totalMetalTime += measureTimeMillis {
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
