import com.onyxdevtools.ai.NeuralNetwork
import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.extensions.sparseCategoricalCrossEntropy
import com.onyxdevtools.ai.generation.chat
import com.onyxdevtools.ai.layer.Layer
import com.onyxdevtools.ai.layer.impl.*
import com.onyxdevtools.ai.transformation.BPETokenizer
import com.onyxdevtools.ai.transformation.OnyxVocabulary
import com.onyxdevtools.ai.transformation.Vocabulary
import com.onyxdevtools.ai.transformation.appendToVocabulary
import kotlin.random.Random
import java.io.File

val checkpointPath = "/Volumes/onyx/books/model-checkpoint.ser"   // <‑‑ the file you keep saving to

// Add somewhere above train call
fun askProbes(model: NeuralNetwork, vocab: Vocabulary, seqLen: Int) {
    val qs = listOf(
//        "What are you?",
//        "Who is Alice?",
//        "Who is the White Rabbit?",
        "Who is the son of God?",
        "What are the ten commandments?",
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
// brand-new Sequence each time it is invoked.
// ---------------------------------------------------------------
fun makeCorpusSource(
    booksDir: File,
    vocab: Vocabulary,
    seqLen: Int,
    stride: Int,
    epochSeed: Long            // different for every epoch
): () -> Sequence<Pair<FloatArray, IntArray>> = {
    generateCorpusSequence(
        booksDir = booksDir,
        vocabulary = vocab,
        seqLength = seqLen,
        stride = stride,
        shuffleFiles = true,
        rng = Random(epochSeed),
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
    randomStartOffset: Boolean = false
): Sequence<Pair<FloatArray, IntArray>> {

    require(seqLength > 0) { "seqLength must be > 0" }
    require(stride > 0) { "stride must be > 0" }
    require(stride <= seqLength) { "stride may not be larger than seqLength" }

    val tokenizer = BPETokenizer(vocabulary)

    val allFiles = booksDir.listFiles()
        ?.filter { it.isFile && it.extension.contains("txt") }
        ?: emptyList()
    val files = if (shuffleFiles) (rng?.let { allFiles.shuffled(it) } ?: allFiles.shuffled()) else allFiles

    // Cache special token IDs
    val nlId = vocabulary.getId("\n")
    val sotId = vocabulary.getId("[SOT]")
    val eotId = vocabulary.getId("[EOT]")

    val buffer = ArrayList<Int>(seqLength * 2)

    return sequence {
        for ((idx, file) in files.withIndex()) {
            // Insert hard boundary between documents (except before the first)
            if (idx > 0) buffer.add(eotId)
            // Optional start-of-text marker per doc
            buffer.add(sotId)

            // --- Load tokens in original order (do NOT shuffle within a file) ---
            file.forEachLine { line ->
                for (tok in tokenizer.tokenize(line)) {
                    buffer.add(vocabulary.getId(tok))
                }
                buffer.add(nlId)
            }

            // Optional random offset after appending this file
            if (randomStartOffset) {
                val off = (rng?.nextInt(stride) ?: 0)
                val toDrop = minOf(off, buffer.size)
                repeat(toDrop) { buffer.removeAt(0) }
            }

            // --- Emit windows. Need +1 for next-token targets ---
            while (buffer.size >= seqLength + 1) {
                val inp = FloatArray(seqLength) { i -> buffer[i].toFloat() }
                val tgt = IntArray(seqLength) { i -> buffer[i + 1] }
                yield(inp to tgt)

                // Slide
                repeat(stride) { if (buffer.isNotEmpty()) buffer.removeAt(0) }
            }
        }
        // leftover (< seqLen+1) stays in buffer; next epoch's source starts fresh
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
        val valInputs = Tensor.from(valPairs.map { it.first }.toTypedArray())
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

    // ===== DROP-IN REPLACEMENT: model sizing + stack =====

    // Keep sequence length (context window) separate from model width
    val seqLen = 256                   // context window for training/inference
    val dModel = 512                  // embedding/model width (capacity)
    val numHeads = 8                  // headSize = dModel / numHeads = 64

    fun ffnDim(d: Int): Int {
        val raw = (8 * d) / 3          // ~GPT-style MLP multiplier
        return ((raw + 255) / 256) * 256
    }

    val ffHiddenDim = ffnDim(dModel)

    // --- Build Transformer ---
    val blocks = 14
    val layers = arrayListOf<Layer>(
        // (vocab -> dModel)
        EmbeddingLayer(vocabulary.size, dModel)
    )

    repeat(blocks) {
        // x = x + Attn(RMSNorm(x))
        layers.add(
            ResidualLayer(
                layers = listOf(
                    LayerNormalizationLayer(dModel),
                    RotaryMultiHeadAttentionLayer(
                        modelSize = dModel,
                        headCount = numHeads
                    )
                )
            )
        )
        // x = x + MLP(RMSNorm(x))
        layers.add(
            ResidualLayer(
                layers = listOf(
                    LayerNormalizationLayer(dModel),
                    SwiGLULayer(dModel, ffHiddenDim, dModel)
                )
            )
        )
    }

    // Final norm + lm_head (dModel -> vocab)
    layers.add(LayerNormalizationLayer(dModel))
    layers.add(DenseLayer(dModel, vocabulary.size, Activation.LINEAR))

    // Use seqLen everywhere a *context window* is needed
    val maxSequenceLength = seqLen  // keep your existing variable name alive if referenced below

    var totalProbes = 0
    val checkProbe = { net: NeuralNetwork ->

        if (totalProbes > 2) {
            askProbes(net, vocabulary, maxSequenceLength)
        }
        totalProbes++
        net.saveToFile(checkpointPath)
        println("✅ Saved checkpoint after $totalProbes")
    }

    // Update any calls that used maxSequenceLength as d_model.
    // For example, training stride calc should use seqLen:
    fun strideForEpoch(epoch: Int, s: Int): Int = when {
        epoch < 2 -> s / 4
        epoch < 5 -> s / 2
        else -> s
    }


    var model = NeuralNetwork.loadOrCreate(checkpointPath) {
        // This lambda is only executed when the file does **not** exist
        NeuralNetwork(layers, learningRate = 0.0001f)
    }

    // Train the model using sparse streaming training (memory efficient!)
    try {
        val maxEpochs = 200
        val baseSeed = 42L                 // any number you like

        repeat(maxEpochs) { epochIdx ->
            // 1️⃣  Build a *fresh* source for this epoch
            val source = makeCorpusSource(
                books,
                vocabulary,
                maxSequenceLength,
                strideForEpoch(epochIdx, maxSequenceLength),
                epochSeed = baseSeed + epochIdx
            )

            model = model.trainStreamingSparse(
                source = source,
                batchSize = 1,
                maxEpochs = 1,
                gradAccumSteps = 256,
                patience = Int.MAX_VALUE,
                testFrac = 0.0f,
                lossFn = { pred, sparseTargets -> sparseCategoricalCrossEntropy(pred, sparseTargets) },
                probeFn = { checkProbe(model) },
                comprehensiveLossFn = ComprehensiveLossFunction(
                    books,
                    vocabulary,
                    maxSequenceLength,
                    strideForEpoch(epochIdx, maxSequenceLength)
                ),
                saveModelPath = "/Volumes/onyx/books/onyx-llm-$epochIdx-1.ser"
            )

            println("=== Finished epoch ${epochIdx + 1} ===")
        }
        println("All $maxEpochs epochs finished.")

    } catch (e: Exception) {
        println("Training failed with exception: ${e.message}")
        e.printStackTrace()
    }
}
