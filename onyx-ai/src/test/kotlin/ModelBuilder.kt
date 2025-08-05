import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.loss.CrossEntropyLoss
import com.onyxdevtools.ai.loss.LossFunction
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import java.io.BufferedReader
import java.io.File
import java.io.ObjectOutputStream
import kotlin.random.Random

fun generateCorpusSequencesStreaming(
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
    val files0 = booksDir.listFiles()?.filter { it.isFile } ?: emptyList()
    val files = if (shuffleFiles) (rng?.let { files0.shuffled(it) } ?: files0.shuffled()) else files0

    return Sequence {
        object : Iterator<Pair<DoubleArray, Array<DoubleArray>>> {
            private var fileIdx = 0
            private var reader: BufferedReader? = null
            private var nextItem: Pair<DoubleArray, Array<DoubleArray>>? = null
            private var eof = false

            // ring buffer windowing
            private val ring = IntArray(seqLength)
            private var cursor = 0
            private var seen = 0L
            private var nextEmitAt = seqLength.toLong()

            private fun openNextFile() {
                reader?.close()
                while (fileIdx < files.size) {
                    val f = files[fileIdx++]
                    if (f.isFile) {
                        reader = f.bufferedReader()
                        println("Streaming ${f.name}")
                        return
                    }
                }
                eof = true
            }

            private fun feed(id: Int) {
                ring[cursor] = id
                cursor = (cursor + 1) % seqLength
                seen++
                if (seen >= nextEmitAt && nextItem == null) {
                    val start = cursor
                    val window = IntArray(seqLength) { k -> ring[(start + k) % seqLength] }
                    nextItem = buildExample(window)
                    nextEmitAt += stride
                }
            }

            private fun fillNext() {
                while (nextItem == null && !eof) {
                    val r = reader ?: run { openNextFile(); reader ?: return }
                    val line = r.readLine()
                    if (line == null) {
                        r.close()
                        reader = null
                        continue
                    }
                    // tokenize per line; stays flat on memory
                    val toks = tokenizer.tokenize(line)
                    for (t in toks) {
                        feed(vocabulary.getId(t))
                        if (nextItem != null) return
                    }
                    // keep a notion of boundaries
                    feed(vocabulary.getId("\n"))
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

fun main() {
    val maxSequenceLength = 1024
    val books = File("/Volumes/onyx/books/gutenberg_books")
    val vocabulary = OnyxVocabulary("/Users/tosborn/Desktop/model/vocabulary.dat")


    // Parameters
    val stride = maxSequenceLength
    val embeddingDim = 64
    val numHeads = 16
    val ffHiddenDim = 1024

    val layers = listOf(
        EmbeddingLayer(vocabulary.size, embeddingDim),
        PositionalEncodingLayer(maxSequenceLength, embeddingDim),
        MultiHeadAttentionLayer(maxSequenceLength, embeddingDim, numHeads),
        LayerNormalizationLayer(embeddingDim),
        DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
        DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
        LayerNormalizationLayer(embeddingDim),
        DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
    )

    var model = NeuralNetwork(layers, learningRate = 0.001)

    val lossFunction: LossFunction = CrossEntropyLoss()

    // Train the model using streaming with checkpointing on improved comprehensive score
    var bestComprehensiveLoss = Double.POSITIVE_INFINITY
    try {
        model = model.trainStreaming(
            source = {
                generateCorpusSequencesStreaming(
                    booksDir = books,
                    vocabulary = vocabulary,
                    seqLength = maxSequenceLength,
                    stride = 1,
                    shuffleFiles = true,
                    rng = Random(42),
                    buildExample = ::buildExampleFromIds
                )
            },
            batchSize = 30000,
            maxEpochs = 500,
            patience = 10,
            lossFn = { pred, actual -> lossFunction.calculate(pred, actual) },
            comprehensiveLossFn = { net ->
                val corpus = generateCorpusSequencesStreaming(
                    booksDir = books,
                    vocabulary = vocabulary,
                    seqLength = maxSequenceLength,
                    stride = 1,
                    shuffleFiles = true,
                    rng = Random(42),
                    buildExample = ::buildExampleFromIds
                )
                val score = corpus.sumOf {
                    val tokens = it.first.toList()
                    val valIndices = (0 until tokens.size - maxSequenceLength step stride).take(100)
                    val valInputs = valIndices.map { i ->
                        tokens.subList(i, i + maxSequenceLength).toDoubleArray()
                    }.toTypedArray()
                    val valTargets = valIndices.flatMap { i ->
                        tokens.subList(i + 1, i + 1 + maxSequenceLength).map { targetToken ->
                            DoubleArray(vocabulary.size) { if (it == targetToken.toInt()) 1.0 else 0.0 }
                        }
                    }.toTypedArray()
                    val valPred = net.predict(valInputs)
                    lossFunction.calculate(valPred, valTargets)
                }
                if (score < bestComprehensiveLoss) {
                    bestComprehensiveLoss = score
                    File("best_model.ser").outputStream().use { os ->
                        ObjectOutputStream(os).writeObject(net)
                    }
                }
                score
            }
        )
        println("Streaming training completed successfully")
    } catch (e: Exception) {
        println("Training failed with exception: ${e.message}")
        e.printStackTrace()
    }
}

private fun buildExampleFromIds(ids: IntArray): Pair<DoubleArray, Array<DoubleArray>> {
    val x = DoubleArray(ids.size) { ids[it].toDouble() }
    val y = arrayOf(DoubleArray(ids.size) { 0.0 }) // placeholder
    return x to y
}
