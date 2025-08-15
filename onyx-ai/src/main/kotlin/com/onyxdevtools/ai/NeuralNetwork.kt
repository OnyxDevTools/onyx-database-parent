@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.transformation.*
import com.onyxdevtools.ai.batch.SequentialBatchSplitter
import com.onyxdevtools.ai.batch.TokenBatchSplitter
import java.io.*
import kotlin.math.min

// Avoid the Kotlin scope-function name clash with our ColumnTransforms extensions:
import com.onyxdevtools.ai.transformation.apply as applyTransforms
import com.onyxdevtools.ai.transformation.inverse as inverseTransforms
import com.onyxdevtools.ai.transformation.fitAndTransform as fitAndTransformTransforms

/**
 * Represents a multi-layer neural network using backpropagation and the Adam optimizer.
 */
@Suppress("MemberVisibilityCanBePrivate")
data class NeuralNetwork(
    val layers: List<Layer>,
    val featureTransforms: ColumnTransforms? = null,
    val valueTransforms: ColumnTransforms? = null,
    var learningRate: Float = 1e-3f,
    var lambda: Float = 1e-4f,
    var beta1: Float = 0.9f,
    var beta2: Float = 0.999f,
) : Serializable {

    private var beta1Power = 1.0f
    private var beta2Power = 1.0f
    private var lastInput: Tensor? = null

    fun withTransforms(
        feature: ColumnTransforms = featureTransforms ?: emptyList(),
        label: ColumnTransforms = valueTransforms ?: emptyList(),
    ): NeuralNetwork = copy(featureTransforms = feature, valueTransforms = label)

    fun predict(
        input: Tensor,
        isTraining: Boolean = false,
        returnOriginalScale: Boolean = false,
        skipFeatureTransform: Boolean = false
    ): Tensor {
        val x = if (skipFeatureTransform) input
        else featureTransforms?.applyTransforms(input) ?: input
        lastInput = x

        var out = x
        layers.forEachIndexed { idx, layer ->
            out = layer.forward(out, isTraining, layers.getOrNull(idx + 1))
        }

        return if (returnOriginalScale && valueTransforms?.any { it != null } == true)
            valueTransforms!!.inverseTransforms(out)
        else
            out
    }

    fun predict(input: Tensor): Tensor =
        predict(input = input, isTraining = false, returnOriginalScale = true)

    private fun backward(
        predicted: Tensor,
        actual: Tensor,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = actual.rows.toFloat()
        val weights = sampleWeights ?: FloatArray(actual.rows) { 1.0f }

        val probs = predicted.softmax()

        var delta: Tensor = probs.subtract(actual).mapIndexed { r, row ->
            FloatArray(row.size) { c -> row[c] * weights[r] }
        }

        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            val nextLayer = layers.getOrNull(i + 1)
            val previousLayer = layers.getOrNull(i - 1)

            val inputToLayer = previousLayer?.output ?: lastInput
            ?: error("No input recorded for layer $i")

            delta = layer.backward(
                currentInput = inputToLayer,
                delta = delta,
                featureSize = sampleCount,
                nextLayer = nextLayer,
                previousLayer = previousLayer,
                lambda = lambda
            )
        }
    }

    private fun backwardSparse(
        predicted: Tensor,
        sparseTargets: IntArray,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = predicted.rows.toFloat()
        var delta: Tensor =
            sparseCategoricalCrossEntropyGradients(predicted, sparseTargets, sampleWeights)

        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            val nextLayer = layers.getOrNull(i + 1)
            val previousLayer = layers.getOrNull(i - 1)

            val inputToLayer = previousLayer?.output ?: lastInput
            ?: error("No input recorded for layer $i")

            delta = layer.backward(
                currentInput = inputToLayer,
                delta = delta,
                featureSize = sampleCount,
                nextLayer = nextLayer,
                previousLayer = previousLayer,
                lambda = lambda
            )
        }
    }

    private fun updateParameters() {
        beta1Power *= beta1
        beta2Power *= beta2
        layers.forEach { it.updateParameters(beta1Power, beta2Power, beta1, beta2, learningRate) }
    }

    fun train(
        trainingFeatures: Tensor,
        trainingValues: Tensor,
        trainingWeights: FloatArray? = null,
        batchSize: Int = 32,
        maxEpochs: Int = 100,
        patience: Int = 10,
        shuffle: Boolean = true,
        tokensPerSample: Int = 1,
        lossFn: (NeuralNetwork) -> Float = { n ->
            n.predict(trainingFeatures).meanStandardError(trainingValues)
        },
    ): NeuralNetwork {

        require(trainingWeights == null || trainingWeights.size == trainingFeatures.rows) {
            "Sample weights size must match number of training samples"
        }

        val x = featureTransforms?.fitAndTransformTransforms(trainingFeatures) ?: trainingFeatures
        val y = valueTransforms?.fitAndTransformTransforms(trainingValues) ?: trainingValues

        var bestLoss = Float.POSITIVE_INFINITY
        var bestModel: NeuralNetwork = this.clone()
        var epochsWithoutImprovement = 0

        val indices = (0 until x.rows).toMutableList()

        for (epoch in 1..maxEpochs) {
            if (shuffle) indices.shuffle()

            for (batchStart in indices.indices step batchSize) {
                val batchEnd = min(batchStart + batchSize, indices.size)
                val batchIndices = indices.subList(batchStart, batchEnd)

                val batchFeatures: Tensor = x.subset(batchIndices)
                val batchTargetIndices = batchIndices.flatMap { seqIdx ->
                    (seqIdx * tokensPerSample until (seqIdx + 1) * tokensPerSample)
                }
                val batchLabels: Tensor = y.subset(batchTargetIndices)

                val batchWeights = trainingWeights?.let { w ->
                    batchIndices.map { w[it] }.toFloatArray()
                }

                val batchPredictions =
                    predict(batchFeatures, isTraining = true, skipFeatureTransform = true)
                backward(batchPredictions, batchLabels, batchWeights)
                updateParameters()
            }

            val loss = lossFn(this)
            if (loss < bestLoss) {
                bestLoss = loss
                bestModel = this.clone()
                epochsWithoutImprovement = 0
            } else if (++epochsWithoutImprovement > patience) {
                break
            }
        }
        return bestModel
    }

    fun trainStreaming(
        source: () -> Sequence<Pair<FloatArray, Array<FloatArray>>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Tensor, actual: Tensor) -> Float = { p, a -> p.meanStandardError(a) },
        comprehensiveLossFn: ((NeuralNetwork) -> Float)? = null,
        saveModelPath: String? = null,
    ): NeuralNetwork {

        var bestLoss = Float.POSITIVE_INFINITY
        var best = this.clone()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<FloatArray>()
            val by = mutableListOf<Array<FloatArray>>()
            var runningTrainLoss = 0.0f
            var runningTestLoss = 0.0f
            var testSamples = 0

            for ((inputSeq, targetSeqs) in source()) {
                bx += inputSeq; by += targetSeqs
                if (bx.size == batchSize) {
                    val seqSplitter = SequentialBatchSplitter()

                    // Convert target sequences to Array<Tensor> before splitting
                    val yAsTensors: Array<Tensor> = by.map { it.toTensor() }.toTypedArray()

                    val (xTrainRaw, yTrainRawArr, xTestRaw, yTestRawArr) =
                        seqSplitter.splitBatch(
                            bx.toTypedArray().toTensor(),
                            yAsTensors,
                            testFraction = testFrac,
                            shuffle = shuffle
                        )

                    val xTrain = featureTransforms?.fitAndTransformTransforms(xTrainRaw) ?: xTrainRaw
                    val yTrainFlat = flattenSequencesToTensor(yTrainRawArr)
                    val yTrain = valueTransforms?.fitAndTransformTransforms(yTrainFlat) ?: yTrainFlat

                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backward(predTrain, yTrain)
                    updateParameters()

                    val xTest = featureTransforms?.applyTransforms(xTestRaw) ?: xTestRaw
                    val yTestFlat = flattenSequencesToTensor(yTestRawArr)
                    val yTest = valueTransforms?.applyTransforms(yTestFlat) ?: yTestFlat
                    val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)

                    runningTrainLoss += lossFn(predTrain, yTrain) * xTrain.rows
                    runningTestLoss += lossFn(predTest, yTest) * xTest.rows
                    testSamples += xTest.rows

                    bx.clear(); by.clear()
                }
            }

            if (bx.isNotEmpty()) {
                val seqSplitter = SequentialBatchSplitter()

                val yAsTensors: Array<Tensor> = by.map { it.toTensor() }.toTypedArray()

                val (xT, yTArr, xv, yvArr) =
                    seqSplitter.splitBatch(
                        bx.toTypedArray().toTensor(),
                        yAsTensors,
                        testFraction = testFrac,
                        shuffle = shuffle
                    )

                val xTf = featureTransforms?.fitAndTransformTransforms(xT) ?: xT
                val yTfFlat = flattenSequencesToTensor(yTArr)
                val yTf = valueTransforms?.fitAndTransformTransforms(yTfFlat) ?: yTfFlat
                val predT = predict(xTf, true, skipFeatureTransform = true)
                backward(predT, yTf); updateParameters()

                val xv2 = featureTransforms?.applyTransforms(xv) ?: xv
                val yvFlat = flattenSequencesToTensor(yvArr)
                val yv2 = valueTransforms?.applyTransforms(yvFlat) ?: yvFlat
                val predV = predict(xv2, false, skipFeatureTransform = true)

                runningTrainLoss += lossFn(predT, yTf) * xTf.rows
                runningTestLoss += lossFn(predV, yv2) * xv2.rows
                testSamples += xv2.rows
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace) println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.clone()
                epochsWithoutImprovement = 0
                saveModelPath?.let { path ->
                    try {
                        best.saveToFile(path)
                        if (trace) println("Model saved to $path (loss: $epochTestLoss)")
                    } catch (e: Exception) {
                        if (trace) println("Warning: Failed to save model to $path: ${e.message}")
                    }
                }
            } else if (++epochsWithoutImprovement >= patience) {
                if (trace) println("early stop at epoch $epoch")
                return best
            }
        }
        return best
    }

    fun trainStreamingSparse(
        source: () -> Sequence<Pair<FloatArray, IntArray>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Tensor, sparseTargets: IntArray) -> Float =
            { p, s -> sparseCategoricalCrossEntropy(p, s) },
        probeFn: () -> Unit = { },
        comprehensiveLossFn: ((NeuralNetwork) -> Float)? = null,
        saveModelPath: String? = null,
    ): NeuralNetwork {

        var bestLoss = Float.POSITIVE_INFINITY
        var best = this.clone()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<FloatArray>()
            val by = mutableListOf<IntArray>()
            var runningTrainLoss = 0.0f
            var runningTestLoss = 0.0f
            var testSamples = 0

            var iter = 0
            for ((inputSeq, targetSeq) in source()) {
                bx += inputSeq; by += targetSeq
                if (bx.size == batchSize) {
                    val tokenSplitter = TokenBatchSplitter()

                    val (xTrainRaw, yTrainRaw, xTestRaw, yTestRaw) =
                        tokenSplitter.splitBatch(
                            bx.toTypedArray().toTensor(),
                            by.toTypedArray(),
                            testFraction = testFrac,
                            shuffle = shuffle
                        )

                    val xTrain = featureTransforms?.fitAndTransformTransforms(xTrainRaw) ?: xTrainRaw
                    val yTrainFlat = yTrainRaw.flatMap { arr -> arr.asList() }.toIntArray()

                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backwardSparse(predTrain, yTrainFlat)
                    updateParameters()

                    val xTest = featureTransforms?.applyTransforms(xTestRaw) ?: xTestRaw
                    val yTestFlat = yTestRaw.flatMap { arr -> arr.asList() }.toIntArray()

                    runningTrainLoss += lossFn(predTrain, yTrainFlat) * xTrain.rows

                    if (xTest.rows > 0) {
                        val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)
                        runningTestLoss += lossFn(predTest, yTestFlat) * xTest.rows
                        testSamples += xTest.rows
                    }

                    bx.clear(); by.clear()
                }
                if (iter.rem(100) == 0) probeFn.invoke()
                iter++
            }

            if (bx.isNotEmpty()) {
                val tokenSplitter = TokenBatchSplitter()

                val (xT, yT, xv, yv) =
                    tokenSplitter.splitBatch(
                        bx.toTypedArray().toTensor(),
                        by.toTypedArray(),
                        testFraction = testFrac,
                        shuffle = shuffle
                    )

                val xTf = featureTransforms?.fitAndTransformTransforms(xT) ?: xT
                val yTfFlat = yT.flatMap { arr -> arr.asList() }.toIntArray()
                val predT = predict(xTf, true, skipFeatureTransform = true)
                backwardSparse(predT, yTfFlat); updateParameters()

                val xv2 = featureTransforms?.applyTransforms(xv) ?: xv
                val yvFlat = yv.flatMap { arr -> arr.asList() }.toIntArray()
                val predV = predict(xv2, false, skipFeatureTransform = true)

                runningTrainLoss += lossFn(predT, yTfFlat) * xTf.rows
                runningTestLoss += lossFn(predV, yvFlat) * xv2.rows
                testSamples += xv2.rows
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace) println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.clone()
                epochsWithoutImprovement = 0
                saveModelPath?.let { path ->
                    try {
                        best.saveToFile(path)
                        if (trace) println("Model saved to $path (loss: $epochTestLoss)")
                    } catch (e: Exception) {
                        if (trace) println("Warning: Failed to save model to $path: ${e.message}")
                    }
                }
            } else if (++epochsWithoutImprovement >= patience) {
                if (trace) println("early stop at epoch $epoch")
                return best
            }
        }
        return best
    }

    private fun trainOnBatch(xRaw: Tensor, yRaw: Tensor) {
        val x = featureTransforms?.fitAndTransformTransforms(xRaw) ?: xRaw
        val y = valueTransforms?.fitAndTransformTransforms(yRaw) ?: yRaw
        val pred = predict(x, isTraining = true, skipFeatureTransform = true)
        backward(pred, y)
        updateParameters()
    }

    fun clone(): NeuralNetwork =
        NeuralNetwork(
            layers = layers.map { it.clone() },
            learningRate = learningRate,
            lambda = lambda,
            beta1 = beta1,
            beta2 = beta2,
            featureTransforms = featureTransforms?.map { it?.clone() },
            valueTransforms = valueTransforms?.map { it?.clone() },
        ).apply {
            this.beta1Power = this@NeuralNetwork.beta1Power
            this.beta2Power = this@NeuralNetwork.beta2Power
            this.lastInput = this@NeuralNetwork.lastInput?.copy()
        }

    fun saveToFile(filePath: String) {
        try {
            ObjectOutputStream(FileOutputStream(filePath)).use { oos ->
                oos.writeObject(this)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to save model to file: $filePath", e)
        }
    }

    companion object {
        private const val serialVersionUID = 1L

        fun loadFromFile(filePath: String): NeuralNetwork {
            return try {
                ObjectInputStream(FileInputStream(filePath)).use { ois ->
                    ois.readObject() as NeuralNetwork
                }
            } catch (e: IOException) {
                throw RuntimeException("Failed to load model from file: $filePath", e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException("Failed to deserialize model from file: $filePath", e)
            }
        }

        fun loadOrCreate(path: String, creator: () -> NeuralNetwork): NeuralNetwork {
            val f = File(path)
            return if (f.isFile && f.canRead()) {
                try {
                    loadFromFile(path).also {
                        println("✅ Loaded model from $path")
                    }
                } catch (e: Exception) {
                    println("⚠️  Failed to load model from $path (${e.message}) – creating a new one.")
                    creator()
                }
            } else {
                println("📂 No existing model at $path – creating a new one.")
                creator()
            }
        }
    }
}

