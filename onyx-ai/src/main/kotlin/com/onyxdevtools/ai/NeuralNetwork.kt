@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.transformation.*
import com.onyxdevtools.ai.batch.SequentialBatchSplitter
import com.onyxdevtools.ai.batch.TokenBatchSplitter
import java.io.*
import kotlin.apply
import kotlin.math.min

typealias Matrix = Array<FloatArray>

/**
 * Represents a multi-layer neural network using backpropagation and the Adam optimizer.
 *
 * @property layers List of layers composing the network.
 * @param featureTransforms Feature transforms used to normalize and transform feature data before training
 * @param valueTransforms Value transformations used to normalize and transform the outputs
 * @property learningRate Learning rate for parameter updates.
 * @property lambda Regularization parameter.
 * @property beta1 Exponential decay rate for the first moment estimates (Adam).
 * @property beta2 Exponential decay rate for the second moment estimates (Adam).
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
    private var lastInput: Matrix? = null

    fun withTransforms(
        feature: ColumnTransforms = featureTransforms ?: emptyList(),
        label: ColumnTransforms = valueTransforms ?: emptyList(),
    ): NeuralNetwork = copy(featureTransforms = feature, valueTransforms = label)

    /**
     * Feeds an input matrix forward through the network.
     *
     * @param input Input matrix.
     * @param isTraining Whether the network is in training mode (affects layers like dropout).
     * @param returnOriginalScale Whether to run the transforms before returning
     * @param skipFeatureTransform Skip while training since the data has already been transformed
     * @return Output matrix after processing through all layers.
     */
    fun predict(
        input: Matrix,
        isTraining: Boolean = false,
        returnOriginalScale: Boolean = false,
        skipFeatureTransform: Boolean = false
    ): Matrix {
        val x = if (skipFeatureTransform)
            input
        else
            featureTransforms?.apply(input) ?: input
        lastInput = x

        var out = x
        layers.forEachIndexed { idx, layer ->
            out = layer.forward(out, isTraining, layers.getOrNull(idx + 1))
        }

        return if (returnOriginalScale && valueTransforms?.any { it != null } == true)
            valueTransforms.inverse(out)
        else
            out
    }

    /**
     * Feeds an input matrix forward through the network.
     *
     * @param input Input matrix.
     * @return Output matrix after processing through all layers.
     */
    fun predict(
        input: Matrix
    ): Matrix = predict(input = input, isTraining = false, returnOriginalScale = true)

    /**
     * Performs the backward pass to compute gradients using dense targets.
     *
     * @param predicted The predicted output.
     * @param actual The true labels (dense one-hot encoded).
     * @param sampleWeights Optional sample weights.
     */
    private fun backward(
        predicted: Matrix,
        actual: Matrix,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = actual.size.toFloat()
        val weights = sampleWeights ?: FloatArray(actual.size) { 1.0f }

        // Compute softmax probabilities
        val probs = softmax(predicted)

        // Delta for CE loss: probs - actual
        var delta: Matrix = subtract(probs, actual).mapIndexed { r, row ->
            FloatArray(row.size) { c -> row[c] * weights[r] }
        }.toTypedArray()

        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            val nextLayer = layers.getOrNull(i + 1)      // forward-order "next"
            val previousLayer = layers.getOrNull(i - 1)      // forward-order "prev"

            val inputToLayer = previousLayer?.output ?: lastInput
            ?: error("No input recorded for layer $i")

            delta = layer.backward(
                currentInput = inputToLayer,                 // <-- change
                delta = delta,
                featureSize = sampleCount,
                nextLayer = nextLayer,
                previousLayer = previousLayer,
                lambda = lambda
            )
        }
    }

    /**
     * Performs the backward pass to compute gradients using sparse targets.
     *
     * @param predicted The predicted output logits.
     * @param sparseTargets The true labels as token IDs (sparse representation).
     * @param sampleWeights Optional sample weights.
     */
    private fun backwardSparse(
        predicted: Matrix,
        sparseTargets: IntArray,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = predicted.size.toFloat()
        
        var delta: Matrix = sparseCategoricalCrossEntropyGradients(predicted, sparseTargets, sampleWeights)

        val startIndex = layers.lastIndex

        for (i in startIndex downTo 0) {
            val layer = layers[i]
            val nextLayer = layers.getOrNull(i + 1)      // forward-order "next"
            val previousLayer = layers.getOrNull(i - 1)      // forward-order "prev"

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

    /**
     * Updates the network parameters using the Adam optimization algorithm.
     */
    private fun updateParameters() {
        beta1Power *= beta1
        beta2Power *= beta2

        layers.forEach { layer ->
            layer.updateParameters(beta1Power, beta2Power, beta1, beta2, learningRate)
        }
    }

    /**
     * Trains the neural network using mini-batch gradient descent with early stopping.
     *
     * @param trainingFeatures The input feature matrix.
     * @param trainingValues The expected output matrix.
     * @param trainingWeights Optional sample weights.
     * @param batchSize Size of each mini-batch.
     * @param maxEpochs Maximum number of training epochs.
     * @param patience Number of epochs without improvement before early stopping.
     * @param shuffle Whether to shuffle data each epoch.
     * @param lossFn Custom loss evaluation function.
     * @return The best model observed during training.
     */
    fun train(
        trainingFeatures: Matrix,
        trainingValues: Matrix,
        trainingWeights: FloatArray? = null,
        batchSize: Int = 32,
        maxEpochs: Int = 100,
        patience: Int = 10,
        shuffle: Boolean = true,
        tokensPerSample: Int = 1,
        lossFn: (NeuralNetwork) -> Float = { n -> n.predict(trainingFeatures).meanStandardError(trainingValues) },
    ): NeuralNetwork {

        require(trainingWeights == null || trainingWeights.size == trainingFeatures.size) {
            "Sample weights size must match number of training samples"
        }

        val x = featureTransforms?.fitAndTransform(trainingFeatures) ?: trainingFeatures
        val y = valueTransforms?.fitAndTransform(trainingValues) ?: trainingValues

        var bestLoss = Float.POSITIVE_INFINITY
        var bestModel: NeuralNetwork = this.clone()
        var epochsWithoutImprovement = 0

        val indices = x.indices.toMutableList()

        for (epoch in 1..maxEpochs) {
            if (shuffle) indices.shuffle()

            for (batchStart in indices.indices step batchSize) {
                val batchEnd = min(batchStart + batchSize, indices.size)
                val batchIndices = indices.subList(batchStart, batchEnd)

                val batchFeatures = batchIndices.map { x[it] }.toTypedArray()
                val batchTargetIndices = batchIndices.flatMap { seqIdx ->
                    (seqIdx * tokensPerSample until (seqIdx + 1) * tokensPerSample)
                }
                val batchLabels = batchTargetIndices.map { y[it] }.toTypedArray()
                val batchWeights = trainingWeights?.let { weights ->
                    batchIndices.map { weights[it] }.toFloatArray()
                }

                val batchPredictions = predict(batchFeatures, isTraining = true, skipFeatureTransform = true)
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

    /**
     * Trains the neural network on streaming data using mini-batch gradient descent with early stopping.
     *
     * @receiver The neural network instance to train.
     * @param source A lambda that provides a lazy [Sequence] of input-output pairs, where each pair is a feature vector ([FloatArray])
     *               and its corresponding label vector ([FloatArray]).
     * @param batchSize Number of samples per training batch. Default is 1024.
     * @param maxEpochs Maximum number of full passes over the data. Default is 20.
     * @param patience Number of consecutive epochs without loss improvement before early stopping. Default is 5.
     * @param testFrac Fraction of each batch reserved for testing/validation. Range: 0.0–1.0. Default is 0.1.
     * @param shuffle Whether to shuffle each batch before splitting into train/test subsets. Default is true.
     * @param lossFn Function to compute loss given predicted and actual matrices; defaults to mean standard error.
     * @param saveModelPath Optional path to save the model on every improvement. If null, no saving occurs.
     * @return A clone of this [NeuralNetwork] corresponding to the epoch with the best observed test loss.
     */
    fun trainStreaming(
        source: () -> Sequence<Pair<FloatArray, Array<FloatArray>>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Matrix, actual: Matrix) -> Float =
            { p, a -> p.meanStandardError(a) },
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
                    // --- split batch --------------------------------------------------
                    val seqSplitter = SequentialBatchSplitter()
                    val (xTrainRaw, yTrainRawList, xTestRaw, yTestRawList) =
                        seqSplitter.splitBatch(
                            bx.toTypedArray(), by.toTypedArray(),
                            testFraction = testFrac, shuffle = shuffle
                        )

                    // --- fit+transform *only* on training slice ----------------------
                    val xTrain = featureTransforms?.fitAndTransform(xTrainRaw) ?: xTrainRaw
                    val yTrainFlat = yTrainRawList.flatMap { it.toList() }.toTypedArray()
                    val yTrain = valueTransforms?.fitAndTransform(yTrainFlat) ?: yTrainFlat

                    // --- train step ---------------------------------------------------
                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backward(predTrain, yTrain)
                    updateParameters()

                    // --- evaluate on test slice (do NOT refit transforms) ------------
                    val xTest = featureTransforms?.apply(xTestRaw) ?: xTestRaw
                    val yTestFlat = yTestRawList.flatMap { it.toList() }.toTypedArray()
                    val yTest = valueTransforms?.apply(yTestFlat) ?: yTestFlat
                    val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)

                    runningTrainLoss += lossFn(predTrain, yTrain) * xTrain.size
                    runningTestLoss += lossFn(predTest, yTest) * xTest.size
                    testSamples += xTest.size

                    bx.clear(); by.clear()
                }
            }

            // left-overs
            if (bx.isNotEmpty()) {
                val seqSplitter = SequentialBatchSplitter()
                val (xT, yTList, xv, yvList) = seqSplitter.splitBatch(
                    bx.toTypedArray(), by.toTypedArray(),
                    testFraction = testFrac, shuffle = shuffle
                )
                val xTf = featureTransforms?.fitAndTransform(xT) ?: xT
                val yTfFlat = yTList.flatMap { it.toList() }.toTypedArray()
                val yTf = valueTransforms?.fitAndTransform(yTfFlat) ?: yTfFlat
                val predT = predict(xTf, true, skipFeatureTransform = true)
                backward(predT, yTf); updateParameters()

                val xv2 = featureTransforms?.apply(xv) ?: xv
                val yvFlat = yvList.flatMap { it.toList() }.toTypedArray()
                val yv2 = valueTransforms?.apply(yvFlat) ?: yvFlat
                val predV = predict(xv2, false, skipFeatureTransform = true)

                runningTrainLoss += lossFn(predT, yTf) * xTf.size
                runningTestLoss += lossFn(predV, yv2) * xv2.size
                testSamples += xv2.size
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.clone()
                epochsWithoutImprovement = 0
                
                // Save model to disk if saveModelPath is provided
                saveModelPath?.let { path ->
                    try {
                        best.saveToFile(path)
                        if (trace) {
                            println("Model saved to $path (loss: $epochTestLoss)")
                        }
                    } catch (e: Exception) {
                        if (trace) {
                            println("Warning: Failed to save model to $path: ${e.message}")
                        }
                    }
                }
            } else if (++epochsWithoutImprovement >= patience) {
                if (trace)
                    println("early stop at epoch $epoch")
                return best
            }
        }
        return best
    }

    /**
     * Memory-efficient training on streaming data using sparse targets and categorical cross-entropy.
     * 
     * This method dramatically reduces memory usage compared to trainStreaming() by working directly
     * with sparse target representations (token IDs) instead of dense one-hot vectors.
     * 
     * Memory comparison for vocab_size=50K, batch_size=1024, seq_length=512:
     * - trainStreaming (dense): ~200GB RAM for targets alone
     * - trainStreamingSparse: ~4MB RAM for targets
     *
     * @param source A lambda that provides a lazy [Sequence] of input-output pairs with sparse targets,
     *               where each pair is a feature vector ([FloatArray]) and sparse target array ([IntArray]).
     * @param batchSize Number of samples per training batch. Default is 1024.
     * @param maxEpochs Maximum number of full passes over the data. Default is 20.
     * @param patience Number of consecutive epochs without loss improvement before early stopping. Default is 5.
     * @param testFrac Fraction of each batch reserved for testing/validation. Range: 0.0–1.0. Default is 0.1.
     * @param shuffle Whether to shuffle each batch before splitting into train/test subsets. Default is true.
     * @param lossFn Function to compute sparse categorical cross-entropy loss given predictions and sparse targets.
     * @param comprehensiveLossFn Optional comprehensive loss function for final evaluation.
     * @param trace Whether to print training progress. Default is true.
     * @param saveModelPath Optional path to save the model on every improvement. If null, no saving occurs.
     * @return A clone of this [NeuralNetwork] corresponding to the epoch with the best observed test loss.
     */
    fun trainStreamingSparse(
        source: () -> Sequence<Pair<FloatArray, IntArray>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Matrix, sparseTargets: IntArray) -> Float =
            { p, s -> sparseCategoricalCrossEntropy(p, s) },
        probeFn: () -> Unit = {  },
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
                    // --- split batch --------------------------------------------------
                    val tokenSplitter = TokenBatchSplitter()
                    val (xTrainRaw, yTrainRaw, xTestRaw, yTestRaw) =
                        tokenSplitter.splitBatch(
                            bx.toTypedArray(), by.toTypedArray(),
                            testFraction = testFrac, shuffle = shuffle
                        )

                    // --- fit+transform *only* on training slice ----------------------
                    val xTrain = featureTransforms?.fitAndTransform(xTrainRaw) ?: xTrainRaw
                    val yTrainFlat = yTrainRaw.flatMap { it.toList() }.toIntArray()

                    // --- train step ---------------------------------------------------
                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backwardSparse(predTrain, yTrainFlat)
                    updateParameters()

                    // --- evaluate on test slice (do NOT refit transforms) ------------
                    val xTest = featureTransforms?.apply(xTestRaw) ?: xTestRaw
                    val yTestFlat = yTestRaw.flatMap { it.toList() }.toIntArray()

                    runningTrainLoss += lossFn(predTrain, yTrainFlat) * xTrain.size

                    if (xTest.isNotEmpty()) {
                        val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)
                        runningTestLoss += lossFn(predTest, yTestFlat) * xTest.size
                        testSamples += xTest.size
                    }

                    bx.clear(); by.clear()
                }
                if (iter.rem(100) == 0)
                    probeFn.invoke()
                iter++
            }

            // left-overs
            if (bx.isNotEmpty()) {
                val tokenSplitter = TokenBatchSplitter()
                val (xT, yT, xv, yv) = tokenSplitter.splitBatch(
                    bx.toTypedArray(), by.toTypedArray(),
                    testFraction = testFrac, shuffle = shuffle
                )
                val xTf = featureTransforms?.fitAndTransform(xT) ?: xT
                val yTfFlat = yT.flatMap { it.toList() }.toIntArray()
                val predT = predict(xTf, true, skipFeatureTransform = true)
                backwardSparse(predT, yTfFlat); updateParameters()

                val xv2 = featureTransforms?.apply(xv) ?: xv
                val yvFlat = yv.flatMap { it.toList() }.toIntArray()
                val predV = predict(xv2, false, skipFeatureTransform = true)

                runningTrainLoss += lossFn(predT, yTfFlat) * xTf.size
                runningTestLoss += lossFn(predV, yvFlat) * xv2.size
                testSamples += xv2.size
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.clone()
                epochsWithoutImprovement = 0
                
                // Save model to disk if saveModelPath is provided
                saveModelPath?.let { path ->
                    try {
                        best.saveToFile(path)
                        if (trace) {
                            println("Model saved to $path (loss: $epochTestLoss)")
                        }
                    } catch (e: Exception) {
                        if (trace) {
                            println("Warning: Failed to save model to $path: ${e.message}")
                        }
                    }
                }
            } else if (++epochsWithoutImprovement >= patience) {
                if (trace)
                    println("early stop at epoch $epoch")
                return best
            }
        }
        return best
    }

    /**
     * Performs a single training step on a batch of raw input and label matrices.
     *
     * This method:
     * 1. Fits and transforms the raw feature and label matrices using configured transforms.
     * 2. Executes a forward pass in training mode (skipping feature transforms).
     * 3. Performs backpropagation and updates model parameters using Adam optimizer.
     *
     * @param xRaw Raw feature matrix (samples × features) to train on.
     * @param yRaw Raw label matrix (samples × outputs) corresponding to [xRaw].
     */
    private fun trainOnBatch(xRaw: Matrix, yRaw: Matrix) {
        // 1. fit + transform in ONE call; each column transform updates itself
        val x = featureTransforms?.fitAndTransform(xRaw) ?: xRaw
        val y = valueTransforms?.fitAndTransform(yRaw) ?: yRaw

        // 2. forward / backward / Adam
        val pred = predict(x, isTraining = true, skipFeatureTransform = true)
        backward(pred, y)
        updateParameters()
    }

    /**
     * Creates a deep copy of the current neural network including internal state.
     *
     * @return A cloned instance of this neural network.
     */
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
            this.lastInput = this@NeuralNetwork.lastInput?.deepCopy()
        }

    /**
     * Saves this neural network to a file using Java serialization.
     *
     * @param filePath Path where to save the model.
     */
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
        
        /**
         * Loads a neural network from a file using Java deserialization.
         *
         * @param filePath Path to the saved model file.
         * @return The loaded neural network.
         */
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

        /**
         * Load a model from *path* if the file exists, otherwise create one with the
         * supplied [creator] lambda.
         *
         * @param path    Path to the serialized model file.
         * @param creator Lambda that builds a brand‑new NeuralNetwork when the file
         *                is missing (or you want to start from scratch).
         */
        fun loadOrCreate(path: String, creator: () -> NeuralNetwork): NeuralNetwork {
            val f = File(path)
            return if (f.isFile && f.canRead()) {
                try {
                    loadFromFile(path).also {
                        println("✅ Loaded model from $path")
                    }
                } catch (e: Exception) {
                    // If the file is corrupted we fall back to a fresh model
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
