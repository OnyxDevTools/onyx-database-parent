@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.transformation.*
import java.io.Serializable
import kotlin.apply
import kotlin.math.min

typealias Matrix = Array<DoubleArray>

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
    var learningRate: Double = 1e-3,
    var lambda: Double = 1e-4,
    var beta1: Double = 0.9,
    var beta2: Double = 0.999,
) : Serializable {

    private var beta1Power = 1.0
    private var beta2Power = 1.0
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
        sampleWeights: DoubleArray? = null
    ) {
        val sampleCount = actual.size.toDouble()
        val weights = sampleWeights ?: DoubleArray(actual.size) { 1.0 }

        // Compute softmax probabilities
        val probs = softmax(predicted)

        // Delta for CE loss: probs - actual
        var delta: Matrix = subtract(probs, actual).mapIndexed { r, row ->
            DoubleArray(row.size) { c -> row[c] * weights[r] }
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
        sampleWeights: DoubleArray? = null
    ) {
        val sampleCount = predicted.size.toDouble()
        
        // Check if the last layer supports optimized sparse backward pass
        val lastLayer = layers.lastOrNull()
        
        var delta: Matrix = when (lastLayer) {
            is com.onyxdevtools.ai.layer.impl.SparseDenseLayer -> {
                // Use optimized sparse backward pass for SparseDenseLayer
                val previousLayer = layers.getOrNull(layers.lastIndex - 1)
                val inputToLastLayer = previousLayer?.output ?: lastInput
                ?: error("No input recorded for last layer")
                
                lastLayer.backwardSparse(
                    currentInput = inputToLastLayer,
                    predictions = predicted,
                    sparseTargets = sparseTargets,
                    featureSize = sampleCount,
                    previousLayer = previousLayer,
                    lambda = lambda
                )
            }
            is com.onyxdevtools.ai.layer.impl.FastDenseLayer -> {
                // Use optimized sparse backward pass for FastDenseLayer
                val previousLayer = layers.getOrNull(layers.lastIndex - 1)
                val inputToLastLayer = previousLayer?.output ?: lastInput
                ?: error("No input recorded for last layer")
                
                lastLayer.backwardSparse(
                    currentInput = inputToLastLayer,
                    predictions = predicted,
                    sparseTargets = sparseTargets,
                    featureSize = sampleCount,
                    previousLayer = previousLayer,
                    lambda = lambda
                )
            }
            else -> {
                // Fall back to standard sparse categorical cross-entropy gradients
                sparseCategoricalCrossEntropyGradients(predicted, sparseTargets, sampleWeights)
            }
        }
        
        // Continue backward pass through remaining layers (skip last layer if it handled its own backward pass)
        val startIndex = when (lastLayer) {
            is com.onyxdevtools.ai.layer.impl.SparseDenseLayer,
            is com.onyxdevtools.ai.layer.impl.FastDenseLayer -> layers.lastIndex - 1
            else -> layers.lastIndex
        }

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
        trainingWeights: DoubleArray? = null,
        batchSize: Int = 32,
        maxEpochs: Int = 100,
        patience: Int = 10,
        shuffle: Boolean = true,
        tokensPerSample: Int = 1,
        lossFn: (NeuralNetwork) -> Double = { n -> n.predict(trainingFeatures).meanStandardError(trainingValues) },
    ): NeuralNetwork {

        require(trainingWeights == null || trainingWeights.size == trainingFeatures.size) {
            "Sample weights size must match number of training samples"
        }

        val x = featureTransforms?.fitAndTransform(trainingFeatures) ?: trainingFeatures
        val y = valueTransforms?.fitAndTransform(trainingValues) ?: trainingValues

        var bestLoss = Double.POSITIVE_INFINITY
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
                    batchIndices.map { weights[it] }.toDoubleArray()
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
     * @param source A lambda that provides a lazy [Sequence] of input-output pairs, where each pair is a feature vector ([DoubleArray])
     *               and its corresponding label vector ([DoubleArray]).
     * @param batchSize Number of samples per training batch. Default is 1024.
     * @param maxEpochs Maximum number of full passes over the data. Default is 20.
     * @param patience Number of consecutive epochs without loss improvement before early stopping. Default is 5.
     * @param testFrac Fraction of each batch reserved for testing/validation. Range: 0.0–1.0. Default is 0.1.
     * @param shuffle Whether to shuffle each batch before splitting into train/test subsets. Default is true.
     * @param lossFn Function to compute loss given predicted and actual matrices; defaults to mean standard error.
     * @return A clone of this [NeuralNetwork] corresponding to the epoch with the best observed test loss.
     */
    fun trainStreaming(
        source: () -> Sequence<Pair<DoubleArray, Array<DoubleArray>>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Double = 0.1,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Matrix, actual: Matrix) -> Double =
            { p, a -> p.meanStandardError(a) },
        comprehensiveLossFn: ((NeuralNetwork) -> Double)? = null,
    ): NeuralNetwork {

        var bestLoss = Double.POSITIVE_INFINITY
        var best = this.clone()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<DoubleArray>()
            val by = mutableListOf<Array<DoubleArray>>()
            var runningTrainLoss = 0.0
            var runningTestLoss = 0.0
            var testSamples = 0

            for ((inputSeq, targetSeqs) in source()) {
                bx += inputSeq; by += targetSeqs
                if (bx.size == batchSize) {
                    // --- split batch --------------------------------------------------
                    val (xTrainRaw, yTrainRawList, xTestRaw, yTestRawList) =
                        splitBatchSeq(
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
                val (xT, yTList, xv, yvList) = splitBatchSeq(
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
     *               where each pair is a feature vector ([DoubleArray]) and sparse target array ([IntArray]).
     * @param batchSize Number of samples per training batch. Default is 1024.
     * @param maxEpochs Maximum number of full passes over the data. Default is 20.
     * @param patience Number of consecutive epochs without loss improvement before early stopping. Default is 5.
     * @param testFrac Fraction of each batch reserved for testing/validation. Range: 0.0–1.0. Default is 0.1.
     * @param shuffle Whether to shuffle each batch before splitting into train/test subsets. Default is true.
     * @param lossFn Function to compute sparse categorical cross-entropy loss given predictions and sparse targets.
     * @param comprehensiveLossFn Optional comprehensive loss function for final evaluation.
     * @param trace Whether to print training progress. Default is true.
     * @return A clone of this [NeuralNetwork] corresponding to the epoch with the best observed test loss.
     */
    fun trainStreamingSparse(
        source: () -> Sequence<Pair<DoubleArray, IntArray>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Double = 0.2,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Matrix, sparseTargets: IntArray) -> Double =
            { p, s -> sparseCategoricalCrossEntropy(p, s) },
        comprehensiveLossFn: ((NeuralNetwork) -> Double)? = null,
    ): NeuralNetwork {

        var bestLoss = Double.POSITIVE_INFINITY
        var best = this.clone()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<DoubleArray>()
            val by = mutableListOf<IntArray>()
            var runningTrainLoss = 0.0
            var runningTestLoss = 0.0
            var testSamples = 0

            for ((inputSeq, targetSeq) in source()) {
                bx += inputSeq; by += targetSeq
                if (bx.size == batchSize) {
                    // --- split batch --------------------------------------------------
                    val (xTrainRaw, yTrainRaw, xTestRaw, yTestRaw) =
                        splitBatchSparse(
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
                    val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)

                    runningTrainLoss += lossFn(predTrain, yTrainFlat) * xTrain.size
                    runningTestLoss += lossFn(predTest, yTestFlat) * xTest.size
                    testSamples += xTest.size

                    bx.clear(); by.clear()
                }
            }

            // left-overs
            if (bx.isNotEmpty()) {
                val (xT, yT, xv, yv) = splitBatchSparse(
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
            } else if (++epochsWithoutImprovement >= patience) {
                if (trace)
                    println("early stop at epoch $epoch")
                return best
            }
        }
        return best
    }

    /**
     * Splits a batch of feature and label matrices into training and test subsets.
     *
     * @param x The full feature matrix (rows of samples × features).
     * @param y The full label matrix (rows of samples × outputs).
     * @param testFraction Fraction of samples to reserve for testing (0.0–1.0). Default is 0.1.
     * @param shuffle Whether to shuffle samples before splitting. Default is true.
     * @return A [Quad] containing:
     *   - a: training feature matrix
     *   - b: training label matrix
     *   - c: test feature matrix
     *   - d: test label matrix
     */
    private fun splitBatchSeq(
        x: Array<DoubleArray>,
        y: Array<Array<DoubleArray>>,
        testFraction: Double = 0.1,
        shuffle: Boolean = true
    ): Quad<Array<DoubleArray>, List<Array<DoubleArray>>, Array<DoubleArray>, List<Array<DoubleArray>>> {
        val idx = x.indices.toMutableList()
        if (shuffle) idx.shuffle()
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subsetInputs(src: Array<DoubleArray>, ids: List<Int>) =
            Array(ids.size) { i -> src[ids[i]] }

        fun subsetTargets(src: Array<Array<DoubleArray>>, ids: List<Int>) =
            ids.map { src[it] }

        return Quad(
            subsetInputs(x, trainIdx), subsetTargets(y, trainIdx),       // train
            subsetInputs(x, testIdx), subsetTargets(y, testIdx)         // test
        )
    }

    private fun splitBatch(
        x: Matrix,
        y: Matrix,
        testFraction: Double = 0.1,
        shuffle: Boolean = true
    ): Quad<Matrix, Matrix, Matrix, Matrix> {
        val idx = x.indices.toMutableList()
        if (shuffle) idx.shuffle()
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subset(src: Matrix, ids: List<Int>) =
            Array(ids.size) { i -> src[ids[i]] }

        return Quad(
            subset(x, trainIdx), subset(y, trainIdx),       // train
            subset(x, testIdx), subset(y, testIdx)         // test
        )
    }

    /**
     * Splits a batch of feature arrays and sparse target arrays into training and test subsets.
     *
     * @param x The full feature matrix (rows of samples × features).
     * @param y The full sparse target matrix (rows of IntArrays with target token IDs).
     * @param testFraction Fraction of samples to reserve for testing (0.0–1.0). Default is 0.1.
     * @param shuffle Whether to shuffle samples before splitting. Default is true.
     * @return A [Quad] containing:
     *   - a: training feature matrix
     *   - b: training sparse target arrays
     *   - c: test feature matrix
     *   - d: test sparse target arrays
     */
    private fun splitBatchSparse(
        x: Array<DoubleArray>,
        y: Array<IntArray>,
        testFraction: Double = 0.1,
        shuffle: Boolean = true
    ): Quad<Array<DoubleArray>, List<IntArray>, Array<DoubleArray>, List<IntArray>> {
        // For very small batches, don't split - use all data for training
        if (x.size <= 2) {
            return Quad(
                x, y.toList(),                    // All data goes to training
                emptyArray(), emptyList()         // Empty test set
            )
        }
        
        val idx = x.indices.toMutableList()
        if (shuffle) idx.shuffle()
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        
        // Ensure we have at least 1 training sample
        val actualTestSize = minOf(testSize, idx.size - 1)
        val testIdx = idx.take(actualTestSize)
        val trainIdx = idx.drop(actualTestSize)

        fun subsetInputs(src: Array<DoubleArray>, ids: List<Int>) =
            Array(ids.size) { i -> src[ids[i]] }

        fun subsetSparseTargets(src: Array<IntArray>, ids: List<Int>) =
            ids.map { src[it] }

        return Quad(
            subsetInputs(x, trainIdx), subsetSparseTargets(y, trainIdx),       // train
            subsetInputs(x, testIdx), subsetSparseTargets(y, testIdx)         // test
        )
    }

    /**
     * A tuple of four values.
     *
     * @param A Type of the first element.
     * @param B Type of the second element.
     * @param C Type of the third element.
     * @param D Type of the fourth element.
     * @property a First value.
     * @property b Second value.
     * @property c Third value.
     * @property d Fourth value.
     */
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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

    companion object {
        private const val serialVersionUID = 1L
    }
}
