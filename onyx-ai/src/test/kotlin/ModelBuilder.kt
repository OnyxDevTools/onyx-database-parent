import Activation
import com.onyxdevtools.ai.*
import com.onyxdevtools.ai.data.DefaultSequenceGenerator
import com.onyxdevtools.ai.extensions.sparseCategoricalCrossEntropy
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.generation.chat
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.random.Random
import java.io.File
import java.io.FileWriter

// Add somewhere above train call
fun askProbes(model: NeuralNetwork, vocab: Vocabulary, seqLen: Int) {
    val qs = listOf(
        "[SOT]What are you?[SEP] ",
        "[SOT]Who is Alice?[SEP] ",
        "[SOT]Who is the White Rabbit?[SEP] ",
        "[SOT]Where is Wonderland?[SEP] ",
        "[SOT]What game does the Queen of Hearts play?[SEP] ",
        "[SOT]Alice was beginning to get very tired "
    )

    val logFile = File("log.txt")
    FileWriter(logFile, true).use { writer ->

        qs.forEach { q ->
            val out = model.chat(
                prompt = q,
                vocabulary = vocab,
                seqLength = seqLen,
                maxTokens = 5,
            )
            println(out)
            writer.append(out + "\n")
        }
    }
}

fun generateCorpusSequence(
    booksDir: File,
    vocabulary: Vocabulary,
    seqLength: Int,
    stride: Int,
    shuffleFiles: Boolean = true,
    rng: Random? = null,
    shuffleWithinFile: Boolean = false
): Sequence<Pair<FlexibleMatrix, IntArray>> {
    require(seqLength > 0) { "seqLength must be > 0" }
    require(stride > 0) { "stride must be > 0" }

    val tokenizer = BPETokenizer(vocabulary)
    val generator = DefaultSequenceGenerator(vocabulary)
    val all = booksDir.listFiles()?.filter { !it.isHidden && it.isFile && it.extension.contains("txt") } ?: emptyList()
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

        val valInputsList = valPairs.map { it.first }
        val valSparseTargets = valPairs.flatMap { it.second.toList() }.toIntArray()
        
        // Stack all FlexibleMatrix vertically to create a single input matrix
        val valInputs = if (valInputsList.isNotEmpty()) {
            val totalRows = valInputsList.map { it.rows }.sum()
            val cols = valInputsList[0].cols
            val isSingle = valInputsList[0].isSinglePrecision
            
            createMatrix(totalRows, cols, isSingle) { r, c ->
                var currentRowOffset = 0
                for (matrix in valInputsList) {
                    if (r >= currentRowOffset && r < currentRowOffset + matrix.rows) {
                        return@createMatrix matrix[r - currentRowOffset, c]
                    }
                    currentRowOffset += matrix.rows
                }
                0.0
            }
        } else {
            createMatrix(0, 0, true)
        }
        
        val valPred = net.predict(valInputs)
        return valPred.sparseCategoricalCrossEntropy(valSparseTargets)
    }
}

fun main() {
    val books = File("/Volumes/onyx/books/formatted_books")
    val vocabulary = OnyxVocabulary("/Users/tosborn/Desktop/onyx/books/vocabulary_1.dat")

    if (vocabulary.size == 0) {
        books.listFiles()?.forEach {
            if (it.isHidden) return@forEach
            vocabulary.appendToVocabulary(it.readText())
        }
        vocabulary.commit()
    }

    // Parameters
    val maxSequenceLength = 512
    val stride = maxSequenceLength

    // Configure neural network
    val embeddingDim = maxSequenceLength
    val numHeads = 8
    val ffHiddenDim = 64
    val precision = MatrixPrecision.SINGLE

    val layers = listOf(
        EmbeddingLayer(vocabulary.size, embeddingDim, precision = precision),
        PositionalEncodingLayer(maxSequenceLength, embeddingDim, precision = precision),
        MultiHeadAttentionLayer(maxSequenceLength, embeddingDim, numHeads, precision = precision),
        LayerNormalizationLayer(embeddingDim, precision = precision),
        DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU, precision = precision),
        DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR, precision = precision),
        LayerNormalizationLayer(embeddingDim, precision = precision),
        DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR, precision = precision)
    )

    var model = NeuralNetwork(layers, learningRate = 0.001, precision = precision)

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
            batchSize = 16,
            maxEpochs = 200,
            patience = 100,
            lossFn = { pred, sparseTargets -> pred.sparseCategoricalCrossEntropy(sparseTargets) },
            probeFn = {
                askProbes(model, vocabulary, maxSequenceLength)
            },
            comprehensiveLossFn = comprehensiveLossFn,
            saveModelPath = "/mnt/onyx/books/model.ser"
        )
        println("Sparse streaming training completed successfully")
    } catch (e: Exception) {
        println("Training failed with exception: ${e.message}")
        e.printStackTrace()
    }
}
