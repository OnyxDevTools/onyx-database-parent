package com.onyxdevtools.ai

import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.transformation.impl.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.*

@Ignore
class TransformerTests {

    /* ────────────────────────────────────────────────────────────────
     *  Column-wise L2 normaliser
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `columnL2Normalizer scales to unit norm`() {
        val col = floatArrayOf(3.0f, 4.0f, 0.0f, 1.0f)  // ||x|| = √(26) ≈ 5.099
        val norm = ColumnL2Normalizer()
        val z = norm.apply(col)

        val zNorm = sqrt(z.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1.0f, zNorm, EPSILON)

        // Spot-check first two elements
        val factor = sqrt(26.0).toFloat()
        assertEquals(3.0f / factor, z[0], EPSILON)
        assertEquals(4.0f / factor, z[1], EPSILON)

        // inverse restores
        val restored = norm.inverse(z)
        col.indices.forEach { i -> assertEquals(col[i], restored[i], EPSILON) }
    }

    /* ────────────────────────────────────────────────────────────────
     *  RobustScaler
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `robustScaler centers around median and scales by IQR`() {
        val col = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 100.0f)   // median=3, Q1=2, Q3=4, IQR=2
        val s = RobustScaler()
        val z = s.apply(col)

        val expected = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 48.5f)
        expected.indices.forEach { i -> assertEquals(expected[i], z[i], EPSILON) }

        // median of transformed ≈ 0
        val medZ = z.sorted()[z.size / 2]
        assertEquals(0.0f, medZ, EPSILON)

        // inverse
        val restored = s.inverse(z)
        col.indices.forEach { i -> assertEquals(col[i], restored[i], EPSILON) }
    }

    /* ────────────────────────────────────────────────────────────────
     *  QuantileTransformer
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `quantileTransformer maps to uniform distribution`() {
        val col = floatArrayOf(10.0f, 30.0f, 20.0f)  // ranks 0,2,1 → 0,1,0.5
        val qt  = QuantileTransformer()
        val z   = qt.apply(col)

        val expected = floatArrayOf(0.0f, 1.0f, 0.5f)
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
        val col = floatArrayOf(1.0f, 3.0f, 5.0f)         // min=1, max=5
        val mm = MinMaxScaler()                        // [0,1]
        val z  = mm.apply(col)

        assertArraysEqual(floatArrayOf(0.0f, 0.5f, 1.0f), z)
        assertArraysEqual(col, mm.inverse(z))
    }

    @Test
    fun `minMaxScaler scales to custom range -1 to 1`() {
        val col = floatArrayOf(0.0f, 50.0f, 100.0f)
        val mm  = MinMaxScaler(minRange = -1.0f, maxRange = 1.0f)
        val z   = mm.apply(col)

        assertArraysEqual(floatArrayOf(-1.0f, 0.0f, 1.0f), z)
        assertArraysEqual(col, mm.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  MaxAbsScaler
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `maxAbsScaler scales by max abs`() {
        val col = floatArrayOf(-5.0f, 0.0f, 2.5f)   // maxAbs = 5
        val ma  = MaxAbsScaler()
        val z   = ma.apply(col)

        assertArraysEqual(floatArrayOf(-1.0f, 0.0f, 0.5f), z)
        assertArraysEqual(col, ma.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  TimeDecayTransform
     * ──────────────────────────────────────────────────────────────── */

    @Test
    fun `timeDecay reduces values positionally`() {
        val lambda = 0.5f
        val col    = FloatArray(4) { 10.0f }
        val td     = TimeDecayTransform(lambda)

        val z = td.apply(col)
        (0 until 4).forEach { i ->
            val expected = 10.0f * exp(-lambda * i)
            assertEquals(expected, z[i], EPSILON)
        }

        // order check
        assertTrue(z[0] > z[1] && z[1] > z[2] && z[2] > z[3])

        // inverse
        assertArraysEqual(col, td.inverse(z))
    }

    /* ────────────────────────────────────────────────────────────────
     *  Helper
     * ──────────────────────────────────────────────────────────────── */

    private fun assertArraysEqual(expected: FloatArray, actual: FloatArray, tol: Float = EPSILON) {
        assertEquals(expected.size, actual.size, "Length mismatch")
        expected.indices.forEach { i ->
            assertEquals(expected[i], actual[i], tol, "Mismatch at index $i")
        }
    }
}
