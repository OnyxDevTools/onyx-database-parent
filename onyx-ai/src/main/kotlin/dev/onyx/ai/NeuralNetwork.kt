@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.onyx.ai

import dev.onyx.ai.extensions.*
import dev.onyx.ai.layer.Layer
import dev.onyx.ai.transformation.*
import dev.onyx.ai.batch.SequentialBatchSplitter
import dev.onyx.ai.batch.TokenBatchSplitter
import java.io.*
import kotlin.apply
import kotlin.math.min
import kotlin.math.ln

/**
 * Represents a multi-layer neural network using backpropagation and the Adam optimizer.
 *
 * This Tensor-only version fixes unresolved functions and Tensor mismatches.
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
        input: Tensor,
        isTraining: Boolean = false,
        returnOriginalScale: Boolean = false,
        skipFeatureTransform: Boolean = false
    ): Tensor {
        val x: Tensor = if (skipFeatureTransform) input else (featureTransforms?.apply(input) ?: input)
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

    /** Convenience default: de-normalized prediction */
    fun predict(input: Tensor): Tensor = predict(input = input, isTraining = false, returnOriginalScale = true)

    // -------------------- Backward (dense targets) --------------------------

    private fun backward(
        predicted: Tensor,
        actual: Tensor,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = actual.rows.toFloat()

        // softmax over rows
        val probs = predicted.softmaxRows()

        // CE gradient: probs - actual
        var delta: Tensor = probs.sub(actual)

        // optional per-row weights
        if (sampleWeights != null) {
            require(sampleWeights.size == actual.rows) {
                "sampleWeights size (${sampleWeights.size}) must equal number of rows (${actual.rows})"
            }
            delta = Tensor(delta.rows, delta.cols) { r, c -> delta[r, c] * sampleWeights[r] }
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

    // -------------------- Backward (sparse targets) ------------------------

    private fun backwardSparse(
        predicted: Tensor,
        sparseTargets: IntArray,
        sampleWeights: FloatArray? = null
    ) {
        val sampleCount = predicted.rows.toFloat()

        // dCE/dlogits for sparse targets (row-wise softmax + gradient)
        var delta = sparseCategoricalCrossEntropyGradients(predicted, sparseTargets, sampleWeights)

        val startIndex = layers.lastIndex
        for (i in startIndex downTo 0) {
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

    // -------------------- Parameter update ---------------------------------

    private fun updateParameters() {
        beta1Power *= beta1
        beta2Power *= beta2
        layers.forEach { layer ->
            layer.updateParameters(beta1Power, beta2Power, beta1, beta2, learningRate)
        }
    }

    // -------------------- Train (fixed-size labels in a single Tensor) -----

    fun train(
        trainingFeatures: Tensor,
        trainingValues: Tensor,
        trainingWeights: FloatArray? = null,
        batchSize: Int = 32,
        maxEpochs: Int = 100,
        patience: Int = 10,
        shuffle: Boolean = true,
        tokensPerSample: Int = 1,
        lossFn: (NeuralNetwork) -> Float = { n -> meanStandardError(n.predict(trainingFeatures), trainingValues) },
    ): NeuralNetwork {

        require(trainingWeights == null || trainingWeights.size == trainingFeatures.rows) {
            "Sample weights size (${trainingWeights?.size}) must match number of training samples (${trainingFeatures.rows})"
        }

        // Fit transforms once up-front
        val x: Tensor = featureTransforms?.fitAndTransform(trainingFeatures) ?: trainingFeatures
        val y: Tensor = valueTransforms?.fitAndTransform(trainingValues) ?: trainingValues

        var bestLoss = Float.POSITIVE_INFINITY
        var bestModel: NeuralNetwork = this.clone()
        var epochsWithoutImprovement = 0

        val indices = x.indices.toMutableList()

        for (epoch in 1..maxEpochs) {
            if (shuffle) indices.shuffle()

            var batchStart = 0
            while (batchStart < indices.size) {
                val batchEnd = min(batchStart + batchSize, indices.size)
                val batchSeqIdx = indices.subList(batchStart, batchEnd)

                // gather feature rows by sample
                val batchX = gatherRows(x, batchSeqIdx)

                // target rows: expand each sample → its token rows
                val tokenRowIdx = batchSeqIdx.flatMap { seqIdx ->
                    (seqIdx * tokensPerSample until (seqIdx + 1) * tokensPerSample)
                }
                val batchY = gatherRows(y, tokenRowIdx)

                // expand sample weights (if any) to per-token rows
                val weightsExpanded: FloatArray? = trainingWeights?.let { w ->
                    val arr = FloatArray(tokenRowIdx.size)
                    var pos = 0
                    for (seqIdx in batchSeqIdx) {
                        val wv = w[seqIdx]
                        repeat(tokensPerSample) { arr[pos++] = wv }
                    }
                    arr
                }

                val pred = predict(batchX, isTraining = true, skipFeatureTransform = true)
                backward(pred, batchY, weightsExpanded)
                updateParameters()

                batchStart = batchEnd
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

    // -------------------- Train (streaming, dense labels per sequence) -----

    fun trainStreaming(
        source: () -> Sequence<Pair<FloatArray, Tensor>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        trace: Boolean = true,
        lossFn: (pred: Tensor, actual: Tensor) -> Float = { p, a -> meanStandardError(p, a) },
        comprehensiveLossFn: ((NeuralNetwork) -> Float)? = null,
        saveModelPath: String? = null,
    ): NeuralNetwork {

        var bestLoss = Float.POSITIVE_INFINITY
        var best = this.clone()
        var epochsWithoutImprovement = 0

        repeat(maxEpochs) { epoch ->
            val bx = mutableListOf<FloatArray>()
            val by = mutableListOf<Tensor>() // keep as sequences of rows, convert later
            var runningTrainLoss = 0.0f
            var runningTestLoss = 0.0f
            var testSamples = 0

            for ((inputSeq, targetSeqs) in source()) {
                bx += inputSeq
                by += targetSeqs
                if (bx.size == batchSize) {
                    // --- split batch by sequences -----------------------------------
                    val seqSplitter = SequentialBatchSplitter()
                    val (xTrainRaw, yTrainRawList, xTestRaw, yTestRawList) = seqSplitter.splitBatch(
                        Tensor(bx.size, bx[0].size) { r, c -> bx[r][c] },
                        by.toTypedArray(),
                        testFraction = testFrac,
                        shuffle = shuffle
                    )

                    // --- features: fit+transform ONLY on train ----------------------
                    val xTrain: Tensor = featureTransforms?.fitAndTransform(xTrainRaw) ?: xTrainRaw

                    // --- labels: flatten lists of sequences -> Tensor ---------------
                    val yTrainTensor = concatSequencesToTensor(yTrainRawList.toList())
                    val yTrain: Tensor = valueTransforms?.fitAndTransform(yTrainTensor) ?: yTrainTensor

                    // --- train step ---------------------------------------------------
                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backward(predTrain, yTrain)
                    updateParameters()

                    // --- evaluate on test slice --------------------------------------
                    val xTest: Tensor = featureTransforms?.apply(xTestRaw) ?: xTestRaw
                    val yTestTensor = concatSequencesToTensor(yTestRawList.toList())
                    val yTest: Tensor = valueTransforms?.apply(yTestTensor) ?: yTestTensor

                    val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)

                    runningTrainLoss += lossFn(predTrain, yTrain) * xTrain.rows
                    runningTestLoss += lossFn(predTest, yTest) * xTest.rows
                    testSamples += xTest.rows

                    bx.clear(); by.clear()
                }
            }

            // leftovers --------------------------------------------------------------
            if (bx.isNotEmpty()) {
                val seqSplitter = SequentialBatchSplitter()
                val (xT, yTList, xv, yvList) = seqSplitter.splitBatch(
                    Tensor(bx.size, bx[0].size) { r, c -> bx[r][c] },
                    by.toTypedArray(),
                    testFraction = testFrac,
                    shuffle = shuffle
                )

                val xTf: Tensor = featureTransforms?.fitAndTransform(xT) ?: xT
                val yTfTensor = concatSequencesToTensor(yTList.toList())
                val yTf: Tensor = valueTransforms?.fitAndTransform(yTfTensor) ?: yTfTensor
                val predT = predict(xTf, true, skipFeatureTransform = true)
                backward(predT, yTf); updateParameters()

                val xv2: Tensor = featureTransforms?.apply(xv) ?: xv
                val yvTensor = concatSequencesToTensor(yvList.toList())
                val yv2: Tensor = valueTransforms?.apply(yvTensor) ?: yvTensor
                val predV = predict(xv2, false, skipFeatureTransform = true)

                runningTrainLoss += lossFn(predT, yTf) * xTf.rows
                runningTestLoss += lossFn(predV, yv2) * xv2.rows
                testSamples += xv2.rows
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this) ?: (runningTestLoss / testSamples)

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

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

    // -------------------- Train (streaming, sparse targets) ----------------

    fun trainStreamingSparse(
        source: () -> Sequence<Pair<FloatArray, IntArray>>,
        batchSize: Int = 1024,
        maxEpochs: Int = 20,
        patience: Int = 5,
        testFrac: Float = 0.1f,
        shuffle: Boolean = true,
        gradAccumSteps: Int = 1,
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

            // NEW: per-accumulation (window) test-loss tracker
            var windowTestLoss = 0.0f
            var windowTestSamples = 0

            var windowRunningLoss = 0.0f
            var windowRunningSamples = 0

            var micro = 0
            var updates = 0

            fun flushAccumWindow(label: String) {
                // Average accumulated grads over the window, then update params
                scaleAccumulatedGradients(1f / micro)
                updateParameters()
                micro = 0
                updates += 1
                probeFn.invoke()

                // Print test loss for *the just-finished accumulation window*
                if (trace) {
                    if (windowTestSamples > 0) {
                        val winAvg = windowTestLoss / windowTestSamples
                        println("$label update $updates  test-loss(window) $winAvg")
                    } else if(windowRunningLoss > 0) {
                        val winAvg = windowRunningLoss / windowRunningSamples
                        println("$label update $updates  running-loss(window) $winAvg")
                    }
                    else {
                        println("$label update $updates  test-loss(window) NA (no test split in window)")
                    }
                }
                // reset window counters for the next accumulation window
                windowTestLoss = 0.0f
                windowTestSamples = 0

                windowRunningLoss = 0.0f
                windowRunningSamples = 0
            }

            for ((inputSeq, targetSeq) in source()) {
                bx += inputSeq; by += targetSeq
                if (bx.size == batchSize) {
                    val tokenSplitter = TokenBatchSplitter()
                    val (xTrainRaw, yTrainRaw, xTestRaw, yTestRaw) = tokenSplitter.splitBatch(
                        Tensor(bx.size, bx[0].size) { r, c -> bx[r][c] },
                        by.toTypedArray(), testFraction = testFrac, shuffle = shuffle
                    )

                    val xTrain: Tensor = featureTransforms?.fitAndTransform(xTrainRaw) ?: xTrainRaw
                    val yTrainFlat: IntArray = yTrainRaw.flatMap { it.toList() }.toIntArray()

                    val predTrain = predict(xTrain, isTraining = true, skipFeatureTransform = true)
                    backwardSparse(predTrain, yTrainFlat)
                    micro += 1

                    val xTest: Tensor = featureTransforms?.apply(xTestRaw) ?: xTestRaw
                    val yTestFlat: IntArray = yTestRaw.flatMap { it.toList() }.toIntArray()

                    val loss = lossFn(predTrain, yTrainFlat) * xTrain.rows

                    runningTrainLoss += loss
                    windowTestLoss += loss * xTrain.rows
                    windowTestSamples += xTrain.rows

                    if (xTest.rows > 0) {
                        val predTest = predict(xTest, isTraining = false, skipFeatureTransform = true)
                        val batchTestLoss = lossFn(predTest, yTestFlat)
                        runningTestLoss += batchTestLoss * xTest.rows
                        testSamples += xTest.rows

                        // track per-accumulation window loss
                        windowTestLoss += batchTestLoss * xTest.rows
                        windowTestSamples += xTest.rows
                    }

                    if (micro == gradAccumSteps) {
                        flushAccumWindow(label = "epoch $epoch")
                    }

                    bx.clear(); by.clear()
                }
            }

            // leftovers
            if (bx.isNotEmpty()) {
                val tokenSplitter = TokenBatchSplitter()
                val (xT, yT, xv, yv) = tokenSplitter.splitBatch(
                    Tensor(bx.size, bx[0].size) { r, c -> bx[r][c] },
                    by.toTypedArray(),
                    testFraction = testFrac, shuffle = shuffle
                )
                val xTf: Tensor = featureTransforms?.fitAndTransform(xT) ?: xT
                val yTfFlat = yT.flatMap { it.toList() }.toIntArray()
                val predT = predict(xTf, isTraining = true, skipFeatureTransform = true)
                backwardSparse(predT, yTfFlat)
                micro += 1

                val xv2: Tensor = featureTransforms?.apply(xv) ?: xv
                val yvFlat = yv.flatMap { it.toList() }.toIntArray()
                val predV = predict(xv2, false, skipFeatureTransform = true)

                val batchTrainLoss = lossFn(predT, yTfFlat)
                runningTrainLoss += batchTrainLoss * xTf.rows

                val batchTestLoss = lossFn(predV, yvFlat)
                runningTestLoss += batchTestLoss * xv2.rows
                testSamples += xv2.rows

                // track per-accumulation window loss
                windowTestLoss += batchTestLoss * xv2.rows
                windowTestSamples += xv2.rows
            }

            // ---- FINAL FLUSH (after leftovers) ----
            if (micro > 0) {
                flushAccumWindow(label = "epoch $epoch (final)")
            }

            val epochTestLoss = comprehensiveLossFn?.invoke(this)
                ?: if (testSamples > 0) runningTestLoss / testSamples else Float.NaN

            if (trace)
                println("epoch $epoch  test-loss $epochTestLoss")

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

    // -------------------- One-batch helper ---------------------------------

    private fun trainOnBatch(xRaw: Tensor, yRaw: Tensor) {
        val x: Tensor = featureTransforms?.fitAndTransform(xRaw) ?: xRaw
        val y: Tensor = valueTransforms?.fitAndTransform(yRaw) ?: yRaw
        val pred = predict(x, isTraining = true, skipFeatureTransform = true)
        backward(pred, y)
        updateParameters()
    }

    // -------------------- Clone / Save / Load -------------------------------

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
                    loadFromFile(path).also { println("✅ Loaded model from $path") }
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

    // ======================= Private helpers ===============================
    /** Gather arbitrary rows from [src] into a new Tensor (rows = indices.size). */
    private fun gatherRows(src: Tensor, indices: List<Int>): Tensor {
        if (indices.isEmpty()) return Tensor(0, src.cols)
        val out = Tensor(indices.size, src.cols)
        var r = 0
        while (r < indices.size) {
            out.copyRowFrom(src, indices[r], r)
            r++
        }
        return out
    }

    /** Flatten a list of (sequence → rows) into one Tensor. */
    private fun concatSequencesToTensor(seqs: List<Tensor>): Tensor {
        if (seqs.isEmpty()) return Tensor(0, 0)
        val cols = if (seqs[0].isEmpty()) 0 else seqs[0][0].size
        var totalRows = 0
        for (s in seqs) totalRows += s.size
        val out = Tensor(totalRows, cols)
        var r = 0
        for (s in seqs) {
            for (row in s) {
                var c = 0
                while (c < cols) {
                    out[r, c] = row[c]; c++
                }
                r++
            }
        }
        return out
    }

    /** Mean squared error between two tensors. */
    private fun meanStandardError(pred: Tensor, actual: Tensor): Float {
        require(pred.rows == actual.rows && pred.cols == actual.cols) {
            "MSE shape mismatch: pred=${pred.rows}x${pred.cols}, actual=${actual.rows}x${actual.cols}"
        }
        val n = pred.rows * pred.cols
        var i = 0
        var sum = 0.0f
        while (i < n) {
            val d = pred.data[i] - actual.data[i]
            sum += d * d
            i++
        }
        return sum / n
    }

    /** Sparse CE loss: mean over rows of -log softmax(row)[target]. */
    private fun sparseCategoricalCrossEntropy(logits: Tensor, targets: IntArray): Float {
        require(logits.rows == targets.size) {
            "Targets length (${targets.size}) must equal logits rows (${logits.rows})"
        }
        val R = logits.rows
        val C = logits.cols
        var loss = 0.0f
        var r = 0
        while (r < R) {
            val t = targets[r]
            require(t in 0 until C) { "Target id $t out of [0, $C)" }
            // log-softmax for row r
            var maxV = logits[r, 0]
            var c = 1
            while (c < C) {
                val v = logits[r, c]; if (v > maxV) maxV = v; c++
            }
            var sumExp = 0.0
            c = 0
            while (c < C) {
                sumExp += kotlin.math.exp((logits[r, c] - maxV).toDouble()); c++
            }
            val logProbT = (logits[r, t] - maxV) - ln(sumExp).toFloat()
            loss += (-logProbT)
            r++
        }
        return loss / R
    }

    /** d/dlogits of sparse CE (with softmax): probs - onehot(target). Optional per-row weights. */
    private fun sparseCategoricalCrossEntropyGradients(
        logits: Tensor,
        targets: IntArray,
        sampleWeights: FloatArray? = null
    ): Tensor {
        require(logits.rows == targets.size) {
            "Targets length (${targets.size}) must equal logits rows (${logits.rows})"
        }
        val probs = logits.softmaxRows()
        val out = Tensor(probs.rows, probs.cols)
        var r = 0
        while (r < probs.rows) {
            val t = targets[r]
            val w = sampleWeights?.getOrNull(r) ?: 1.0f
            var c = 0
            while (c < probs.cols) {
                val oneHot = if (c == t) 1.0f else 0.0f
                out[r, c] = (probs[r, c] - oneHot) * w
                c++
            }
            r++
        }
        return out
    }

    private fun scaleAccumulatedGradients(f: Float) {
        for (L in layers) {
            L.scaleAccumulatedGradients(f)
        }
    }

}
