package com.onyx.vector

import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VectorCalibrationTest {

    @Test
    fun `fit and encode are deterministic and independent of sample order`() {
        val samples = randomSamples(count = 48, dimensions = 8, seed = 17L)
        val first = VectorCalibration.fit(samples, randomSeed = 918273L)
        val second = VectorCalibration.fit(samples.reversed(), randomSeed = 918273L)

        assertEquals(first, second)
        assertEquals(first.calibrationId, second.calibrationId)
        assertNotEquals(0L, first.calibrationId)

        val entropy = VectorEntropy(128)
        val query = floatArrayOf(0.3f, -0.2f, 0.8f, 0.4f, -0.7f, 0.1f, 0.6f, -0.5f)
        val firstSignature = first.encode(query, entropy)
        val secondSignature = second.encode(query, entropy)
        assertEquals(firstSignature, secondSignature)
        assertEquals(firstSignature.bucketId, first.packCells(firstSignature.cells))

        val components = first.components
        components[0][0] = 99.0
        assertNotEquals(99.0, first.components[0][0])
        for (left in first.components.indices) {
            for (right in first.components.indices) {
                val expected = if (left == right) 1.0 else 0.0
                assertEquals(expected, dot(first.components[left], first.components[right]), 1e-7)
            }
        }
    }

    @Test
    fun `product cells round trip through real mixed-radix bucket`() {
        val calibration = identityCalibration(
            dimensions = 6,
            componentCount = 6,
            firstAxisCells = 5,
            otherAxisCells = 10,
        )
        val cells = intArrayOf(2, 3, 4, 5, 6, 7)

        assertEquals(234567, calibration.packCells(cells))
        assertContentEquals(cells, calibration.unpackBucket(234567))
        assertEquals(500_000, calibration.totalBucketCount)

        cells[0] = 0
        assertContentEquals(intArrayOf(2, 3, 4, 5, 6, 7), calibration.unpackBucket(234567))
    }

    @Test
    fun `simhash supports four equal bands for every entropy size`() {
        val calibration = identityCalibration(4, 3, 3, 4)
        val embedding = floatArrayOf(0.2f, -0.9f, 0.7f, 0.4f)

        for (bits in listOf(64, 128, 192, 256)) {
            val signature = calibration.encode(embedding, VectorEntropy(bits))
            assertEquals(bits, signature.bitCount)
            assertEquals(4, signature.bands.size)
            assertContentEquals(
                signature.fingerprint,
                reconstructFingerprint(signature.bands, bits),
                "Four bands must preserve every fingerprint bit at $bits bits",
            )
        }
    }

    @Test
    fun `hamming and normalized bucket similarities have exact endpoints`() {
        val calibration = identityCalibration(2, 2, 3, 4)
        val zero = signature(calibration, intArrayOf(0, 0), longArrayOf(0L))
        val ones = signature(calibration, intArrayOf(2, 3), longArrayOf(-1L))
        val oneBit = signature(calibration, intArrayOf(0, 0), longArrayOf(1L))

        assertEquals(64, zero.hammingDistance(ones))
        assertEquals(0.0, zero.hammingSimilarity(ones), 0.0)
        assertEquals(1, zero.hammingDistance(oneBit))
        assertEquals(63.0 / 64.0, zero.hammingSimilarity(oneBit), 0.0)
        assertEquals(1.0, calibration.normalizedBucketSimilarity(zero, oneBit), 0.0)
        assertEquals(0.0, calibration.normalizedBucketSimilarity(zero, ones), 0.0)
    }

    @Test
    fun `invalid calibration samples vectors and buckets fail explicitly`() {
        assertFailsWith<IllegalArgumentException> { VectorCalibration.fit(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            VectorCalibration.fit(randomSamples(9, 8, 1L))
        }
        assertFailsWith<IllegalArgumentException> {
            VectorCalibration.fit(List(12) { FloatArray(8) { 1f } })
        }

        val malformed = randomSamples(12, 4, 2L).toMutableList()
        malformed[3] = FloatArray(3) { 1f }
        assertFailsWith<IllegalArgumentException> {
            VectorCalibration.fit(malformed, componentCount = 3, firstAxisCells = 3, otherAxisCells = 4)
        }

        val calibration = identityCalibration(4, 3, 3, 4)
        assertFailsWith<IllegalArgumentException> {
            calibration.encode(FloatArray(3) { 1f }, VectorEntropy(64))
        }
        assertFailsWith<IllegalArgumentException> {
            calibration.encode(floatArrayOf(0f, 0f, 0f, 0f), VectorEntropy(64))
        }
        assertFailsWith<IllegalArgumentException> {
            calibration.encode(floatArrayOf(Float.NaN, 1f, 2f, 3f), VectorEntropy(64))
        }
        assertFailsWith<IllegalArgumentException> { calibration.packCells(intArrayOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { calibration.packCells(intArrayOf(0, 4, 0)) }
        assertFailsWith<IllegalArgumentException> { calibration.unpackBucket(calibration.totalBucketCount) }
        assertFailsWith<IllegalArgumentException> {
            calibration.nearbyBuckets(0, radius = -1)
        }
    }

    @Test
    fun `signature retains no dense vector and is immutable by content`() {
        val samples = randomSamples(16, 4, 31L)
        val calibration = VectorCalibration.fit(
            samples,
            componentCount = 3,
            firstAxisCells = 3,
            otherAxisCells = 4,
            randomSeed = 42L,
        )
        val embedding = floatArrayOf(0.2f, -0.9f, 0.7f, 0.4f)
        val signature = calibration.encode(embedding, VectorEntropy(192))
        val expectedCells = signature.cells
        val expectedCellCounts = signature.cellCounts
        val expectedFingerprint = signature.fingerprint

        embedding.fill(99f)
        val exposedCells = signature.cells
        val exposedCellCounts = signature.cellCounts
        val exposedFingerprint = signature.fingerprint
        val exposedBands = signature.bands
        exposedCells.fill(99)
        exposedCellCounts.fill(99)
        exposedFingerprint.fill(99L)
        exposedBands.fill(99L)

        assertContentEquals(expectedCells, signature.cells)
        assertContentEquals(expectedCellCounts, signature.cellCounts)
        assertContentEquals(expectedFingerprint, signature.fingerprint)
        assertFalse(
            SemanticVectorSignature::class.java.declaredFields.any {
                it.type == FloatArray::class.java || it.type == DoubleArray::class.java
            },
            "A semantic signature must not retain a dense embedding",
        )

        val originalId = calibration.calibrationId
        samples.forEach { it.fill(0f) }
        assertEquals(originalId, calibration.calibrationId)
        assertEquals(signature, signature)
        assertEquals(signature.hashCode(), signature.hashCode())
    }

    @Test
    fun `nearby product probing respects radius axis bounds uniqueness and budget`() {
        val calibration = identityCalibration(2, 2, 3, 4)
        val origin = intArrayOf(0, 0)
        val probes = calibration.nearbyProductCells(origin, radius = 2, maxProbes = 100)

        assertContentEquals(origin, probes.first())
        assertEquals(6, probes.size)
        assertEquals(probes.size, probes.map(calibration::packCells).toSet().size)
        probes.forEach { cell ->
            assertTrue(cell[0] in 0..2)
            assertTrue(cell[1] in 0..3)
            assertTrue(abs(cell[0] - origin[0]) + abs(cell[1] - origin[1]) <= 2)
        }

        val limited = calibration.nearbyBuckets(calibration.packCells(origin), radius = 8, maxProbes = 3)
        assertEquals(3, limited.size)
        assertTrue(limited.all { it in 0 until calibration.totalBucketCount })
        assertEquals(limited.size, limited.toSet().size)
    }

    private fun identityCalibration(
        dimensions: Int,
        componentCount: Int,
        firstAxisCells: Int,
        otherAxisCells: Int,
    ): VectorCalibration {
        val components = Array(componentCount) { axis ->
            DoubleArray(dimensions).also { it[axis] = 1.0 }
        }
        val thresholds = Array(componentCount) { axis ->
            val count = if (axis == 0) firstAxisCells else otherAxisCells
            DoubleArray(count - 1) { boundary ->
                -1.0 + 2.0 * (boundary + 1).toDouble() / count.toDouble()
            }
        }
        return VectorCalibration(
            dimensions = dimensions,
            componentCount = componentCount,
            firstAxisCells = firstAxisCells,
            otherAxisCells = otherAxisCells,
            randomSeed = 99L,
            mean = DoubleArray(dimensions),
            components = components,
            quantileThresholds = thresholds,
            projectionMinimums = DoubleArray(componentCount) { -1.0 },
            projectionMaximums = DoubleArray(componentCount) { 1.0 },
        )
    }

    private fun signature(
        calibration: VectorCalibration,
        cells: IntArray,
        fingerprint: LongArray,
    ): SemanticVectorSignature = SemanticVectorSignature(
        calibrationId = calibration.calibrationId,
        bucketId = calibration.packCells(cells),
        cells = cells,
        cellCounts = calibration.cellCounts,
        fingerprint = fingerprint,
        bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
        boundaryConfidence = 1f,
    )

    private fun randomSamples(count: Int, dimensions: Int, seed: Long): List<FloatArray> {
        val random = Random(seed)
        return List(count) {
            FloatArray(dimensions) { (random.nextDouble() * 2.0 - 1.0).toFloat() }
        }
    }

    private fun reconstructFingerprint(bands: LongArray, bitCount: Int): LongArray {
        val bandBits = bitCount / bands.size
        val fingerprint = LongArray(bitCount / Long.SIZE_BITS)
        for (bandIndex in bands.indices) {
            for (bandBit in 0 until bandBits) {
                if (((bands[bandIndex] ushr bandBit) and 1L) == 0L) continue
                val targetBit = bandIndex * bandBits + bandBit
                fingerprint[targetBit / Long.SIZE_BITS] = fingerprint[targetBit / Long.SIZE_BITS] or
                    (1L shl (targetBit % Long.SIZE_BITS))
            }
        }
        return fingerprint
    }

    private fun dot(left: DoubleArray, right: DoubleArray): Double {
        var result = 0.0
        for (index in left.indices) result += left[index] * right[index]
        return result
    }
}
