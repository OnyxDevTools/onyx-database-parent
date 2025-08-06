import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.data.DefaultSequenceGenerator
import com.onyxdevtools.ai.extensions.sparseCategoricalCrossEntropy
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.random.Random
import java.io.File

fun generateCorpusSequence(
    booksDir: File,
    vocabulary: Vocabulary,
    seqLength: Int,
    stride: Int,
    shuffleFiles: Boolean = true,
    rng: Random? = null,
    shuffleWithinFile: Boolean = false
): Sequence<Pair<DoubleArray, IntArray>> {
    require(seqLength > 0) { "seqLength must be > 0" }
    require(stride > 0) { "stride must be > 0" }

    val tokenizer = BPETokenizer(vocabulary)
    val generator = DefaultSequenceGenerator(vocabulary)
    val all = booksDir.listFiles()?.filter { it.isFile && it.extension.contains("txt") } ?: emptyList()
    val files = if (shuffleFiles) (rng?.let { all.shuffled(it) } ?: all.shuffled()) else all

    return sequence {
        for (f in files) {
            val tokens = mutableListOf<Int>()
            f.forEachLine { line ->
                    for (tok in tokenizer.tokenize(line)) {
                    tokens.add(vocabulary.getId(tok))
                }
                tokens.add(vocabulary.getId("\n"))
            }
            val seqs = generator.generateSequences(tokens, seqLength, stride, shuffle = shuffleWithinFile)
            for (pair in seqs) {
                yield(pair)
            }
        }
    }
}

class ComprehensiveLossFunction(
    private val booksDir: File,
    private val vocabulary: Vocabulary,
    private val seqLength: Int,
    private val stride: Int,
    private val numValExamples: Int = 10,
    private val shuffleForVal: Boolean = false
) : (NeuralNetwork) -> Double {
    override fun invoke(net: NeuralNetwork): Double {
        val valSeq = generateCorpusSequence(
            booksDir,
            vocabulary,
            seqLength,
            stride,
            shuffleFiles = shuffleForVal,
            rng = if (shuffleForVal) Random(42) else null
        )
        val valPairs = valSeq.take(numValExamples).toList()
        if (valPairs.isEmpty()) return 0.0

        val valInputs = valPairs.map { it.first }.toTypedArray()
        val valSparseTargets = valPairs.flatMap { it.second.toList() }.toIntArray()
        val valPred = net.predict(valInputs)
        return sparseCategoricalCrossEntropy(valPred, valSparseTargets)
    }
}

fun main() {
    val books = File("/mnt/onyx/books/gutenberg_books")
    val vocabulary = OnyxVocabulary("/mnt/onyx/books/vocabulary.dat")

    if (vocabulary.size == 0) {
        books.listFiles()?.forEach {
            vocabulary.appendToVocabulary(it.readText())
        }
        vocabulary.commit()
    }

    // Parameters
    val maxSequenceLength = 512
    val stride = maxSequenceLength

    // Configure neural network
    val embeddingDim = maxSequenceLength
    val numHeads = 4
    val ffHiddenDim = 64

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

    // Create corpus sequence generator (streams all books)
    val source = {
        generateCorpusSequence(
            books,
            vocabulary,
            maxSequenceLength,
            stride,
            shuffleFiles = true,
            rng = Random(42)
        )
    }

    // Create comprehensive loss function instance
    val comprehensiveLossFn = ComprehensiveLossFunction(books, vocabulary, maxSequenceLength, stride)

    // Train the model using sparse streaming training (memory efficient!)
    try {
        model = model.trainStreamingSparse(
            source = source,
            batchSize = 32,
            maxEpochs = 100,
            patience = 10,
            lossFn = { pred, sparseTargets -> sparseCategoricalCrossEntropy(pred, sparseTargets) },
            comprehensiveLossFn = comprehensiveLossFn,
            saveModelPath = "/mnt/onyx/books/model.ser"
        )
        println("Sparse streaming training completed successfully")
    } catch (e: Exception) {
        println("Training failed with exception: ${e.message}")
        e.printStackTrace()
    }
}
