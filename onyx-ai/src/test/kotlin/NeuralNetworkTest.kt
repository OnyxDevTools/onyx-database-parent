package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.flatten
import com.onyxdevtools.ai.extensions.meanStandardError
import com.onyxdevtools.ai.layer.impl.BatchNormalizationLayer
import com.onyxdevtools.ai.layer.impl.DenseLayer
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
@Ignore
class NeuralNetworkTest {

    /**
     * Generates a simple linear dataset: y = 2x + 3 + noise.
     *
     * @param size Number of samples to generate.
     * @return A pair of feature and label matrices.
     */
    private fun generateLinearRegressionData(size: Int): Pair<Matrix, Matrix> {
        val features = Array(size) { FloatArray(1) { Random.nextFloat() * 10.0f - 5.0f } }
        val labels = Array(size) {
            FloatArray(1) { 2 * features[it][0] + 3 + (Random.nextFloat() * 0.1f - 0.05f) }
        }
        return features to labels
    }

    /**
     * Tests that the predicted output dimensions match the expected shape of the final layer.
     */
    @Test
    fun `predict output dimensions match last layer`() {
        val model = NeuralNetwork(listOf(DenseLayer(3, 4, Activation.RELU), DenseLayer(4, 2, Activation.LINEAR)))
        val input = Array(5) { FloatArray(3) { Random.nextFloat() } }
        val output = model.predict(input)
        assertEquals(5, output.size)
        assertEquals(2, output[0].size)
    }

    /**
     * Tests that cloning a neural network results in a deep copy (mutations on the clone do not affect the original).
     */
    @Test
    fun `clone produces deep copy`() {
        val layer = DenseLayer(2, 2, Activation.LINEAR)
        val original = NeuralNetwork(listOf(layer))
        val cloned = original.clone()
        cloned.layers.filterIsInstance<DenseLayer>().first().weights[0][0] += 1.0f
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
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.01f)

        val initialLoss = model.predict(features).meanStandardError(labels)
        val trained = model.train(
            trainingFeatures = features,
            trainingValues = labels,
            maxEpochs = 300,
            patience = 20
        ) { net -> net.predict(features).meanStandardError(labels) }

        val finalLoss = trained.predict(features).meanStandardError(labels)
        assertTrue(finalLoss < initialLoss * 0.1, "loss should be reduced by at least 90%")
    }

    /**
     * Tests that sample weights affect model training and bias predictions toward more heavily weighted examples.
     */
    @Test
    @Ignore
    fun `sample weights influence training`() {
        val features = Array(100) { FloatArray(1) { it.toFloat() } }
        val labels = Array(100) { FloatArray(1) { if (it < 50) 1.0f else 10.0f } }
        val weights = FloatArray(100) { if (it < 50) 1.0f else 0.1f }

        val weightedModel = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.05f)
        val unweightedModel = weightedModel.clone()

        weightedModel.train(features, labels, trainingWeights = weights, maxEpochs = 200, patience = 15) {
            it.predict(features).meanStandardError(labels)
        }
        unweightedModel.train(features, labels, maxEpochs = 200, patience = 15) {
            it.predict(features).meanStandardError(labels)
        }

        val lossWithWeights = weightedModel.predict(features).meanStandardError(labels)
        val lossWithoutWeights = unweightedModel.predict(features).meanStandardError(labels)
        assertTrue(lossWithWeights < lossWithoutWeights)
    }

    /**
     * Tests that early stopping halts training before the maximum number of epochs.
     */
    @Test
    fun `early stopping stops before max epochs`() {
        val (features, labels) = generateLinearRegressionData(50)
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)), learningRate = 0.02f)

        var epochsTrained = 0
        model.train(features, labels, maxEpochs = 1000, patience = 5) {
            epochsTrained++
            it.predict(features).meanStandardError(labels)
        }
        assertTrue(epochsTrained < 1000, "training should stop early due to patience")
    }

    /**
     * Tests that dropout scaling preserves the mean output across training and evaluation phases.
     */
    @Test
    fun `dropout scaling keeps expectation`() {
        val dropoutLayer = DenseLayer(10, 10, Activation.LINEAR, dropoutRate = 0.5f)
        val model = NeuralNetwork(listOf(dropoutLayer))
        val input = Array(2000) { FloatArray(10) { 1.0f } }

        val trainOutput = model.predict(input, isTraining = true)
        val evalOutput = model.predict(input, isTraining = false)

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
        assertNotNull(deserialized.predict(arrayOf(floatArrayOf(0.0f, 0.0f))))
    }

    /**
     * Tests that providing mismatched sample weights throws an exception during training.
     */
    @Test
    fun `mismatched weight length throws`() {
        val features = Array(2) { FloatArray(1) { 0.0f } }
        val labels = Array(2) { FloatArray(1) { 0.0f } }
        val weights = FloatArray(1) { 1.0f } // Incorrect length
        val model = NeuralNetwork(listOf(DenseLayer(1, 1, Activation.LINEAR)))
        assertThrows(IllegalArgumentException::class.java) {
            model.train(features, labels, trainingWeights = weights, maxEpochs = 1) { 0.0f }
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
            learningRate = 0.01f
        )

        val initialLoss = model.predict(features).meanStandardError(labels)
        val trained = model.train(
            trainingFeatures = features,
            trainingValues = labels,
            maxEpochs = 500,
            patience = 20
        ) { it.predict(features).meanStandardError(labels) }
        val finalLoss = trained.predict(features).meanStandardError(labels)
        assertTrue(finalLoss < initialLoss * 0.1f, "BatchNorm-enhanced model should reduce loss by at least 90%")
    }
}
