import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.data.DefaultSequenceGenerator
import com.onyxdevtools.ai.extensions.sparseCategoricalCrossEntropy
import com.onyxdevtools.ai.generation.chat
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.random.Random
import java.io.File

// Add somewhere above train call
fun askProbes(model: NeuralNetwork, vocab: Vocabulary, seqLen: Int) {
    val qs = listOf(
        "What are you?",
        "Who is Alice?",
        "Who is the White Rabbit?",
        "Where is Wonderland?",
        "What game does the Queen of Hearts play?",
        "Can you write a hello world program?",
    )
    qs.forEach { q ->
        val out = model.chat(
            prompt = q,
            vocabulary = vocab,
            seqLength = seqLen
        )
        println(out)
    }
}

// ---------------------------------------------------------------
// Helper that returns a *factory* – i.e. a lambda that builds a
// brand‑new Sequence each time it is invoked.
// ---------------------------------------------------------------
fun makeCorpusSource(
    booksDir: File,
    vocab: Vocabulary,
    seqLen: Int,
    stride: Int,
    epochSeed: Long            // different for every epoch
): () -> Sequence<Pair<FloatArray, IntArray>> = {
    generateCorpusSequence(
        booksDir,
        vocab,
        seqLen,
        stride,
        shuffleFiles = true,
        rng = Random(epochSeed),          // <-- NEW: seed changes each epoch
        shuffleWithinFile = true,         // <-- enable full shuffling
        randomStartOffset = true
    )
}

fun generateCorpusSequence(
    booksDir: File,
    vocabulary: Vocabulary,
    seqLength: Int,
    stride: Int,
    shuffleFiles: Boolean = true,
    rng: Random? = null,
    shuffleWithinFile: Boolean = false,
    randomStartOffset: Boolean = false
): Sequence<Pair<FloatArray, IntArray>> {

    require(seqLength > 0) { "seqLength must be > 0" }
    require(stride > 0)   { "stride must be > 0" }
    require(stride <= seqLength) { "stride may not be larger than seqLength" }

    val tokenizer = BPETokenizer(vocabulary)

    // -----------------------------------------------------------------
    // 1️⃣  Determine the file order for this *epoch*
    // -----------------------------------------------------------------
    val allFiles = booksDir.listFiles()
        ?.filter { it.isFile && it.extension.contains("txt") }
        ?: emptyList()

    val files = if (shuffleFiles) {
        (rng?.let { allFiles.shuffled(it) } ?: allFiles.shuffled())
    } else allFiles

    // -----------------------------------------------------------------
    // 2️⃣  The mutable buffer that is kept *across* file boundaries
    // -----------------------------------------------------------------
    val buffer = mutableListOf<Int>()

    // -----------------------------------------------------------------
    // 3️⃣  Build the lazy sequence
    // -----------------------------------------------------------------
    return sequence {
        for (file in files) {
            // ---------------------------------------------------------
            // 3a) Load the whole file into a token list
            // ---------------------------------------------------------
            val fileTokens = mutableListOf<Int>()
            file.forEachLine { line ->
                for (tok in tokenizer.tokenize(line)) {
                    fileTokens.add(vocabulary.getId(tok))
                }
                fileTokens.add(vocabulary.getId("\n"))
            }

            // ---------------------------------------------------------
            // 3b) Optional per‑file shuffling
            // ---------------------------------------------------------
            val shuffledFileTokens = if (shuffleWithinFile) {
                val list = fileTokens.toMutableList()
                val r = rng ?: Random.Default
                for (i in list.indices.reversed()) {
                    val j = r.nextInt(i + 1)
                    val tmp = list[i]; list[i] = list[j]; list[j] = tmp
                }
                list
            } else fileTokens

            // ---------------------------------------------------------
            // 3c) Random start offset – we simply drop the first `offset`
            //     tokens from the *buffer* before we start pulling windows.
            // ---------------------------------------------------------
            val offset = if (randomStartOffset) {
                (rng?.nextInt(stride) ?: 0)
            } else 0

            // If we already have some tokens from the previous file we keep them.
            // The offset applies **after** we have appended the new file tokens.
            buffer.addAll(shuffledFileTokens)
            if (offset > 0 && buffer.size >= offset) {
                repeat(offset) { buffer.removeAt(0) }
            }

            // ---------------------------------------------------------
            // 3d) Emit full‑size windows while we have enough tokens
            // ---------------------------------------------------------
            while (buffer.size >= seqLength) {
                // ── INPUT (seqLength tokens) ───────────────────────
                val inp = FloatArray(seqLength) { idx ->
                    // convert token‑id → float (the network expects FloatArray)
                    buffer[idx].toFloat()
                }

                // ── TARGET (next‑token for every position) ──────────
                val tgt = IntArray(seqLength) { idx ->
                    buffer[idx + 1]               // shifted by one
                }

                yield(Pair(inp, tgt))

                // ── SLIDE THE WINDOW ---------------------------------
                // stride == seqLength → we drop the whole window.
                // If you want overlapping windows, change `stride`.
                repeat(stride) { buffer.removeAt(0) }
            }
        }

        // -----------------------------------------------------------------
        // 4️⃣  What to do with the *final* leftover (< seqLength) tokens?
        // -----------------------------------------------------------------
        // We *do not* discard them.  Because `makeCorpusSource` creates a fresh
        // Sequence for every epoch, the leftover will become the *prefix* of the
        // first file of the *next* epoch – exactly the “remainder should be part
        // of the next training cycle” you asked for.
        // No extra code is needed; just leave `buffer` as‑is.
    }
}

/**
 * Computes a “comprehensive” loss on a random validation subset.
 *
 * @param booksDir          Directory that holds the training books.
 * @param vocabulary        Token‑vocabulary used for the corpus.
 * @param seqLength         Length of each training sequence.
 * @param stride            Stride between successive windows.
 * @param sampleSize        How many validation examples to draw (default = 1 000).
 * @param shuffleForVal    Whether to shuffle the corpus before sampling.
 *                         When true a brand‑new Random seed is used each call,
 *                         giving a *different* random subset every evaluation.
 */
class ComprehensiveLossFunction(
    private val booksDir: File,
    private val vocabulary: Vocabulary,
    private val seqLength: Int,
    private val stride: Int,
    private val sampleSize: Int = 1_000,
    private val shuffleForVal: Boolean = true
) : (NeuralNetwork) -> Float {

    override fun invoke(net: NeuralNetwork): Float {
        /* -------------------------------------------------------------
         * 1️⃣  Build a lazy corpus source.
         *    • When shuffleForVal == true we give a fresh RNG seed
         *      (System.nanoTime()) so the order changes on every call.
         *    • If you ever need a deterministic sample you can pass a
         *      fixed seed (e.g. Random(42)).
         * ------------------------------------------------------------- */
        val valSeq = generateCorpusSequence(
            booksDir = booksDir,
            vocabulary = vocabulary,
            seqLength = seqLength,
            stride = stride,
            shuffleFiles = shuffleForVal,
            rng = if (shuffleForVal) Random(System.nanoTime()) else null
        )

        /* -------------------------------------------------------------
         * 2️⃣  Pull the first `sampleSize` pairs from the (shuffled) stream.
         *    The stream is lazy, so only the needed windows are generated.
         * ------------------------------------------------------------- */
        val valPairs = valSeq.take(sampleSize).toList()
        if (valPairs.isEmpty()) return 0.0f

        /* -------------------------------------------------------------
         * 3️⃣  Convert to the shapes expected by the network.
         * ------------------------------------------------------------- */
        val valInputs = valPairs.map { it.first }.toTypedArray()
        val valSparseTargets = valPairs.flatMap { it.second.toList() }.toIntArray()

        /* -------------------------------------------------------------
         * 4️⃣  Forward‑pass and compute the sparse‑categorical‑cross‑entropy.
         * ------------------------------------------------------------- */
        val valPred = net.predict(valInputs)
        return sparseCategoricalCrossEntropy(valPred, valSparseTargets)
    }
}

fun main() {
    val books = File("/Volumes/onyx/books/training_data")
    val vocabulary = OnyxVocabulary("/Users/tosborn/onyx/books/vocabulary.dat")

    if (vocabulary.size == 0) {
        books.listFiles()?.forEach {
            vocabulary.appendToVocabulary(it.readText())
        }
        vocabulary.commit()
    }

    // Parameters
    val maxSequenceLength = 1024
    val stride = maxSequenceLength

    // Configure neural network
    val embeddingDim = maxSequenceLength
    val numHeads = 8
    val ffHiddenDim = 128
    var totalProbes = 0

    val checkProbe = { net: NeuralNetwork ->
        askProbes(net, vocabulary, maxSequenceLength)
        totalProbes++
        if (totalProbes % 1000 == 0) {
            net.saveToFile("/mnt/onyx/books/model-checkpoint.ser")
            println("✅ Saved checkpoint after $totalProbes")
        }
    }

    val layers = listOf(
        EmbeddingLayer(vocabulary.size, embeddingDim),
        PositionalEncodingLayer(maxSequenceLength, embeddingDim),
        CachedMultiHeadAttentionLayer(maxSequenceLength, embeddingDim, numHeads),
        LayerNormalizationLayer(embeddingDim),
        DenseLayer(embeddingDim, ffHiddenDim, Activation.RELU),
        DenseLayer(ffHiddenDim, embeddingDim, Activation.LINEAR),
        LayerNormalizationLayer(embeddingDim),
        DenseLayer(embeddingDim, vocabulary.size, Activation.LINEAR)
    )
    val checkpointPath = "/mnt/onyx/books/onyx-llm-checkpoint.ser"   // <‑‑ the file you keep saving to

    var model = NeuralNetwork.loadOrCreate(checkpointPath) {
        // This lambda is only executed when the file does **not** exist
        NeuralNetwork(layers, learningRate = 0.001f)
    }

    // Create comprehensive loss function instance
    val comprehensiveLossFn = ComprehensiveLossFunction(books, vocabulary, maxSequenceLength, stride)

    // Train the model using sparse streaming training (memory efficient!)
    try {
        val maxEpochs = 200
        val baseSeed   = 42L                 // any number you like

        repeat(maxEpochs) { epochIdx ->
            // 1️⃣  Build a *fresh* source for this epoch
            val source = makeCorpusSource(
                books,
                vocabulary,
                maxSequenceLength,
                stride,
                epochSeed = baseSeed + epochIdx
            )

            model = model.trainStreamingSparse(
                source = source,
                batchSize = 1,
                maxEpochs = 1,
                patience = Int.MAX_VALUE,
                testFrac = 0.0f,
                lossFn = { pred, sparseTargets -> sparseCategoricalCrossEntropy(pred, sparseTargets) },
                probeFn = { checkProbe(model) },
                comprehensiveLossFn = comprehensiveLossFn,
                saveModelPath = "/mnt/onyx/books/onyx-llm-$epochIdx.ser"
            )

            println("=== Finished epoch ${epochIdx + 1} ===")
        }
        println("All $maxEpochs epochs finished.")

    } catch (e: Exception) {
        println("Training failed with exception: ${e.message}")
        e.printStackTrace()
    }
}
