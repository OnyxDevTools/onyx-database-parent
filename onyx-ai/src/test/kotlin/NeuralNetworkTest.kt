package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.flatten
import com.onyxdevtools.ai.extensions.meanStandardError
import com.onyxdevtools.ai.layer.impl.BatchNormalizationLayer
import com.onyxdevtools.ai.layer.impl.DenseLayer
import com.onyxdevtools.ai.toFlexibleMatrix
import org.junit.Assert.*
import org.junit.Ignore
import java.io.*
import kotlin.random.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for verifying the correctness and behavior of [NeuralNetwork].
 */
class NeuralNetworkTest {

    /**
     * Generates a simple linear dataset: y = 2x + 3 + noise.
     *
     * @param size Number of samples to generate.
     * @return A pair of feature and label matrices.
     */
    private fun generateLinearRegressionData(size: Int): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val features = Array(size) { DoubleArray(1) { Random.nextDouble(-5.0, 5.0) } }
        val labels = Array(size) {
            DoubleArray(1) { 2 * features[it][0] + 3 + Random.nextDouble(-0.05, 0.05) }
        }
        return features to labels
    }

    /**
     * Tests that the predicted output dimensions match the expected shape of the final layer.
     */
    @Test
    fun `predict output dimensions match last layer`() {
        val model = NeuralNetwork(listOf(DenseLayer(3, 4, Activation.RELU), DenseLayer(4, 2, Activation.LINEAR)))
        val input = Array(5) { DoubleArray(3) { Random.nextDouble() } }
        val output = model.predict(input.toFlexibleMatrix())
        assertEquals(5, output.rows)
        assertEquals(2, output.cols)
    }

    /**
     * Tests that cloning a neural network results in a deep copy (mutations on the clone do not affect the original).
     */
    @Test
    fun `clone produces deep copy`() {
        val layer = DenseLayer(2, 2, Activation.LINEAR)
        val original = NeuralNetwork(listOf(layer))
        val cloned = original.clone()
        cloned.layers.filterIsInstance<DenseLayer>().first().weights[0][0] += 1.0
        val originalWeight = original.layers.filterIsInstance<DenseLayer>().first().weights[0][0]
        val mutatedWeight = cloned.layers.filterIsInstance<DenseLayer>().first().weights[0][0]
        assertNotEquals(originalWeight, mutatedWeight, "clone() must deep-copy weight arrays")
    }

    /**
     * Tests that the loss is reduced significantly when training on a linearly generated dataset.
     */
    @Test
    fun `training on linear data reduces loss`() {
        val (features, labels) = generateLinearRegressionData(200)
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.01)

        val initialLoss = model.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        val trained = model.train(
            trainingFeatures = features.toFlexibleMatrix(),
            trainingValues = labels.toFlexibleMatrix(),
            maxEpochs = 300,
            patience = 20
        ) { net -> net.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix()) }

        val finalLoss = trained.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        assertTrue(finalLoss < initialLoss * 0.1, "loss should be reduced by at least 90%")
    }

    /**
     * Tests that sample weights affect model training and bias predictions toward more heavily weighted examples.
     */
    @Test
    @Ignore
    fun `sample weights influence training`() {
        val features = Array(100) { DoubleArray(1) { it.toDouble() } }
        val labels = Array(100) { DoubleArray(1) { if (it < 50) 1.0 else 10.0 } }
        val weights = DoubleArray(100) { if (it < 50) 1.0 else 0.1 }

        val weightedModel = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.05)
        val unweightedModel = weightedModel.clone()

        weightedModel.train(features.toFlexibleMatrix(), labels.toFlexibleMatrix(), trainingWeights = weights, maxEpochs = 200, patience = 15) {
            it.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        }
        unweightedModel.train(features.toFlexibleMatrix(), labels.toFlexibleMatrix(), maxEpochs = 200, patience = 15) {
            it.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        }

        val lossWithWeights = weightedModel.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        val lossWithoutWeights = unweightedModel.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        assertTrue(lossWithWeights < lossWithoutWeights)
    }

    /**
     * Tests that early stopping halts training before the maximum number of epochs.
     */
    @Test
    fun `early stopping stops before max epochs`() {
        val (features, labels) = generateLinearRegressionData(50)
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.02)

        var epochsTrained = 0
        model.train(features.toFlexibleMatrix(), labels.toFlexibleMatrix(), maxEpochs = 1000, patience = 5) {
            epochsTrained++
            it.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        }
        assertTrue(epochsTrained < 1000, "training should stop early due to patience")
    }

    /**
     * Tests that dropout scaling preserves the mean output across training and evaluation phases.
     */
    @Test
    fun `dropout scaling keeps expectation`() {
        val dropoutLayer = DenseLayer(10, 10, Activation.LINEAR, dropoutRate = 0.5)
        val model = NeuralNetwork(listOf(dropoutLayer))
        val input = Array(2000) { DoubleArray(10) { 1.0 } }

        val trainOutput = model.predict(input.toFlexibleMatrix(), isTraining = true)
        val evalOutput = model.predict(input.toFlexibleMatrix(), isTraining = false)

        val trainMean = trainOutput.flatten().average()
        val evalMean = evalOutput.flatten().average()
        assertTrue(abs(trainMean - evalMean) < 0.05, "dropout scaling should preserve mean activity")
    }

    /**
     * Tests that a neural network is serializable and can be deserialized correctly.
     */
    @Test
    fun `network is serialisable`() {
        val model = NeuralNetwork(listOf(DenseLayer(2, 1, Activation.LINEAR)))
        val outputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).use { it.writeObject(model) }
        val serializedData = outputStream.toByteArray()
        val deserialized = ObjectInputStream(ByteArrayInputStream(serializedData)).readObject() as NeuralNetwork
        assertNotNull(deserialized.predict(arrayOf(doubleArrayOf(0.0, 0.0)).toFlexibleMatrix()))
    }

    /**
     * Tests that providing mismatched sample weights throws an exception during training.
     */
    @Test
    fun `mismatched weight length throws`() {
        val features = Array(2) { DoubleArray(1) { 0.0 } }
        val labels = Array(2) { DoubleArray(1) { 0.0 } }
        val weights = DoubleArray(1) { 1.0 } // Incorrect length
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)))
        assertThrows(IllegalArgumentException::class.java) {
            model.train(features.toFlexibleMatrix(), labels.toFlexibleMatrix(), trainingWeights = weights, maxEpochs = 1) { 0.0 }
        }
    }

    /**
     * Tests that a network using [BatchNormalizationLayer] is able to train and significantly reduce loss.
     */
    @Test
    fun `neural network with batch normalization trains correctly`() {
        val (features, labels) = generateLinearRegressionData(300)
        val model = NeuralNetwork(
            layers = listOf(
                DenseLayer(1, 16, Activation.RELU),
                BatchNormalizationLayer(16),
                DenseLayer(16, 1, Activation.LINEAR)
            ),
            learningRate = 0.01
        )

        val initialLoss = model.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        val trained = model.train(
            trainingFeatures = features.toFlexibleMatrix(),
            trainingValues = labels.toFlexibleMatrix(),
            maxEpochs = 500,
            patience = 20
        ) { it.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix()) }
        val finalLoss = trained.predict(features.toFlexibleMatrix()).meanStandardError(labels.toFlexibleMatrix())
        assertTrue(finalLoss < initialLoss * 0.1, "BatchNorm-enhanced model should reduce loss by at least 90%")
    }
}
