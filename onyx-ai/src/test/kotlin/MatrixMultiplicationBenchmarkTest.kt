package com.onyxdevtools.ai.compute

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import kotlin.system.measureTimeMillis

class MatrixMultiplicationBenchmarkTest {

    private val matrixSize = 1024*3 // Large matrix for benchmarking
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

    @Test
    fun benchmarkCpuVsMetalMatrixMultiplication() {
        // Skip test unless running on macOS and Metal is available
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        // Initialize matrices as Array<FloatArray>
        val matrixA = Array(matrixSize) { r -> FloatArray(matrixSize) { c -> (r * matrixSize + c).toFloat() } }
        val matrixB = Array(matrixSize) { r -> FloatArray(matrixSize) { c -> (r * matrixSize + c).toFloat() } }

        // --- CPU Benchmark ---
        val cpuTime = measureTimeMillis {
            cpuBackend.matrixMultiply(matrixA, matrixB)
        }
        println("CPU Matrix Multiplication (${matrixSize}x${matrixSize}): ${cpuTime} ms")

        // --- Metal Benchmark ---
        val bytesA = matrixSize * matrixSize * Float.SIZE_BYTES
        val bytesB = bytesA
        val bytesR = matrixSize * matrixSize * Float.SIZE_BYTES

        val metalTime = measureTimeMillis {
            val bufferA = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesA)
            val bufferB = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesB)
            val bufferResult = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesR)

            MetalComputeBackend.copyToGPU(metalBackend.metalContext, bufferA, cpuBackend.flatten(matrixA))
            MetalComputeBackend.copyToGPU(metalBackend.metalContext, bufferB, cpuBackend.flatten(matrixB))

            MetalComputeBackend.gpuMatrixMultiply(
                metalBackend.metalContext,
                bufferA, matrixSize, matrixSize,
                bufferB, matrixSize, matrixSize,
                bufferResult
            )

            // Read back to ensure GPU work completes before timing ends
            MetalComputeBackend.copyFromGPU(metalBackend.metalContext, bufferResult, matrixSize * matrixSize)

            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferA)
            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferB)
            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferResult)
        }
        println("Metal Matrix Multiplication (${matrixSize}x${matrixSize}): ${metalTime} ms")

        // Guard against divide-by-zero if Metal returns instantly (shouldn’t, but be safe)
        assumeTrue("Measured metalTime must be > 0", metalTime > 0)

        // Assert Metal is faster (tune threshold to your environment)
        val performanceRatio = cpuTime.toDouble() / metalTime.toDouble()
        println("Performance Ratio (CPU/Metal): %.2f".format(performanceRatio))
        assertTrue(
            "Metal backend should be significantly faster than CPU backend for matrix multiplication.",
            performanceRatio > 1.5
        )
    }

    @Test
    fun benchmarkCpuVsMetalElementWiseMultiplication() {
        // Skip test unless running on macOS and Metal is available
        assumeTrue(isMacOs())
        assumeTrue("Metal is not available on this system", MetalComputeBackend.isMetalAvailable())

        // Initialize matrices as Array<FloatArray>
        val matrixA = Array(matrixSize) { r -> FloatArray(matrixSize) { c -> (r * matrixSize + c).toFloat() } }
        val matrixB = Array(matrixSize) { r -> FloatArray(matrixSize) { c -> (r * matrixSize + c).toFloat() } }

        // --- CPU Benchmark ---
        val cpuTime = measureTimeMillis {
            cpuBackend.elementWiseMultiply(matrixA, matrixB)
        }
        println("CPU Element-wise Multiplication (${matrixSize}x${matrixSize}): ${cpuTime} ms")

        // --- Metal Benchmark ---
        val bytesA = matrixSize * matrixSize * Float.SIZE_BYTES
        val bytesB = bytesA
        val bytesR = matrixSize * matrixSize * Float.SIZE_BYTES

        val metalTime = measureTimeMillis {
            val bufferA = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesA)
            val bufferB = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesB)
            val bufferResult = MetalComputeBackend.createGPUBuffer(metalBackend.metalContext, bytesR)

            MetalComputeBackend.copyToGPU(metalBackend.metalContext, bufferA, cpuBackend.flatten(matrixA))
            MetalComputeBackend.copyToGPU(metalBackend.metalContext, bufferB, cpuBackend.flatten(matrixB))

            MetalComputeBackend.gpuElementWiseOperation(
                metalBackend.metalContext,
                bufferA, bufferB, bufferResult,
                matrixSize, matrixSize, 2 // 2 = multiply
            )

            // Read back to ensure GPU work completes before timing ends
            MetalComputeBackend.copyFromGPU(metalBackend.metalContext, bufferResult, matrixSize * matrixSize)

            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferA)
            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferB)
            MetalComputeBackend.releaseGPUBuffer(metalBackend.metalContext, bufferResult)
        }
        println("Metal Element-wise Multiplication (${matrixSize}x${matrixSize}): ${metalTime} ms")

        // Guard against divide-by-zero if Metal returns instantly (shouldn’t, but be safe)
        assumeTrue("Measured metalTime must be > 0", metalTime > 0)

        // Assert Metal is faster (tune threshold to your environment)
        val performanceRatio = cpuTime.toDouble() / metalTime.toDouble()
        println("Performance Ratio (CPU/Metal) for Element-wise: %.2f".format(performanceRatio))
        assertTrue(
            "Metal backend should be significantly faster than CPU backend for element-wise multiplication.",
            performanceRatio > 1.5
        )
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name")?.contains("mac", ignoreCase = true) == true
}
