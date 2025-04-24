package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.EPSILON
import com.onyxdevtools.ai.transformation.impl.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.*

class TransformerTests {

    /* ────────────────────────────────────────────────────────────────
     *  Column-wise L2 normaliser
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `columnL2Normalizer scales to unit norm`() {
        val col = doubleArrayOf(3.0, 4.0, 0.0, 1.0)  // ||x|| = √(26) ≈ 5.099
        val norm = ColumnL2Normalizer()
        val z = norm.apply(col)

        val zNorm = sqrt(z.sumOf { it * it })
        assertEquals(1.0, zNorm, EPSILON)

        // Spot-check first two elements
        val factor = sqrt(26.0)
        assertEquals(3.0 / factor, z[0], EPSILON)
        assertEquals(4.0 / factor, z[1], EPSILON)

        // inverse restores
        val restored = norm.inverse(z)
        col.indices.forEach { i -> assertEquals(col[i], restored[i], EPSILON) }
    }

    /* ────────────────────────────────────────────────────────────────
     *  RobustScaler
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `robustScaler centers around median and scales by IQR`() {
        val col = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 100.0)   // median=3, Q1=2, Q3=4, IQR=2
        val s = RobustScaler()
        val z = s.apply(col)

        val expected = doubleArrayOf(-1.0, -0.5, 0.0, 0.5, 48.5)
        expected.indices.forEach { i -> assertEquals(expected[i], z[i], EPSILON) }

        // median of transformed ≈ 0
        val medZ = z.sorted()[z.size / 2]
        assertEquals(0.0, medZ, EPSILON)

        // inverse
        val restored = s.inverse(z)
        col.indices.forEach { i -> assertEquals(col[i], restored[i], EPSILON) }
    }

    /* ────────────────────────────────────────────────────────────────
     *  QuantileTransformer
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `quantileTransformer maps to uniform distribution`() {
        val col = doubleArrayOf(10.0, 30.0, 20.0)  // ranks 0,2,1 → 0,1,0.5
        val qt  = QuantileTransformer()
        val z   = qt.apply(col)

        val expected = doubleArrayOf(0.0, 1.0, 0.5)
        expected.indices.forEach { i -> assertEquals(expected[i], z[i], EPSILON) }

        // round-trip
        val restored = qt.inverse(z)
        col.indices.forEach { i -> assertTrue(abs(col[i] - restored[i]) < 1e-6) }
    }

    /* ────────────────────────────────────────────────────────────────
     *  MinMaxScaler
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `minMaxScaler scales to default 0-1`() {
        val col = doubleArrayOf(1.0, 3.0, 5.0)         // min=1, max=5
        val mm = MinMaxScaler()                        // [0,1]
        val z  = mm.apply(col)

        assertArraysEqual(doubleArrayOf(0.0, 0.5, 1.0), z)
        assertArraysEqual(col, mm.inverse(z))
    }

    @Test
    fun `minMaxScaler scales to custom range -1 to 1`() {
        val col = doubleArrayOf(0.0, 50.0, 100.0)
        val mm  = MinMaxScaler(minRange = -1.0, maxRange = 1.0)
        val z   = mm.apply(col)

        assertArraysEqual(doubleArrayOf(-1.0, 0.0, 1.0), z)
        assertArraysEqual(col, mm.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  MaxAbsScaler
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `maxAbsScaler scales by max abs`() {
        val col = doubleArrayOf(-5.0, 0.0, 2.5)   // maxAbs = 5
        val ma  = MaxAbsScaler()
        val z   = ma.apply(col)

        assertArraysEqual(doubleArrayOf(-1.0, 0.0, 0.5), z)
        assertArraysEqual(col, ma.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  TimeDecayTransform
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `timeDecay reduces values positionally`() {
        val lambda = 0.5
        val col    = DoubleArray(4) { 10.0 }
        val td     = TimeDecayTransform(lambda)

        val z = td.apply(col)
        (0 until 4).forEach { i ->
            assertEquals(10.0 * exp(-lambda * i), z[i], EPSILON)
        }

        // order check
        assertTrue(z[0] > z[1] && z[1] > z[2] && z[2] > z[3])

        // inverse
        assertArraysEqual(col, td.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  Helper
     * ──────────────────────────────────────────────────────────────── */

    private fun assertArraysEqual(expected: DoubleArray, actual: DoubleArray, tol: Double = EPSILON) {
        assertEquals(expected.size, actual.size, "Length mismatch")
        expected.indices.forEach { i ->
            assertEquals(expected[i], actual[i], tol, "Mismatch at index $i")
        }
    }
}
