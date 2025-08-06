import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.data.DefaultSequenceGenerator
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.loss.CrossEntropyLoss
import com.onyxdevtools.ai.loss.LossFunction
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import java.io.ObjectOutputStream
import kotlin.random.Random
import java.io.BufferedReader
import java.io.File
import java.util.ArrayDeque

fun generateCorpusSequence(
    booksDir: File,
    vocabulary: Vocabulary,
    seqLength: Int,
    stride: Int,
    shuffleFiles: Boolean = true,
    rng: Random? = null,
    buildExample: (IntArray) -> Pair<DoubleArray, Array<DoubleArray>>
): Sequence<Pair<DoubleArray, Array<DoubleArray>>> {
    require(seqLength > 0) { "seqLength must be > 0" }
    require(stride > 0) { "stride must be > 0" }

    val tokenizer = BPETokenizer(vocabulary)
    val all = booksDir.listFiles()?.filter { it.isFile && it.extension.contains("txt") } ?: emptyList()
    val files = if (shuffleFiles) (rng?.let { all.shuffled(it) } ?: all.shuffled()) else all

    return Sequence {
        object : Iterator<Pair<DoubleArray, Array<DoubleArray>>> {
            private var fileIdx = 0
            private var reader: BufferedReader? = null
            private val buf = ArrayDeque<Int>(seqLength * 2)
            private var nextItem: Pair<DoubleArray, Array<DoubleArray>>? = null
            private var exhausted = false

            private fun openNextFile() {
                reader?.close()
                if (fileIdx >= files.size) { exhausted = true; return }
                val f = files[fileIdx++]
                reader = f.bufferedReader()
                // println("Streaming ${f.name}")
            }

            private fun tryEmit() {
                if (nextItem != null || buf.size < seqLength) return
                // materialize current window
                val window = IntArray(seqLength)
                var i = 0
                for (it in buf) {
                    window[i++] = it
                    if (i == seqLength) break
                }
                nextItem = buildExample(window)

                // advance by stride
                repeat(minOf(stride, buf.size)) { buf.removeFirst() }
            }

            private fun fillNext() {
                while (nextItem == null && !exhausted) {
                    val r = reader ?: run { openNextFile(); reader ?: return }
                    val line = r.readLine()
                    if (line == null) {
                        r.close(); reader = null
                        continue
                    }
                    // tokenize this line and push ids
                    for (tok in tokenizer.tokenize(line)) {
                        buf.addLast(vocabulary.getId(tok))
                        tryEmit()
                        if (nextItem != null) return
                    }
                    // keep simple boundary token (optional)
                    buf.addLast(vocabulary.getId("\n"))
                    tryEmit()
                }
            }

            override fun hasNext(): Boolean {
                if (nextItem == null) fillNext()
                return nextItem != null
            }

            override fun next(): Pair<DoubleArray, Array<DoubleArray>> {
                if (!hasNext()) throw NoSuchElementException()
                val out = nextItem!!
                nextItem = null
                return out
            }
        }
    }
}

fun generate(file: File, vocabulary: Vocabulary, seqLength: Int, stride: Int): Sequence<Pair<DoubleArray, Array<DoubleArray>>> {
    val text = file.readText()
    val tokenizer = BPETokenizer(vocabulary)
    val tokens = tokenizer.tokenize(text).map { vocabulary.getId(it) }
    val sequenceGenerator = DefaultSequenceGenerator(vocabulary)
    return sequenceGenerator.generateSequences(tokens, seqLength, stride, true)
}

fun main() {
    val books = File("/Volumes/onyx/books/gutenberg_books")
    val vocabulary = OnyxVocabulary("/Users/tosborn/Desktop/model/vocabulary.dat")

    if (vocabulary.size == 0) {
        books.listFiles()?.forEach {
            vocabulary.appendToVocabulary(it.readText())
        }
    }

    // Parameters
    val maxSequenceLength = 1024
    val stride = maxSequenceLength

    // Configure neural network
    val embeddingDim = maxSequenceLength
    val numHeads = 4
    val ffHiddenDim = 64
    val tokensPerSample = maxSequenceLength

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

    books.listFiles()?.forEach { file ->

        val sequenceGenerator = DefaultSequenceGenerator(vocabulary)
        val text = file.readText()
        val tokenizer = BPETokenizer(vocabulary)
        val tokens = tokenizer.tokenize(text).map { vocabulary.getId(it) }
        val source = { sequenceGenerator.generateSequences(tokens, maxSequenceLength, stride, true) }

        println(file.name)

        val lossFunction: LossFunction = CrossEntropyLoss()

        // Train the model using streaming with checkpointing on improved comprehensive score
        var bestComprehensiveLoss = Double.POSITIVE_INFINITY
        try {
            model = model.trainStreaming(
                source = source,//{
//                    generate(file, vocabulary, maxSequenceLength, stride)
//                    generateCorpusSequence(
//                        booksDir = books,
//                        vocabulary = vocabulary,
//                        seqLength = maxSequenceLength,
//                        stride = stride,
//                        shuffleFiles = true,
//                        rng = Random(42),
//                        buildExample = { ids -> buildExampleFromIds(ids, vocabulary.size) }
//                    )
//                },
                batchSize = 16,
                maxEpochs = 500,
                patience = 10,
                lossFn = { pred, actual -> lossFunction.calculate(pred, actual) },
                comprehensiveLossFn = { net ->
                    val valIndices = (0 until tokens.size - maxSequenceLength step stride).take(100) // small val set
                    val valInputs = valIndices.map { i ->
                        tokens.subList(i, i + maxSequenceLength).map { it.toDouble() }.toDoubleArray()
                    }.toTypedArray()
                    val valTargets = valIndices.flatMap { i ->
                        tokens.subList(i + 1, i + 1 + maxSequenceLength).map { targetToken ->
                            DoubleArray(vocabulary.size) { if (it == targetToken) 1.0 else 0.0 }
                        }
                    }.toTypedArray()
                    val valPred = net.predict(valInputs)
                    lossFunction.calculate(valPred, valTargets)
                }
            )
            println("Streaming training completed successfully")
        } catch (e: Exception) {
            println("Training failed with exception: ${e.message}")
            e.printStackTrace()
        }
    }
}

private fun buildExampleFromIds(ids: IntArray, vocabSize: Int): Pair<DoubleArray, Array<DoubleArray>> {
    val seqLength = ids.size - 1
    val x = DoubleArray(seqLength) { ids[it].toDouble() }
    val y = Array(seqLength) { i ->
        val target = ids[i + 1]
        DoubleArray(vocabSize) { if (it == target) 1.0 else 0.0 }
    }
    return x to y
}
