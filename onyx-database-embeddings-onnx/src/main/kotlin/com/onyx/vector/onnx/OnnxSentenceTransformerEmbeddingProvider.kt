package com.onyx.vector.onnx

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.exception.SearchEmbeddingUnavailableException
import com.onyx.vector.SearchEmbedding
import com.onyx.vector.SearchEmbeddingProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Local SentenceTransformer inference backed by ONNX Runtime.
 *
 * The checkpoint remains outside the application artifact. A standard SentenceTransformer model
 * directory supplies the tokenizer, sequence length, mean-pooling configuration, and ONNX graph.
 * Instances are safe to share across persistence managers and request threads.
 */
class OnnxSentenceTransformerEmbeddingProvider @JvmOverloads constructor(
    modelDirectory: Path,
    onnxModelPath: Path = modelDirectory.resolve(DEFAULT_ONNX_MODEL_PATH),
) : SearchEmbeddingProvider, AutoCloseable {

    private val lifecycleLock = Any()
    private val files = SentenceTransformerModelFiles.resolve(modelDirectory, onnxModelPath)

    /** Stable identifier for this exact checkpoint and embedding pipeline. */
    val calibrationId: Long = calibrationIdForArtifacts(files.calibrationArtifacts)

    private val resources = loadModel(files)
    private var closed = false

    /** Number of values returned for each input string. */
    val embeddingDimension: Int
        get() = resources.embeddingDimension

    /** Maximum number of tokens, including special tokens, accepted by this checkpoint. */
    val maxSequenceLength: Int
        get() = resources.maxSequenceLength

    override fun embed(text: String, entityType: Class<*>): SearchEmbedding =
        synchronized(lifecycleLock) {
            if (closed) {
                throw SearchEmbeddingUnavailableException(
                    "SentenceTransformer embedding provider is closed",
                )
            }

            try {
                val encoding = resources.tokenizer.encode(text)
                val inputIds = encoding.ids
                val attentionMask = encoding.attentionMask
                val tokenTypeIds = encoding.typeIds

                check(inputIds.isNotEmpty()) { "Tokenizer returned no input IDs" }
                check(attentionMask.size == inputIds.size) {
                    "Tokenizer returned mismatched input IDs and attention mask"
                }
                if (resources.usesTokenTypeIds) {
                    check(tokenTypeIds.size == inputIds.size) {
                        "Tokenizer returned mismatched token type IDs"
                    }
                }

                val inputs = LinkedHashMap<String, OnnxTensor>(3)
                try {
                    inputs[INPUT_IDS] = longTensor(inputIds)
                    inputs[ATTENTION_MASK] = longTensor(attentionMask)
                    if (resources.usesTokenTypeIds) {
                        inputs[TOKEN_TYPE_IDS] = longTensor(tokenTypeIds)
                    }

                    resources.session.run(inputs).use { output ->
                        val hiddenState = output.get(LAST_HIDDEN_STATE).orElseThrow {
                            IllegalStateException(
                                "ONNX model did not return $LAST_HIDDEN_STATE",
                            )
                        } as? OnnxTensor ?: error(
                            "ONNX output $LAST_HIDDEN_STATE is not a tensor",
                        )
                        val shape = (hiddenState.info as TensorInfo).shape
                        check(shape.size == 3 && shape[0] == 1L) {
                            "ONNX output $LAST_HIDDEN_STATE must have shape [1, tokens, dimensions]"
                        }
                        val sequenceLength = shape[1].exactPositiveInt("sequence length")
                        val dimension = shape[2].exactPositiveInt("embedding dimension")
                        check(sequenceLength == inputIds.size) {
                            "ONNX output sequence length $sequenceLength did not match tokenizer length ${inputIds.size}"
                        }
                        check(dimension == embeddingDimension) {
                            "ONNX output dimension $dimension did not match configured dimension $embeddingDimension"
                        }

                        val values = FloatArray(sequenceLength * dimension)
                        hiddenState.floatBuffer.get(values)
                        SearchEmbedding(
                            calibrationId,
                            SentenceEmbeddingMath.meanPoolAndNormalize(
                                hiddenState = values,
                                sequenceLength = sequenceLength,
                                embeddingDimension = dimension,
                                attentionMask = attentionMask,
                            ),
                        )
                    }
                } finally {
                    OnnxValue.close(inputs)
                }
            } catch (failure: SearchEmbeddingUnavailableException) {
                throw failure
            } catch (failure: Exception) {
                throw SearchEmbeddingUnavailableException(
                    "The local SentenceTransformer model could not generate an embedding",
                    failure,
                )
            }
        }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            var failure: Exception? = null
            try {
                resources.session.close()
            } catch (closeFailure: Exception) {
                failure = closeFailure
            }
            try {
                resources.tokenizer.close()
            } catch (closeFailure: Exception) {
                if (failure == null) failure = closeFailure else failure.addSuppressed(closeFailure)
            }
            if (failure != null) {
                throw IllegalStateException("Could not close SentenceTransformer resources", failure)
            }
        }
    }

    private fun longTensor(values: LongArray): OnnxTensor =
        OnnxTensor.createTensor(resources.environment, arrayOf(values))

    private data class LoadedModel(
        val environment: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: HuggingFaceTokenizer,
        val embeddingDimension: Int,
        val maxSequenceLength: Int,
        val usesTokenTypeIds: Boolean,
    )

    companion object {
        private const val DEFAULT_ONNX_MODEL_PATH = "onnx/model.onnx"
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val TOKEN_TYPE_IDS = "token_type_ids"
        private const val LAST_HIDDEN_STATE = "last_hidden_state"

        /**
         * Downloads the pinned all-MiniLM-L6-v2 checkpoint when necessary and opens it.
         * Verified cache entries are reused without a network request.
         */
        @JvmStatic
        fun allMiniLmL6V2(): OnnxSentenceTransformerEmbeddingProvider =
            allMiniLmL6V2(AllMiniLmL6V2Model.defaultModelDirectory())

        /**
         * Downloads the pinned all-MiniLM-L6-v2 checkpoint into [modelDirectory] when necessary
         * and opens it. Missing or corrupted artifacts are repaired before the model is loaded.
         */
        @JvmStatic
        fun allMiniLmL6V2(modelDirectory: Path): OnnxSentenceTransformerEmbeddingProvider =
            OnnxSentenceTransformerEmbeddingProvider(
                AllMiniLmL6V2Model.ensureAvailable(modelDirectory),
            )

        /** Default cache directory used by [allMiniLmL6V2]. */
        @JvmStatic
        fun defaultAllMiniLmL6V2Directory(): Path = AllMiniLmL6V2Model.defaultModelDirectory()

        private fun loadModel(files: SentenceTransformerModelFiles): LoadedModel {
            val sentenceConfiguration = readJson(files.sentenceConfiguration)
            val maxSequenceLength = sentenceConfiguration.requiredPositiveInt(
                "max_seq_length",
                files.sentenceConfiguration,
            )
            val poolingConfiguration = readJson(files.poolingConfiguration)
            validateMeanPooling(poolingConfiguration, files.poolingConfiguration)
            val configuredDimension = poolingConfiguration.requiredPositiveInt(
                "word_embedding_dimension",
                files.poolingConfiguration,
            )

            var tokenizer: HuggingFaceTokenizer? = null
            var session: OrtSession? = null
            try {
                tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(files.tokenizer)
                    .optTokenizerConfigPath(files.tokenizerConfiguration.toString())
                    .optAddSpecialTokens(true)
                    .optTruncation(true)
                    .optPadding(true)
                    .optMaxLength(maxSequenceLength)
                    .build()

                val environment = OrtEnvironment.getEnvironment()
                session = OrtSession.SessionOptions().use { options ->
                    environment.createSession(files.onnxModel.toString(), options)
                }
                val usesTokenTypeIds = validateModelContract(session, configuredDimension)
                return LoadedModel(
                    environment = environment,
                    session = session,
                    tokenizer = tokenizer,
                    embeddingDimension = configuredDimension,
                    maxSequenceLength = maxSequenceLength,
                    usesTokenTypeIds = usesTokenTypeIds,
                )
            } catch (failure: Throwable) {
                runCatching { session?.close() }
                runCatching { tokenizer?.close() }
                if (failure is IllegalArgumentException) throw failure
                throw IllegalStateException(
                    "Could not load SentenceTransformer model from ${files.modelDirectory}",
                    failure,
                )
            }
        }

        private fun validateModelContract(session: OrtSession, configuredDimension: Int): Boolean {
            val supportedInputs = setOf(INPUT_IDS, ATTENTION_MASK, TOKEN_TYPE_IDS)
            val unknownInputs = session.inputNames - supportedInputs
            require(unknownInputs.isEmpty()) {
                "ONNX model has unsupported inputs: ${unknownInputs.sorted().joinToString()}"
            }
            require(INPUT_IDS in session.inputNames && ATTENTION_MASK in session.inputNames) {
                "ONNX model must accept $INPUT_IDS and $ATTENTION_MASK"
            }
            session.inputInfo.forEach { (name, node) ->
                val info = node.info as? TensorInfo
                    ?: throw IllegalArgumentException("ONNX input $name must be a tensor")
                require(info.type == OnnxJavaType.INT64 && info.shape.size == 2) {
                    "ONNX input $name must be a rank-2 INT64 tensor"
                }
            }

            val output = session.outputInfo[LAST_HIDDEN_STATE]?.info as? TensorInfo
                ?: throw IllegalArgumentException(
                    "ONNX model must return a $LAST_HIDDEN_STATE tensor",
                )
            require(output.type == OnnxJavaType.FLOAT && output.shape.size == 3) {
                "ONNX output $LAST_HIDDEN_STATE must be a rank-3 FLOAT tensor"
            }
            val modelDimension = output.shape[2]
            require(modelDimension == configuredDimension.toLong()) {
                "Pooling dimension $configuredDimension does not match ONNX dimension $modelDimension"
            }
            return TOKEN_TYPE_IDS in session.inputNames
        }

        private fun validateMeanPooling(configuration: JsonObject, path: Path) {
            require(configuration["pooling_mode_mean_tokens"]?.asBoolean == true) {
                "$path must enable pooling_mode_mean_tokens"
            }
            val unsupportedModes = configuration.entrySet()
                .filter { (name, value) ->
                    name.startsWith("pooling_mode_") &&
                        name != "pooling_mode_mean_tokens" &&
                        value.isJsonPrimitive &&
                        value.asJsonPrimitive.isBoolean &&
                        value.asBoolean
                }
                .map { it.key }
            require(unsupportedModes.isEmpty()) {
                "$path enables unsupported pooling modes: ${unsupportedModes.joinToString()}"
            }
        }

        private fun readJson(path: Path): JsonObject = try {
            Files.newBufferedReader(path).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        } catch (failure: Exception) {
            throw IllegalArgumentException("Could not read JSON configuration $path", failure)
        }

        private fun JsonObject.requiredPositiveInt(name: String, path: Path): Int {
            val value = try {
                get(name)?.asInt
            } catch (_: Exception) {
                null
            }
            require(value != null && value > 0) {
                "$path must contain a positive integer $name"
            }
            return value
        }
    }
}

internal data class SentenceTransformerModelFiles(
    val modelDirectory: Path,
    val onnxModel: Path,
    val tokenizer: Path,
    val tokenizerConfiguration: Path,
    val sentenceConfiguration: Path,
    val poolingConfiguration: Path,
) {
    val calibrationArtifacts: List<Pair<String, Path>>
        get() = listOf(
            "onnx-model" to onnxModel,
            "tokenizer" to tokenizer,
            "tokenizer-configuration" to tokenizerConfiguration,
            "sentence-configuration" to sentenceConfiguration,
            "pooling-configuration" to poolingConfiguration,
        )

    companion object {
        fun resolve(modelDirectory: Path, onnxModelPath: Path): SentenceTransformerModelFiles {
            val directory = modelDirectory.toAbsolutePath().normalize()
            require(Files.isDirectory(directory) && Files.isReadable(directory)) {
                "SentenceTransformer model directory is not readable: $directory"
            }
            return SentenceTransformerModelFiles(
                modelDirectory = directory,
                onnxModel = requiredFile(onnxModelPath.toAbsolutePath().normalize(), "ONNX model"),
                tokenizer = requiredFile(directory.resolve("tokenizer.json"), "tokenizer"),
                tokenizerConfiguration = requiredFile(
                    directory.resolve("tokenizer_config.json"),
                    "tokenizer configuration",
                ),
                sentenceConfiguration = requiredFile(
                    directory.resolve("sentence_bert_config.json"),
                    "SentenceTransformer configuration",
                ),
                poolingConfiguration = requiredFile(
                    directory.resolve("1_Pooling/config.json"),
                    "pooling configuration",
                ),
            )
        }

        private fun requiredFile(path: Path, label: String): Path {
            require(Files.isRegularFile(path) && Files.isReadable(path)) {
                "SentenceTransformer $label is not readable: $path"
            }
            return path
        }
    }
}

internal object SentenceEmbeddingMath {
    fun meanPoolAndNormalize(
        hiddenState: FloatArray,
        sequenceLength: Int,
        embeddingDimension: Int,
        attentionMask: LongArray,
    ): FloatArray {
        require(sequenceLength > 0 && embeddingDimension > 0) {
            "Sequence length and embedding dimension must be positive"
        }
        require(attentionMask.size == sequenceLength) {
            "Attention mask length must match the sequence length"
        }
        require(hiddenState.size == sequenceLength * embeddingDimension) {
            "Hidden state shape does not match sequence length and embedding dimension"
        }

        val pooled = FloatArray(embeddingDimension)
        var includedTokens = 0
        for (token in 0 until sequenceLength) {
            if (attentionMask[token] == 0L) continue
            includedTokens++
            val offset = token * embeddingDimension
            for (dimension in 0 until embeddingDimension) {
                pooled[dimension] += hiddenState[offset + dimension]
            }
        }
        require(includedTokens > 0) { "Attention mask did not include any tokens" }

        var squaredNorm = 0.0
        for (dimension in pooled.indices) {
            pooled[dimension] /= includedTokens.toFloat()
            require(pooled[dimension].isFinite()) {
                "Embedding model returned a non-finite value"
            }
            squaredNorm += pooled[dimension].toDouble() * pooled[dimension].toDouble()
        }
        require(squaredNorm.isFinite() && squaredNorm > 0.0) {
            "Embedding model returned a zero or non-finite vector"
        }

        val norm = sqrt(squaredNorm).toFloat()
        for (dimension in pooled.indices) {
            pooled[dimension] /= norm
        }
        return pooled
    }
}

internal fun calibrationIdForArtifacts(artifacts: List<Pair<String, Path>>): Long {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("onyx-sentence-transformer-mean-pool-l2-v1\u0000".toByteArray())
    artifacts.forEach { (name, path) ->
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(nameBytes.size).array())
        digest.update(nameBytes)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(Files.size(path)).array())
        Files.newInputStream(path).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
    }
    val value = ByteBuffer.wrap(digest.digest()).order(ByteOrder.BIG_ENDIAN).long
    return if (value == 0L) 1L else value
}

private fun Long.exactPositiveInt(label: String): Int {
    check(this in 1..Int.MAX_VALUE.toLong()) { "Invalid ONNX $label: $this" }
    return toInt()
}
