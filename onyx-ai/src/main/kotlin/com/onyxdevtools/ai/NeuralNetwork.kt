@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.transformation.*
import com.onyxdevtools.ai.batch.TokenBatchSplitter
import java.io.*
import kotlin.apply
import kotlin.math.min

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
 * @property precision The precision to use for internal computations (SINGLE or DOUBLE)
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
    val precision: MatrixPrecision = MatrixPrecision.DOUBLE,
) : Serializable {

    private var beta1Power = 1.0
    private var beta2Power = 1.0
    private var lastInput: FlexibleMatrix? = null

    fun withTransforms(
        feature: ColumnTransforms = featureTransforms ?: emptyList(),
        label: ColumnTransforms = valueTransforms ?: emptyList(),
    ): NeuralNetwork = copy(featureTransforms = feature, valueTransforms = label)

    /**
     * Helper function to create FlexibleMatrix from Matrix array
     */
    private fun createFlexibleMatrixFromArray(matrices: Array<DoubleArray>): FlexibleMatrix {
        return matrices.toFlexibleMatrix()
    }

    /**
     * Helper function to stack multiple FlexibleMatrix vertically
     */
    private fun stackMatricesVertically(matrices: List<FlexibleMatrix>): FlexibleMatrix {
        if (matrices.isEmpty()) {
            return createMatrix(0, 0, precision == MatrixPrecision.SINGLE)
        }
        if (matrices.size == 1) {
            return matrices[0]
        }
        
        val totalRows = matrices.map { it.rows }.sum()
        val cols = matrices[0].cols
        val isSingle = matrices[0].isSinglePrecision
        
        return createMatrix(totalRows, cols, isSingle) { r, c ->
            var currentRowOffset = 0
            for (matrix in matrices) {
                if (r >= currentRowOffset && r < currentRowOffset + matrix.rows) {
                    return@createMatrix matrix[r - currentRowOffset, c]
                }
                currentRowOffset += matrix.rows
            }
            0.0 // Should never reach here
        }
    }

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
        input: FlexibleMatrix,
        isTraining: Boolean = false,
        returnOriginalScale: Boolean = false,
        skipFeatureTransform: Boolean = false
    ): FlexibleMatrix {
        val inputMatrix = input.toMatrix()
        val x = if (skipFeatureTransform)
            inputMatrix
        else
            featureTransforms?.apply(inputMatrix) ?: inputMatrix
        lastInput = x.toFlexibleMatrix()

        var out = x.toFlexibleMatrix()
        layers.forEachIndexed { idx, layer ->
            out = layer.forward(out, isTraining, layers.getOrNull(idx + 1))
        }

        return if (returnOriginalScale && valueTransforms?.any { it != null } == true)
            valueTransforms.inverse(out.toMatrix()).toFlexibleMatrix()
        else
            out
    }
    
    /**
     * Backward compatibility method for legacy Matrix input
     */
    fun predict(
        input: Matrix,
        isTraining: Boolean = false,
        returnOriginalScale: Boolean = false,
        skipFeatureTransform: Boolean = false
    ): Matrix = predict(input.toFlexibleMatrix(), isTraining, returnOriginalScale, skipFeatureTransform).toMatrix()

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
        predicted: FlexibleMatrix,
        actual: FlexibleMatrix,
        sampleWeights: DoubleArray? = null
    ) {
        val sampleCount = actual.rows.toDouble()
        val weights = sampleWeights ?: DoubleArray(actual.rows) { 1.0 }

        // Compute softmax probabilities using FlexibleMatrix extensions
        val probs = softmax(predicted)

        // Delta for CE loss: probs - actual, weighted by sample weights
        val delta = createMatrix(probs.rows, probs.cols, probs.isSinglePrecision) { r, c ->
            (probs[r, c] - actual[r, c]) * weights[r]
        }

        var currentDelta = delta
        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            val nextLayer = layers.getOrNull(i + 1)      // forward-order "next"
            val previousLayer = layers.getOrNull(i - 1)      // forward-order "prev"

            val inputToLayer = previousLayer?.output ?: lastInput
            ?: error("No input recorded for layer $i")

            currentDelta = layer.backward(
                currentInput = inputToLayer,
                delta = currentDelta,
                featureSize = sampleCount,
                nextLayer = nextLayer,
                previousLayer = previousLayer,
                lambda = lambda
            )
        }
    }

    /**
     * Backward compatibility method for legacy Matrix input
     */
    private fun backward(
        predicted: Matrix,
        actual: Matrix,
        sampleWeights: DoubleArray? = null
    ) = backward(predicted.toFlexibleMatrix(), actual.toFlexibleMatrix(), sampleWeights)

    /**
     * Performs the backward pass to compute gradients using sparse targets.
     *
     * @param predicted The predicted output logits.
     * @param sparseTargets The true labels as token IDs (sparse representation).
     * @param sampleWeights Optional sample weights.
     */
    private fun backwardSparse(
        predicted: FlexibleMatrix,
        sparseTargets: IntArray,
        sampleWeights: DoubleArray? = null
    ) {
        val sampleCount = predicted.rows.toDouble()
        
        // Use FlexibleMatrix version to avoid copying
        var delta = sparseCategoricalCrossEntropyGradients(predicted, sparseTargets, sampleWeights)

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
     * Backward compatibility method for legacy Matrix input
     */
    private fun backwardSparse(
        predicted: Matrix,
        sparseTargets: IntArray,
        sampleWeights: DoubleArray? = null
    ) = backwardSparse(predicted.toFlexibleMatrix(), sparseTargets, sampleWeights)

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
        trainingFeatures: FlexibleMatrix,
        trainingValues: FlexibleMatrix,
        trainingWeights: DoubleArray? = null,
        batchSize: Int = 32,
        maxEpochs: Int = 100,
        patience: Int = 10,
        shuffle: Boolean = true,
        tokensPerSample: Int = 1,
        lossFn: (NeuralNetwork) -> Double = { n -> n.predict(trainingFeatures).meanStandardError(trainingValues) },
    ): NeuralNetwork {

        require(trainingWeights == null || trainingWeights.size == trainingFeatures.rows) {
            "Sample weights size must match number of training samples"
        }

        val trainingFeaturesMatrix = trainingFeatures.toMatrix()
        val trainingValuesMatrix = trainingValues.toMatrix()
        val x = featureTransforms?.fitAndTransform(trainingFeaturesMatrix) ?: trainingFeaturesMatrix
        val y = valueTransforms?.fitAndTransform(trainingValuesMatrix) ?: trainingValuesMatrix

        var bestLoss = Double.POSITIVE_INFINITY
        var bestModel: NeuralNetwork = this.copy()
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

                val batchPredictions = predict(batchFeatures.toFlexibleMatrix(), isTraining = true, skipFeatureTransform = true)
                backward(batchPredictions, batchLabels.toFlexibleMatrix(), batchWeights)
                updateParameters()
            }

            val loss = lossFn(this)
            if (loss < bestLoss) {
                bestLoss = loss
                bestModel = this.copy()
                epochsWithoutImprovement = 0
            } else if (++epochsWithoutImprovement > patience) {
                break
            }
        }
        return bestModel
    }

    /**
     * Backward compatibility method for legacy Matrix input
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
    ): NeuralNetwork = train(
        trainingFeatures.toFlexibleMatrix(),
        trainingValues.toFlexibleMatrix(),
        trainingWeights,
        batchSize,
        maxEpochs,
        patience,
        shuffle,
        tokensPerSample,
        lossFn
    )

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
     * @param saveModelPath Optional path to save the model on every improvement. If null, no saving occurs.
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
        lossFn: (pred: FlexibleMatrix, actual: FlexibleMatrix) -> Double =
            { p, a -> p.meanStandardError(a) },
        comprehensiveLossFn: ((NeuralNetwork) -> Double)? = null,
        saveModelPath: String? = null,
    ): NeuralNetwork {

        var bestLoss = Double.POSITIVE_INFINITY
        var best = this.copy()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<DoubleArray>()
            val by = mutableListOf<Array<DoubleArray>>()
            var runningLoss = 0.0
            var batchCount = 0

            for ((inputSeq, targetSeqs) in source()) {
                bx += inputSeq; by += targetSeqs
                if (bx.size == batchSize) {
                    // Simple batch processing
                    val xBatch = bx.toTypedArray()
                    val yBatch = by.flatMap { it.toList() }.toTypedArray()
                    
                    // Apply transforms if available
                    val xTrained = featureTransforms?.fitAndTransform(xBatch) ?: xBatch
                    val yTrained = valueTransforms?.fitAndTransform(yBatch) ?: yBatch
                    
                    // Train step
                    val predTrain = predict(xTrained.toFlexibleMatrix(), isTraining = true, skipFeatureTransform = true)
                    backward(predTrain, yTrained.toFlexibleMatrix())
                    updateParameters()
                    
                    // Track loss
                    runningLoss += lossFn(predTrain, yTrained.toFlexibleMatrix())
                    batchCount++

                    bx.clear(); by.clear()
                }
            }

            // Process remaining samples
            if (bx.isNotEmpty()) {
                val xBatch = bx.toTypedArray()
                val yBatch = by.flatMap { it.toList() }.toTypedArray()
                
                val xTrained = featureTransforms?.fitAndTransform(xBatch) ?: xBatch
                val yTrained = valueTransforms?.fitAndTransform(yBatch) ?: yBatch
                val predTrain = predict(xTrained.toFlexibleMatrix(), true, skipFeatureTransform = true)
                backward(predTrain, yTrained.toFlexibleMatrix())
                updateParameters()

                runningLoss += lossFn(predTrain, yTrained.toFlexibleMatrix())
                batchCount++
            }

            val epochLoss = if (batchCount > 0) runningLoss / batchCount else Double.POSITIVE_INFINITY
            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: epochLoss

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.copy()
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
     *               where each pair is a feature vector ([DoubleArray]) and sparse target array ([IntArray]).
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
        source: () -> Sequence<Pair<FlexibleMatrix, IntArray>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Double = 0.1,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: FlexibleMatrix, sparseTargets: IntArray) -> Double =
            { p, s -> sparseCategoricalCrossEntropy(p, s) },
        probeFn: () -> Unit = {  },
        comprehensiveLossFn: ((NeuralNetwork) -> Double)? = null,
        saveModelPath: String? = null,
    ): NeuralNetwork {

        var bestLoss = Double.POSITIVE_INFINITY
        var best = this.copy()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<FlexibleMatrix>()
            val by = mutableListOf<IntArray>()
            var runningTrainLoss = 0.0
            var runningTestLoss = 0.0
            var testSamples = 0

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

                    // --- Skip legacy transforms for now and work directly with FlexibleMatrix ----------------------
                    val yTrainFlat = yTrainRaw.flatMap { it.toList() }.toIntArray()

                    // --- train step ---------------------------------------------------
                    // Use a batch concatenation approach - stack all FlexibleMatrix vertically  
                    val predTrain = if (xTrainRaw.isNotEmpty()) {
                        val batchInput = stackMatricesVertically(xTrainRaw.toList())
                        predict(batchInput, isTraining = true, skipFeatureTransform = true)
                    } else {
                        createMatrix(0, 0, precision == MatrixPrecision.SINGLE)
                    }
                    
                    if (yTrainFlat.isNotEmpty()) {
                        backwardSparse(predTrain, yTrainFlat)
                        updateParameters()
                    }

                    // --- evaluate on test slice (do NOT refit transforms) ------------
                    val yTestFlat = yTestRaw.flatMap { it.toList() }.toIntArray()
                    val predTest = if (xTestRaw.isNotEmpty()) {
                        val batchInput = stackMatricesVertically(xTestRaw.toList())
                        predict(batchInput, isTraining = false, skipFeatureTransform = true)
                    } else {
                        createMatrix(0, 0, precision == MatrixPrecision.SINGLE)
                    }

                    runningTrainLoss += lossFn(predTrain, yTrainFlat) * xTrainRaw.size
                    runningTestLoss += lossFn(predTest, yTestFlat) * xTestRaw.size
                    testSamples += xTestRaw.size

                    bx.clear(); by.clear()
                    probeFn.invoke()
                }
            }

            // left-overs
            if (bx.isNotEmpty()) {
                val tokenSplitter = TokenBatchSplitter()
                val (xT, yT, xv, yv) = tokenSplitter.splitBatch(
                    bx.toTypedArray(), by.toTypedArray(),
                    testFraction = testFrac, shuffle = shuffle
                )
                
                // Skip legacy transforms and work directly with FlexibleMatrix
                val yTfFlat = yT.flatMap { it.toList() }.toIntArray()
                val predT = if (xT.isNotEmpty()) {
                    val batchInput = stackMatricesVertically(xT.toList())
                    predict(batchInput, true, skipFeatureTransform = true)
                } else {
                    createMatrix(0, 0, precision == MatrixPrecision.SINGLE)
                }
                
                if (yTfFlat.isNotEmpty()) {
                    backwardSparse(predT, yTfFlat); updateParameters()
                }

                val yvFlat = yv.flatMap { it.toList() }.toIntArray()
                val predV = if (xv.isNotEmpty()) {
                    val batchInput = stackMatricesVertically(xv.toList())
                    predict(batchInput, false, skipFeatureTransform = true)
                } else {
                    createMatrix(0, 0, precision == MatrixPrecision.SINGLE)
                }

                runningTrainLoss += lossFn(predT, yTfFlat) * xT.size
                runningTestLoss += lossFn(predV, yvFlat) * xv.size
                testSamples += xv.size
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

            if (epochTestLoss < bestLoss) {
                bestLoss = epochTestLoss
                best = this.copy()
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
    private fun trainOnBatch(xRaw: FlexibleMatrix, yRaw: FlexibleMatrix) {
        // 1. fit + transform in ONE call; each column transform updates itself
        val xRawMatrix = xRaw.toMatrix()
        val yRawMatrix = yRaw.toMatrix()
        val x = featureTransforms?.fitAndTransform(xRawMatrix) ?: xRawMatrix
        val y = valueTransforms?.fitAndTransform(yRawMatrix) ?: yRawMatrix

        // 2. forward / backward / Adam
        val pred = predict(x.toFlexibleMatrix(), isTraining = true, skipFeatureTransform = true)
        backward(pred, y.toFlexibleMatrix())
        updateParameters()
    }

    /**
     * Backward compatibility method for legacy Matrix input
     */
    private fun trainOnBatch(xRaw: Matrix, yRaw: Matrix) = 
        trainOnBatch(xRaw.toFlexibleMatrix(), yRaw.toFlexibleMatrix())

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
            precision = precision,
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
    }
}
