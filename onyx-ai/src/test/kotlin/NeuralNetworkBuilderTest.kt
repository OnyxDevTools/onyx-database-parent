
package com.onyxdevtools.ai

import com.onyxdevtools.ai.transformation.fitAndTransform
import com.onyxdevtools.ai.transformation.impl.*
import com.onyxdevtools.ai.transformation.inverse
import com.onyxdevtools.ai.toFlexibleMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeuralNetworkBuilderTest {

    /* --------------------------------------------------------------------- */
    /*  Builder DSL                                                          */
    /* --------------------------------------------------------------------- */

    @Test
    fun `builder constructs network with specified layers and transforms`() {
        val model = neuralNetwork {
            learningRate = 0.01
            lambda       = 0.001

            layers {
                dense(2, 4, Activation.RELU)
                batchNorm(4)
                dense(4, 1, Activation.LINEAR)
            }

            // two feature columns in this toy example
            features {
                log()        // column-0
                meanStd()    // column-1
            }

            values {
                meanStd()    // single output column
            }
        }

        assertEquals(3, model.layers.size)
        assertEquals(2, model.featureTransforms?.size)
        assertEquals(1, model.valueTransforms?.size)
    }

    @Test
    fun `builder-created model produces expected dimensions`() {
        val model = neuralNetwork {
            layers {
                dense(3, 5, Activation.RELU)
                dense(5, 2, Activation.LINEAR)
            }
        }

        val input  = Array(4) { DoubleArray(3) { 1.0 } }
        val output = model.predict(input.toFlexibleMatrix())

        assertEquals(4, output.rows)       // rows
        assertEquals(2, output.cols)    // cols
    }

    /* --------------------------------------------------------------------- */
    /*  Column-wise transform primitives                                     */
    /* --------------------------------------------------------------------- */

    @Test
    fun `log and mean-std transforms apply without error`() {
        val pipeline = listOf(LogTransform(), MeanStdNormalizer())
        val column   = DoubleArray(5) { (it + 1).toDouble() }

        pipeline.forEach { it.fit(column) }
        val transformed = pipeline.fold(column) { acc, t -> t.apply(acc) }
        val restored    = pipeline.asReversed().fold(transformed) { acc, t -> t.inverse(acc) }

        column.indices.forEach { i ->
            assertTrue(abs(column[i] - restored[i]) < 1e-6)
        }
    }

    @Test
    fun `mean-std transform normalizes to zero mean and unit variance`() {
        val data = DoubleArray(100) { (it + 1).toDouble() }
        val norm = MeanStdNormalizer()
        norm.fit(data)
        val z = norm.apply(data)

        val u = z.average()
        val o2 = z.map { it * it }.average()

        assertTrue(abs(u)  < 1e-6, "mean ≈ 0")
        assertTrue(abs(o2 - 1.0) < 1e-6, "variance ≈ 1")
    }

    @Test
    fun `log transform is reversible`() {
        val original = DoubleArray(10) { (it + 1).toDouble() }
        val logT = LogTransform()
        logT.fit(original)
        val z = logT.apply(original)
        val restored = logT.inverse(z)

        original.indices.forEach { i ->
            assertTrue(abs(original[i] - restored[i]) < 1e-6)
        }
    }

    @Test
    fun `boolean transform centers 0 and 1 correctly`() {
        val input = doubleArrayOf(0.0, 1.0, 0.0, 1.0)
        val centered = BooleanTransform(centered = true)
        val output   = centered.apply(input)

        assertEquals(-1.0, output[0])
        assertEquals( 1.0, output[1])
    }

    @Test
    fun `time decay transform reduces successive rows`() {
        val col   = DoubleArray(4) { 10.0 }
        val decay = TimeDecayTransform(lambda = 0.5)
        val out   = decay.apply(col)

        assertTrue(out[0] > out[1] && out[1] > out[2] && out[2] > out[3])
    }

    /* --------------------------------------------------------------------- */
    /*  ColumnTransformPipeline end-to-end                                   */
    /* --------------------------------------------------------------------- */

    @Test
    fun `column pipeline applies and restores single column while leaving others`() {
        val rows = 5
        val matrix = Array(rows) { r -> doubleArrayOf((r + 1).toDouble(), 100.0) }

        // Build per-column transform list: pipeline for col-0, null for col-1
        val transforms = listOf(
            ColumnTransformPipeline(listOf(LogTransform(), MeanStdNormalizer())),
            null
        )

        // fit + transform + inverse via helper extensions
        val xFit   = transforms.fitAndTransform(matrix)
        val xBack  = transforms.inverse(xFit)

        // Column-0 restored, column-1 untouched
        (0 until rows).forEach { i ->
            assertTrue(abs(matrix[i][0] - xBack[i][0]) < 1e-6)
            assertEquals(100.0, xFit[i][1], 1e-12)
        }
    }
}
