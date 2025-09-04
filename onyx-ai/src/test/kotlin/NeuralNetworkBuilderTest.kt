
package dev.onyx.ai

import dev.onyx.ai.transformation.fitAndTransform
import dev.onyx.ai.transformation.impl.*
import dev.onyx.ai.transformation.inverse
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.onyx.ai.layer.impl.RotaryMultiHeadAttentionLayer

class NeuralNetworkBuilderTest {

    /* --------------------------------------------------------------------- */
    /*  Builder DSL                                                          */
    /* --------------------------------------------------------------------- */

    @Test
    fun `builder constructs network with specified layers and transforms`() {
        val model = neuralNetwork {
            learningRate = 0.01f
            lambda       = 0.001f

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

        val input  = Tensor.from(Array(4) { FloatArray(3) { 1.0f } })
        val output = model.predict(input)

        assertEquals(4, output.size)       // rows
        assertEquals(2, output[0].size)    // cols
    }

    @Test
    fun `rotary mha builder adds correct layer`() {
        val model = neuralNetwork {
            layers {
                rotaryMultiHeadAttention(modelSize = 16, headCount = 4)
            }
        }
        assertEquals(1, model.layers.size)
        assertTrue(model.layers[0] is RotaryMultiHeadAttentionLayer)
    }

    /* --------------------------------------------------------------------- */
    /*  Column-wise transform primitives                                     */
    /* --------------------------------------------------------------------- */

    @Test
    fun `log and mean-std transforms apply without error`() {
        val pipeline = listOf(LogTransform(), MeanStdNormalizer())
        val column   = FloatArray(5) { (it + 1).toFloat() }

        pipeline.forEach { it.fit(column) }
        val transformed = pipeline.fold(column) { acc, t -> t.apply(acc) }
        val restored    = pipeline.asReversed().fold(transformed) { acc, t -> t.inverse(acc) }

        column.indices.forEach { i ->
            assertTrue(abs(column[i] - restored[i]) < 1e-6)
        }
    }

    @Test
    fun `mean-std transform normalizes to zero mean and unit variance`() {
        val data = FloatArray(100) { (it + 1).toFloat() }
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
        val original = FloatArray(10) { (it + 1).toFloat() }
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
        val input = floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f)
        val centered = BooleanTransform(centered = true)
        val output   = centered.apply(input)

        assertEquals(-1.0f, output[0])
        assertEquals( 1.0f, output[1])
    }

    @Test
    fun `time decay transform reduces successive rows`() {
        val col   = FloatArray(4) { 10.0f }
        val decay = TimeDecayTransform(lambda = 0.5f)
        val out   = decay.apply(col)

        assertTrue(out[0] > out[1] && out[1] > out[2] && out[2] > out[3])
    }

    /* --------------------------------------------------------------------- */
    /*  ColumnTransformPipeline end-to-end                                   */
    /* --------------------------------------------------------------------- */

    @Test
    fun `column pipeline applies and restores single column while leaving others`() {
        val rows = 5
        val matrix = Tensor.from(Array(rows) { r -> floatArrayOf((r + 1).toFloat(), 100.0f) })

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
            assertEquals(100.0f, xFit[i][1], 1e-12f)
        }
    }
}
