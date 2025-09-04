package dev.onyx.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import Activation

/**
 * Verifies that a NeuralNetwork built via the DSL can be serialized and deserialized
 * without losing the ability to make predictions.
 */
class NeuralNetworkSerializationTest {
    @Test
    fun `neural network is serializable via builder config`() {
        // Build a simple network with the DSL
        val model = neuralNetwork {
            layers {
                dense(2, 3, Activation.RELU)
                dense(3, 1, Activation.LINEAR)
            }
        }

        // Prepare dummy input
        val input = Tensor.from(Array(5) { FloatArray(2) { it.toFloat() } })
        val out1 = model.predict(input)

        // Serialize to bytes
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(model) }
        val bytes = baos.toByteArray()

        // Deserialize back
        val model2 = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as NeuralNetwork }
        val out2 = model2.predict(input)

        // Outputs must retain the same shape after deserialization
        assertEquals(out1.size, out2.size, "Row count should match after deserialization")
        assertEquals(out1[0].size, out2[0].size, "Column count should match after deserialization")
    }
}
