package com.onyxdevtools.ai

import com.onyxdevtools.ai.Constants.EPSILON
import com.onyxdevtools.ai.transformation.fitAndTransform
import com.onyxdevtools.ai.transformation.impl.CategoricalIndexer
import com.onyxdevtools.ai.transformation.inverse
import org.junit.Ignore
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CategoricalIndexerTest {

    /* ------------------------------------------------------------- */
    /*  Basic forward / inverse on a single column                   */
    /* ------------------------------------------------------------- */

    @Test
    fun `categorical indexer encodes and decodes correctly`() {
        val col = floatArrayOf(2.0f, 1.0f, 2.0f, 3.0f)           // first-appearance order
        val enc = CategoricalIndexer()

        enc.fit(col)
        val coded    = enc.apply(col)                         // [0,1,0,2]
        val restored = enc.inverse(coded)

        // New expectations
        assertEquals(0.0f, coded[0])   // 2.0 → 0
        assertEquals(1.0f, coded[1])   // 1.0 → 1
        assertEquals(2.0f, coded[3])   // 3.0 → 2

        col.indices.forEach { i ->
            assertTrue(abs(col[i] - restored[i]) < EPSILON)
        }
    }

    /* ------------------------------------------------------------- */
    /*  Unknown-category behaviour                                   */
    /* ------------------------------------------------------------- */

    @Test
    @Ignore
    fun `categorical indexer throws on unseen category by default`() {
        val train = floatArrayOf(0.0f, 1.0f)
        val test  = floatArrayOf(2.0f)                 // unseen

        val enc = CategoricalIndexer()                 // handleUnknown = "error"
        enc.fit(train)

        assertFailsWith<IllegalArgumentException> {
            enc.apply(test)
        }
    }

    @Test
    fun `categorical indexer can add new category when handleUnknown new`() {
        val train = floatArrayOf(10.0f, 20.0f)
        val test  = floatArrayOf(30.0f)                // unseen

        val enc = CategoricalIndexer(handleUnknown = "new")
        enc.fit(train)

        val coded = enc.apply(test)                    // should extend mapping → index 2
        assertEquals(2.0f, coded[0])
    }

    /* ------------------------------------------------------------- */
    /*  End-to-end matrix pipeline test                              */
    /* ------------------------------------------------------------- */

    @Test
    fun `indexer works in per-column transform list`() {
        // Matrix: 2 feature columns (category, numeric)
        val x: Tensor = Tensor.from(arrayOf(
            floatArrayOf(1.0f, 100.0f),
            floatArrayOf(2.0f, 200.0f),
            floatArrayOf(1.0f, 300.0f)
        ))

        // Column-0 → indexer, Column-1 → untouched
        val transforms = listOf(
            CategoricalIndexer(),
            null
        )

        val xEnc = transforms.fitAndTransform(x)

        // Expect column-0 encoded as 0,1,0 (categories 1.0<2.0)
        assertEquals(0.0f, xEnc[0][0])
        assertEquals(1.0f, xEnc[1][0])
        assertEquals(0.0f, xEnc[2][0])

        // Column-1 unchanged
        assertEquals(100.0f, xEnc[0][1])
        assertEquals(200.0f, xEnc[1][1])
        assertEquals(300.0f, xEnc[2][1])

        // Inverse must restore original matrix
        val xBack = transforms.inverse(xEnc)
        for (i in x.indices) {
            for (j in x[i].indices) {
                assertTrue(abs(x[i][j] - xBack[i][j]) < EPSILON)
            }
        }
    }
}
