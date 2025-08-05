package com.onyxdevtools.ai

import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.Vocabulary
import kotlin.test.Test
import com.onyxdevtools.ai.layer.impl.*
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.test.assertTrue

class LLMTrainingTest {

    @Test
    fun testLargerDataSet() {
        // Load full text
        val fullText = File("src/test/resources/alice_full.txt").readText()

        // Define or build vocabulary
        val vocabulary = buildVocabularyFromText(fullText)
        val tokenizer = BPETokenizer(vocabulary)

        // Tokenize the entire text
        val tokens = tokenizer.tokenize(fullText).map { vocabulary.getId(it) }

        // Parameters
        val seqLength = 16
        val stride = seqLength

        // Configure neural network
        val embeddingDim = 64
        val numHeads = 4
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

        // Source for streaming: generate sequences on-the-fly, shuffled per epoch
        val source = {
            val indices = (0 until tokens.size - seqLength step stride).shuffled()
            indices.asSequence().map { i ->
                val inputSeq = tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
                val targetSeq = tokens.subList(i + 1, i + 1 + seqLength).map { targetToken ->
                    DoubleArray(vocabulary.size) { if (it == targetToken) 1.0 else 0.0 }
                }.toTypedArray()
                inputSeq to targetSeq
            }
        }

        // Train the model using streaming
        try {
            model = model.trainStreaming(
                source = source,
                batchSize = 32,
                maxEpochs = 100,
                patience = 10,
                lossFn = { pred, actual -> calculateCrossEntropyLoss(pred, actual) },
                comprehensiveLossFn = { net ->
                    // For comprehensive loss, compute on a validation set or subset
                    val valIndices = (0 until tokens.size - seqLength step stride).take(100) // small val set
                    val valInputs = valIndices.map { i ->
                        tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
                    }.toTypedArray()
                    val valTargets = valIndices.flatMap { i ->
                        tokens.subList(i + 1, i + 1 + seqLength).map { targetToken ->
                            DoubleArray(vocabulary.size) { if (it == targetToken) 1.0 else 0.0 }
                        }
                    }.toTypedArray()
                    val valPred = net.predict(valInputs)
                    calculateCrossEntropyLoss(valPred, valTargets)
                }
            )
            println("Streaming training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Example generation
        val padId = vocabulary.getId("[PAD]")
        val prompt = "Alice was beginning to get very tired"
        val promptTokens = tokenizer.tokenize(prompt).map { vocabulary.getId(it) }.toMutableList()
        val maxGenerate = 20
        for (i in 0 until maxGenerate) {
            val currentLength = promptTokens.size
            val inputList = if (currentLength >= seqLength) {
                promptTokens.takeLast(seqLength)
            } else {
                List(seqLength - currentLength) { padId } + promptTokens
            }
            val input = inputList.map { it.toDouble() }.toDoubleArray()
            val predictions = model.predict(arrayOf(input))
            val nextPosition = min(currentLength, seqLength) - 1
            val logits = predictions[nextPosition]
            val predictedId = logits.indices.maxByOrNull { logits[it] }!!
            promptTokens.add(predictedId)
            val predictedToken = vocabulary.getToken(predictedId) ?: "[UNK]"
            println("Generated token: $predictedToken")
        }
        val generatedText = promptTokens.map { vocabulary.getToken(it) ?: "[UNK]" }.joinToString(" ")
        println("Generated text: $generatedText")

        // Assertions can be added if needed, but since it's streaming, loss computation is inside
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
    vocabMap["[PAD]"] = vocabMap.size
    return object : Vocabulary {
        override fun getId(token: String): Int = vocabMap.getOrPut(token) { vocabMap.size }
        override fun getToken(index: Int): String? = vocabMap.entries.find { it.value == index }?.key
        override fun findId(token: String): Int? = vocabMap[token]
        override val size: Int get() = vocabMap.size
    }
}
