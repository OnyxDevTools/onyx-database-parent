package com.onyx.vector

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuantizedCosineVectorTest {
    @Test
    fun `byte cosine matches legacy arithmetic exactly including tails and signed extremes`() {
        val random = Random(83619)
        val dimensions = (1..129).toList() + listOf(255, 256, 257, 687, 688, 689, 1536, 16_383, 16_384)
        for (size in dimensions) {
            repeat(8) {
                val left = random.nextBytes(size).also { it[0] = Byte.MIN_VALUE }
                val right = random.nextBytes(size).also { it[0] = Byte.MAX_VALUE }
                assertLegacyCosine(left, right)
            }
        }
        val minimum = ByteArray(QuantizedCosineVector.MAX_DIMENSIONS) { Byte.MIN_VALUE }
        val maximum = ByteArray(QuantizedCosineVector.MAX_DIMENSIONS) { Byte.MAX_VALUE }
        assertLegacyCosine(minimum, minimum)
        assertLegacyCosine(maximum, maximum)
        assertLegacyCosine(minimum, maximum)
        assertLegacyCosine(minimum, ByteArray(minimum.size) { if (it % 2 == 0) -128 else 127 })
        assertLegacyCosine(byteArrayOf(127, 0), byteArrayOf(0, -128))
    }

    @Test
    fun `factories and exported bytes cannot mutate vectors or cached magnitudes`() {
        val bytes = byteArrayOf(-128, 127, 42)
        val stored = QuantizedCosineVector.fromBytes(bytes)
        val reference = QuantizedCosineVector.fromBytes(bytes.copyOf())
        val original = stored.toByteArray()
        bytes.fill(0)
        stored.toByteArray().fill(0)
        assertContentEquals(original, stored.toByteArray())
        assertEquals(1f, stored.cosineSimilarity(reference))

        for (encode in listOf(QuantizedCosineVector::fromDense, QuantizedCosineVector::fromDenseMaxAbsolute)) {
            val dense = floatArrayOf(1f, -0.5f, 0.25f)
            val vector = encode(dense)
            val expected = vector.toByteArray()
            dense.fill(0f)
            vector.toByteArray().fill(0)
            assertContentEquals(expected, vector.toByteArray())
            assertEquals(1f, vector.cosineSimilarity(QuantizedCosineVector.fromBytes(expected)))
        }
    }

    @Test
    fun `invalid stored vectors and mismatched cosine dimensions fail validation`() {
        for (invalid in listOf(byteArrayOf(), ByteArray(8), ByteArray(16_385) { 1 })) {
            assertFailsWith<IllegalArgumentException> { QuantizedCosineVector.fromBytes(invalid) }
        }
        val left = QuantizedCosineVector.fromBytes(byteArrayOf(1))
        val right = QuantizedCosineVector.fromBytes(byteArrayOf(1, 2))
        assertFailsWith<IllegalArgumentException> { left.cosineSimilarity(right) }
        assertFailsWith<IllegalArgumentException> { right.cosineSimilarity(left) }
    }

    @Test
    fun `legacy encoder preserves stored bytes and strict self similarity`() {
        val vector = floatArrayOf(1f, 0.5f)
        val stored = QuantizedCosineVector.fromBytes(byteArrayOf(114, 57))
        val legacy = QuantizedCosineVector.fromDense(vector)
        val improved = QuantizedCosineVector.fromDenseMaxAbsolute(vector)
        assertContentEquals(byteArrayOf(114, 57), legacy.toByteArray())
        assertContentEquals(byteArrayOf(127, 64), improved.toByteArray())
        assertEquals(1f, legacy.cosineSimilarity(stored))
        assertTrue(improved.cosineSimilarity(stored) < 0.999999f,
            "A global encoder change would break a valid legacy minimum-score query")
    }

    @Test
    fun `finite extreme and subnormal inputs use complete byte range without overflow`() {
        val ordinary = floatArrayOf(1f, -1f, 0.5f, 0f)
        val extreme = floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE / 2f, 0f)
        assertContentEquals(
            QuantizedCosineVector.fromDenseMaxAbsolute(ordinary).toByteArray(),
            QuantizedCosineVector.fromDenseMaxAbsolute(extreme).toByteArray(),
        )
        assertContentEquals(
            byteArrayOf(127, -127, 0),
            QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(Float.MIN_VALUE, -Float.MIN_VALUE, 0f)).toByteArray(),
        )
        assertContentEquals(
            byteArrayOf(0, -127),
            QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(Float.MIN_VALUE, -Float.MAX_VALUE)).toByteArray(),
        )
        val largestDense = QuantizedCosineVector.fromDenseMaxAbsolute(FloatArray(16_384) { Float.MAX_VALUE })
        assertTrue(largestDense.toByteArray().all { it == 127.toByte() })
        assertEquals(1f, largestDense.cosineSimilarity(largestDense))
    }

    @Test
    fun `positive scaling preserves cosine while independently scaled vectors remain comparable`() {
        val left = QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(1f, -2f, 4f))
        val scaledLeft = QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(128f, -256f, 512f))
        assertContentEquals(left.toByteArray(), scaledLeft.toByteArray())
        val right = QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(-3f, 5f, 2f))
        val scaledRight = QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(-0.375f, 0.625f, 0.25f))
        assertEquals(left.cosineSimilarity(right), scaledLeft.cosineSimilarity(scaledRight))
        val expected = (-3.0 - 10.0 + 8.0) / sqrt(21.0 * 38.0)
        assertEquals(expected, left.cosineSimilarity(right).toDouble(), 0.005)
    }

    @Test
    fun `previous normalized bytes remain readable and cosine scores keep their meaning`() {
        val oldNorthEast = QuantizedCosineVector.fromBytes(byteArrayOf(90, 90, 0))
        val oldNorth = QuantizedCosineVector.fromBytes(byteArrayOf(127, 0, 0))
        assertContentEquals(byteArrayOf(90, 90, 0), oldNorthEast.toByteArray())
        assertEquals(sqrt(0.5).toFloat(), oldNorth.cosineSimilarity(oldNorthEast))
        val newNorthEast = QuantizedCosineVector.fromDenseMaxAbsolute(floatArrayOf(1f, 1f, 0f))
        assertEquals(1f, newNorthEast.cosineSimilarity(oldNorthEast))
        assertEquals(oldNorth.cosineSimilarity(oldNorthEast), oldNorth.cosineSimilarity(newNorthEast))
    }

    @Test
    fun `heterogeneous 344 feature pairs distinguish variations previously erased near neutral`() {
        val source = DoubleArray(344) { ((it % 29) - 14) * 0.0007 }
        val changed = DoubleArray(344) { source[it] + if (it % 3 == 0) 0.008 else -0.006 }
        val left = paired(source)
        val right = paired(changed)
        // The old unit-norm quantizer assigned every one of these heterogeneous pairs [7,0].
        assertContentEquals(legacyUnitQuantize(left), legacyUnitQuantize(right))
        val currentLeft = QuantizedCosineVector.fromDenseMaxAbsolute(left)
        val currentRight = QuantizedCosineVector.fromDenseMaxAbsolute(right)
        assertFalse(currentLeft.toByteArray().contentEquals(currentRight.toByteArray()))
        assertTrue(currentLeft.toByteArray().any { abs(it.toInt()) == 127 })
        assertEquals(1f, currentLeft.cosineSimilarity(currentLeft))
        assertTrue(currentLeft.cosineSimilarity(currentRight) < 0.99999f)
    }

    @Test
    fun `nonfinite zero and invalid dimension vectors fail validation`() {
        for (invalid in listOf(floatArrayOf(), floatArrayOf(0f, -0f), floatArrayOf(Float.NaN),
            floatArrayOf(Float.POSITIVE_INFINITY), floatArrayOf(Float.NEGATIVE_INFINITY), FloatArray(16_385) { 1f })) {
            assertFailsWith<IllegalArgumentException> { QuantizedCosineVector.validateDense(invalid) }
            assertFailsWith<IllegalArgumentException> { QuantizedCosineVector.fromDense(invalid) }
            assertFailsWith<IllegalArgumentException> { QuantizedCosineVector.fromDenseMaxAbsolute(invalid) }
        }
    }

    private fun paired(values: DoubleArray) = FloatArray(values.size * 2) {
        val angle = Math.PI * values[it / 2] / 2.0
        (if (it % 2 == 0) cos(angle) else sin(angle)).toFloat()
    }

    private fun legacyUnitQuantize(vector: FloatArray): ByteArray {
        val norm = sqrt(vector.sumOf { it.toDouble() * it })
        return ByteArray(vector.size) { Math.round(vector[it] / norm * 127).toByte() }
    }

    private fun assertLegacyCosine(left: ByteArray, right: ByteArray) {
        var dot = 0L
        var leftSquared = 0.0
        var rightSquared = 0.0
        for (index in left.indices) {
            dot += left[index].toLong() * right[index].toLong()
            leftSquared += left[index].toDouble() * left[index].toDouble()
            rightSquared += right[index].toDouble() * right[index].toDouble()
        }
        val expected = (dot.toDouble() / (sqrt(leftSquared) * sqrt(rightSquared))).coerceIn(-1.0, 1.0).toFloat()
        val actual = QuantizedCosineVector.fromBytes(left).cosineSimilarity(QuantizedCosineVector.fromBytes(right))
        assertEquals(expected.toBits(), actual.toBits(), "Cosine must preserve score bits for ${left.size} dimensions")
    }
}
