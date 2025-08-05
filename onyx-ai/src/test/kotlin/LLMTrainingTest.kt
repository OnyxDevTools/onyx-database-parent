package com.onyxdevtools.ai

import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
import kotlin.test.Test
import com.onyxdevtools.ai.layer.impl.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.test.assertTrue

class LLMTrainingTest {

    @Test
    fun testLargerDataSet() {
        // Define or build vocabulary - for real use, build from the corpus
        val vocabulary = buildVocabularyFromText(aliceText)
        val tokenizer = BPETokenizer(vocabulary)

        // Tokenize the entire text
        val tokens = tokenizer.tokenize(aliceText).map { vocabulary.getId(it) }

        // Parameters
        val seqLength = 2  // Desired sequence length (tokensPerSample in your layers)
        val stride = seqLength  // Non-overlapping for simplicity; use smaller for overlap

        // Create input-target sequences
        val inputSequences = mutableListOf<DoubleArray>()
        val targetSequences = mutableListOf<DoubleArray>()  // For one-hot targets per position

        for (i in 0 until tokens.size - seqLength step stride) {
            val inputSeq = tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
            inputSequences.add(inputSeq)

            // For each position, target is the next token (shifted)
            // But since output is (batch*seq, vocab), targets need to match
            // Here we create one-hot for each target position
            val targetSeq = tokens.subList(i + 1, (i + seqLength + 1).coerceAtMost(tokens.size))
            targetSeq.forEach { targetToken ->
                val oneHot = DoubleArray(vocabulary.size) { 0.0 }
                oneHot[targetToken] = 1.0
                targetSequences.add(oneHot)
            }
        }

        // Now inputSequences is List<DoubleArray> where each is seqLength
        // targetSequences is flattened to match model's output shape (batchSize * seqLength, vocabSize)

        // Configure neural network with tokensPerSample = seqLength
        val embeddingDim = 64  // Increased for larger data
        val numHeads = 4  // Increased for better attention
        val ffHiddenDim = 128
        val tokensPerSample = seqLength

        val layers = listOf(
            EmbeddingLayer(vocabulary.size, embeddingDim),
            PositionalEncodingLayer(tokensPerSample, embeddingDim),
            MultiHeadAttentionLayer(tokensPerSample, embeddingDim, numHeads),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
            DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
        )

        var model = NeuralNetwork(layers, learningRate = 0.001)

        // Compute initial loss
        val initialPredictions = model.predict(inputSequences.toTypedArray())
        val initialLoss = calculateCrossEntropyLoss(initialPredictions, targetSequences.toTypedArray())

        // Train the model
        try {
            model = model.train(
                trainingFeatures = inputSequences.toTypedArray(),
                trainingValues = targetSequences.toTypedArray(),
                maxEpochs = 1000,  // Reduced for test speed; increase for real training
                patience = 100,
                batchSize = inputSequences.size, // Use full batch for simplicity
                lossFn = { net ->
                    val predictions = net.predict(inputSequences.toTypedArray())
                    calculateCrossEntropyLoss(predictions, targetSequences.toTypedArray())
                }
            )
            println("Training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Compute final loss
        val finalPredictions = model.predict(inputSequences.toTypedArray())
        val finalLoss = calculateCrossEntropyLoss(finalPredictions, targetSequences.toTypedArray())

        // Test that the model can at least make some predictions and loss is finite
        assertTrue(initialLoss.isFinite(), "Initial loss should be finite")
        assertTrue(finalLoss.isFinite(), "Final loss should be finite")

        println("Initial Loss: $initialLoss")
        println("Final Loss: $finalLoss")

        // Show sample predictions (for the last position in each sequence as an example)
        val numSeq = inputSequences.size
        finalPredictions.withIndex().groupBy { it.index / seqLength }.forEach { (seqIdx, preds) ->
            if (seqIdx < 5) {  // Show first 5 sequences
                val lastPred = preds.last().value  // Last position in sequence
                val predictedToken = lastPred.indices.maxByOrNull { lastPred[it] } ?: 0
                val actualToken = targetSequences[seqIdx * seqLength + (seqLength - 1)].indices.indexOfFirst { it.toDouble() == 1.0 }
                println("Sequence $seqIdx last input token: ${inputSequences[seqIdx].last().toInt()} -> Predicted: $predictedToken, Actual: $actualToken")
            }
        }

        // Test passes if loss improved
        assertTrue(finalLoss < initialLoss, "Model training completed successfully with loss improvement")
    }

    @Test
    fun testSimpleLLMTraining() {
        // Define a small vocabulary
        val vocab = mutableMapOf<String, Int>()
        vocab["hello"] = 0
        vocab["world"] = 1
        vocab["test"] = 2
        vocab["data"] = 3
        vocab["iduno"] = 4
        val vocabulary = object : Vocabulary {
            override fun getId(token: String): Int = vocab.getOrPut(token) { vocab.size }
            override fun getToken(index: Int): String? = vocab.entries.find { it.value == index }?.key
            override fun findId(token: String): Int? = vocab[token]
            override val size: Int get() = vocab.size
        }
        val tokenizer = BPETokenizer(vocabulary)

        // Create training samples where each input is a single token predicting the next
        val sampleTexts = listOf("hello world", "test data", "hello test", "world data")
        val trainingInputs = mutableListOf<DoubleArray>()
        val trainingTargets = mutableListOf<DoubleArray>()

        sampleTexts.forEach { text ->
            val tokens = tokenizer.tokenize(text).map { vocabulary.getId(it) }
            for (i in 0 until tokens.size - 1) {
                trainingInputs.add(doubleArrayOf(tokens[i].toDouble()))
                val targetOneHot = DoubleArray(vocabulary.size) { 0.0 }
                targetOneHot[tokens[i + 1]] = 1.0
                trainingTargets.add(targetOneHot)
            }
        }

        val inputSequences = trainingInputs.toTypedArray()
        val targetSequences = trainingTargets.toTypedArray()

        // Configure neural network with tokensPerSample=1
        val embeddingDim = 8
        val numHeads = 2
        val ffHiddenDim = 32
        val tokensPerSample = 1

        val layers = listOf(
            EmbeddingLayer(vocabulary.size, embeddingDim),
            PositionalEncodingLayer(tokensPerSample, embeddingDim),
            MultiHeadAttentionLayer(tokensPerSample, embeddingDim, numHeads),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
            DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
            LayerNormalizationLayer(embeddingDim),
            DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
        )

        var model = NeuralNetwork(layers, learningRate = 0.001)

        // Compute initial loss
        val initialPredictions = model.predict(inputSequences)
        val initialLoss = calculateCrossEntropyLoss(initialPredictions, targetSequences)

        // Train the model
        try {
            model = model.train(
                trainingFeatures = inputSequences,
                trainingValues = targetSequences,
                maxEpochs = 50000,
                patience = 1000,
                batchSize = inputSequences.size, // Use full batch
                lossFn = { net ->
                    val predictions = net.predict(inputSequences)
                    calculateCrossEntropyLoss(predictions, targetSequences)
                }
            )
            println("Training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Compute final loss
        val finalPredictions = model.predict(inputSequences)
        val finalLoss = calculateCrossEntropyLoss(finalPredictions, targetSequences)

        // Test that the model can at least make some predictions and loss is finite
        assertTrue(initialLoss.isFinite(), "Initial loss should be finite")
        assertTrue(finalLoss.isFinite(), "Final loss should be finite")

        println("Initial Loss: $initialLoss")
        println("Final Loss: $finalLoss")

        // Show sample predictions
        inputSequences.forEachIndexed { idx, input ->
            val prediction = model.predict(arrayOf(input))[0]
            val predictedToken = prediction.indices.maxByOrNull { prediction[it] } ?: 0
            val actualToken = targetSequences[idx].indices.indexOfFirst { targetSequences[idx][it] == 1.0 }
            println("Input: ${input[0].toInt()} -> Predicted: $predictedToken, Actual: $actualToken")
        }

        // Test passes if loss improved
        assertTrue(finalLoss < initialLoss, "Model training completed successfully with loss improvement")
    }

    private fun calculateCrossEntropyLoss(predictions: Array<DoubleArray>, targets: Array<DoubleArray>): Double {
        var loss = 0.0
        val batchSize = targets.size

        for (batchIndex in 0 until batchSize) {
            val logits = predictions[batchIndex]
            val expSum = logits.sumOf { exp(it) }
            val probs = logits.map { exp(it) / expSum }.toDoubleArray()

            val targetIndex = targets[batchIndex].indexOfFirst { it == 1.0 }
            if (targetIndex >= 0 && targetIndex < probs.size) {
                loss -= ln(probs[targetIndex] + 1e-10)
            }
        }
        return loss / batchSize
    }
}

// Simple vocabulary builder - in real BPE, use proper merges and train on corpus
fun buildVocabularyFromText(text: String): Vocabulary {
    val words = text.split(Regex("\\s+")).toSet()
    val vocabMap = words.mapIndexed { index, word -> word to index }.toMap().toMutableMap()
    return object : Vocabulary {
        override fun getId(token: String): Int = vocabMap.getOrPut(token) { vocabMap.size }
        override fun getToken(index: Int): String? = vocabMap.entries.find { it.value == index }?.key
        override fun findId(token: String): Int? = vocabMap[token]
        override val size: Int get() = vocabMap.size
    }
}

val aliceText = """
CHAPTER I.
Down the Rabbit-Hole

Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it, “and what is the use of a book,” thought Alice “without pictures or conversations?”

So she was considering in her own mind (as well as she could, for the hot day made her feel very sleepy and stupid), whether the pleasure of making a daisy-chain would be worth the trouble of getting up and picking the daisies, when suddenly a White Rabbit with pink eyes ran close by her.

There was nothing so very remarkable in that; nor did Alice think it so very much out of the way to hear the Rabbit say to itself, “Oh dear! Oh dear! I shall be late!” (when she thought it over afterwards, it occurred to her that she ought to have wondered at this, but at the time it all seemed quite natural); but when the Rabbit actually took a watch out of its waistcoat-pocket, and looked at it, and then hurried on, Alice started to her feet, for it flashed across her mind that she had never before seen a rabbit with either a waistcoat-pocket, or a watch to take out of it, and burning with curiosity, she ran across the field after it, and fortunately was just in time to see it pop down a large rabbit-hole under the hedge.

In another moment down went Alice after it, never once considering how in the world she was to get out again.

The rabbit-hole went straight on like a tunnel for some way, and then dipped suddenly down, so suddenly that Alice had not a moment to think about stopping herself before she found herself falling down a very deep well.

Either the well was very deep, or she fell very slowly, for she had plenty of time as she went down to look about her and to wonder what was going to happen next. First, she tried to look down and make out what she was coming to, but it was too dark to see anything; then she looked at the sides of the well, and noticed that they were filled with cupboards and book-shelves; here and there she saw maps and pictures hung upon pegs. She took down a jar from one of the shelves as she passed; it was labelled “ORANGE MARMALADE”, but to her great disappointment it was empty: she did not like to drop the jar for fear of killing somebody underneath, so managed to put it into one of the cupboards as she fell past it.

"""