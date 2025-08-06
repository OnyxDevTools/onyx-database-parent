import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.data.SparseSequenceGenerator
import com.onyxdevtools.ai.data.SequenceGenerator
import com.onyxdevtools.ai.extensions.sparseCategoricalCrossEntropy
import com.onyxdevtools.ai.generation.chat
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.loss.CrossEntropyLoss
import com.onyxdevtools.ai.loss.LossFunction
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.MutableVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.test.Test
import java.io.File
import kotlin.test.assertTrue

class LLMTrainingTest {

    @Test
    fun testLargerDataSetA() {
        // Load full text
        val fullText = File("src/test/resources/alice_full_packed.txt").readText()
        val qaText = File("src/test/resources/qa_alice.txt").readText()

        // Define or build vocabulary using the new function
        val vocabulary: Vocabulary = MutableVocabulary()
        vocabulary.appendToVocabulary(fullText)
        vocabulary.appendToVocabulary(qaText)
        vocabulary.commit(5000)

        val tokenizer = BPETokenizer(vocabulary)

        // Tokenize the entire text
        val tokens = tokenizer.tokenize(fullText).map { vocabulary.getId(it) }
        val qaTokens = tokenizer.tokenize(qaText).map { vocabulary.getId(it) }

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
            DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR) // ← Optimized output layer!
        )

        var model = NeuralNetwork(layers, learningRate = 0.001)

        // Source for streaming: generate sequences on-the-fly, shuffled per epoch (using sparse generator)
        val sequenceGenerator: SequenceGenerator = SparseSequenceGenerator(vocabulary)

        val mixedSource = {
            val a = sequenceGenerator.generateSequences(tokens, seqLength, stride, true)
            val b = sequenceGenerator.generateSequences(qaTokens, seqLength, stride, true)
            sequence {
                yieldAll(a)
                yieldAll(b)
            }
        }
        // Train the model using sparse streaming (memory efficient!)
        try {
            model = model.trainStreamingSparse(
                source = mixedSource,
                batchSize = 16,
                maxEpochs = 500,
                patience = 10,
                lossFn = { pred, sparseTargets -> sparseCategoricalCrossEntropy(pred, sparseTargets) },
                comprehensiveLossFn = { net ->
                    var generatedText = model.chat(
                        prompt = "[SOT]Alice was beginning to get very tired[SEP]",
                        vocabulary = vocabulary,
                        seqLength = seqLength
                    )
                    println("Generated text: $generatedText")

                    generatedText = model.chat(
                        prompt = "[SOT]Who are you?[SEP] ",
                        vocabulary = vocabulary,
                        seqLength = seqLength
                    )
                    println("Generated text: $generatedText")

                    val generatedAnswer = model.chat(
                        prompt = "[SOT]Who is the main character in the story?[SEP] ",
                        vocabulary = vocabulary,
                        seqLength = seqLength
                    )
                    println("Generated answer: $generatedAnswer")

                    // For comprehensive loss, compute on sparse validation set
                    val valIndices = (0 until tokens.size - seqLength step stride).take(100) // small val set
                    val valInputs = valIndices.map { i ->
                        tokens.subList(i, i + seqLength).map { it.toDouble() }.toDoubleArray()
                    }.toTypedArray()
                    val valSparseTargets = valIndices.flatMap { i ->
                        tokens.subList(i + 1, i + 1 + seqLength)
                    }.toIntArray()
                    val valPred = net.predict(valInputs)
                    sparseCategoricalCrossEntropy(valPred, valSparseTargets)
                }
            )
            println("Sparse streaming training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        val generatedText = model.chat(
            prompt = "[SOT]Alice was beginning to get very tired",
            vocabulary = vocabulary,
            seqLength = seqLength
        )
        println("Generated text: $generatedText")

        val generatedAnswer = model.chat(
            prompt = "Question: Who is the main character in the story? Answer:",
            vocabulary = vocabulary,
            seqLength = seqLength
        )
        println("Generated answer: $generatedAnswer")
    }

    @Test
    fun testSimpleLLMTraining() {
        // Define a small vocabulary
        val vocabulary: Vocabulary = MutableVocabulary().apply {
            addToken("hello")
            addToken("world")
            addToken("test")
            addToken("data")
            addToken("iduno")
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

        val lossFunction: LossFunction = CrossEntropyLoss()

        // Compute initial loss
        val initialPredictions = model.predict(inputSequences)
        val initialLoss = lossFunction.calculate(initialPredictions, targetSequences)

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
                    lossFunction.calculate(predictions, targetSequences)
                },
            )
            println("Training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }

        // Compute final loss
        val finalPredictions = model.predict(inputSequences)
        val finalLoss = lossFunction.calculate(finalPredictions, targetSequences)

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
}
