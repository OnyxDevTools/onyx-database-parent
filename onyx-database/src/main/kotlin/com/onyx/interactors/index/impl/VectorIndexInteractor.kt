package com.onyx.interactors.index.impl

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.exception.OnyxException
import com.onyx.extension.identifier
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Implements a vector index for semantic search using Locality-Sensitive Hashing (LSH).
 *
 * This interactor converts field values (strings, float arrays) into fixed-size vectors
 * and indexes them across multiple hash tables. Queries are accelerated by probing these
 * tables for approximate nearest neighbors.
 *
 * Features:
 * - **Text Embedding**: A simple "bag-of-words" and n-grams model creates vectors from text.
 * - **LSH**: Multiple random projection hash tables reduce the search space.
 * - **Multi-probe**: Searches adjacent hash buckets (Hamming distance <= 2) to improve recall.
 * - **Fallback**: A bounded full scan is used if LSH yields too few results.
 * - **Scoring**: Candidates are re-ranked using cosine similarity (dot product on normalized vectors).
 */
class VectorIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : DefaultIndexInteractor(entityDescriptor, indexDescriptor, context) {

    // ---- Core Vector and LSH Configuration ----
    /** 
     * The dimensionality of the embedding vectors. 
     * Once defined, this value cannot be changed as it affects the storage format.
     */
    private val embeddingDimension: Int = if (indexDescriptor.embeddingDimensions > 0) indexDescriptor.embeddingDimensions else 128

    /** 
     * The number of LSH hash tables to use. More tables improve accuracy but increase index size.
     * This value can be customized via the @Index annotation.
     */
    private val lshTableCount: Int = if (indexDescriptor.hashTableCount > 0) indexDescriptor.hashTableCount else 12

    /** The maximum number of bits (hyperplanes) per LSH table signature used during indexing. */
    private val maxBitsPerTable: Int = 20

    /** The seed for the random number generator to ensure deterministic hyperplanes. */
    private val randomSeed: Long = 0xC0FFEE5EED

    // ---- Query Quality and Performance Guards ----
    /** Queries with fewer tokens than this will be ignored. */
    private val minQueryTokens = 1

    /** 
     * Search results with a cosine similarity score below this threshold will be discarded.
     * This value can be customized via the @Index annotation.
     */
    private val minReturnedScore = if (indexDescriptor.minimumScore > 0) indexDescriptor.minimumScore else 0.18f

    /** The minimum length of a query string to enable character n-gram generation. */
    private val shortQueryNgramThreshold = 4

    // ---- Text Embedding Parameters ----
    /** Toggles the use of character n-grams to enrich the text embedding. */
    private val useCharNgrams = true

    /** The minimum length of n-grams to generate. */
    private val ngramMinLength = 3

    /** The maximum length of n-grams to generate. */
    private val ngramMaxLength = 5

    /** The minimum length for a word to be considered a token. */
    private val tokenMinLength = 2

    private val contextRef = WeakReference(context)
    private val context: SchemaContext
        get() = contextRef.get()
            ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    private val dataFile: DiskMapFactory get() = context.getDataFile(entityDescriptor)

    /** Stores the mapping from a record's ID to its normalized vector (Float[embeddingDimension]). */
    private val vectors: DiskMap<Long, ByteArray>
        get() = dataFile.getHashMap(Long::class.java, namespace("vectors"))

    /** Retrieves the LSH hash table for a given table index. Maps a signature to a postings list header. */
    private fun tableMap(tableIndex: Int): DiskMap<Int, Header> =
        dataFile.getHashMap(Int::class.java, namespace("table_$tableIndex"))

    /** Retrieves the inverse map for a given LSH table. Maps a record ID to its signature. */
    private fun inverseTableMap(tableIndex: Int): DiskMap<Long, Int> =
        dataFile.getHashMap(Long::class.java, namespace("table_${tableIndex}_inv"))

    /** Pre-computed random hyperplanes for LSH, generated deterministically from the seed. */
    private val planes: Array<Array<FloatArray>> = Array(lshTableCount) { tableIndex ->
        Array(maxBitsPerTable) { planeIndex ->
            randUnitVector(embeddingDimension, seedFor(tableIndex, planeIndex))
        }
    }

    /** Stores all span identifiers that were materialised for string fields. */
    private val allSpanIds: DiskMap<Long, Byte?>
        get() = dataFile.getHashMap(Long::class.java, namespace("span_ids"))

    /** Metadata for each span used to map back to the parent record and offsets. */
    private val spanMeta: DiskMap<Long, SpanMeta>
        get() = dataFile.getHashMap(Long::class.java, namespace("span_meta"))

    /** Persisted normalised span text for clean removal and analytics. */
    private val spanText: DiskMap<Long, String>
        get() = dataFile.getHashMap(Long::class.java, namespace("span_text"))

    /** Tracks how many spans were produced for a given record id. */
    private val documentSpanCounts: DiskMap<Long, Int>
        get() = dataFile.getHashMap(Long::class.java, namespace("doc_span_counts"))

    /** Character 3-gram inverted index pointing to candidate span ids. */
    private val charNgramIndex: DiskMap<String, Header>
        get() = dataFile.getHashMap(String::class.java, namespace("char_ng3"))

    /** Stores per-span token frequencies for lexical scoring. */
    private val spanTokenFrequency: DiskMap<Long, Map<String, Int>>
        get() = dataFile.getHashMap(Long::class.java, namespace("span_tf"))

    /** Stores per-span token counts to compute BM25 length normalisation. */
    private val spanTokenCounts: DiskMap<Long, Int>
        get() = dataFile.getHashMap(Long::class.java, namespace("span_token_counts"))

    /** Global token document frequency across all spans. */
    private val tokenDocumentFrequency: DiskMap<String, Int>
        get() = dataFile.getHashMap(String::class.java, namespace("token_df"))

    /** Global feature document frequency used by the hashed embedding. */
    private val featureDocumentFrequency: DiskMap<String, Int>
        get() = dataFile.getHashMap(String::class.java, namespace("feature_df"))

    /** Miscellaneous aggregate statistics for lexical scoring. */
    private val stats: DiskMap<String, Long>
        get() = dataFile.getHashMap(String::class.java, namespace("stats"))

    private val spanWindowSizeTokens = 200
    private val spanOverlapTokens = 50
    private val spanStrideTokens = spanWindowSizeTokens - spanOverlapTokens

    private val bm25K1 = 1.2f
    private val bm25B = 0.75f
    private val maxRerankCandidates = 256

    private val tokenPattern = Regex("\\p{L}+|\\p{N}+")

    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if",
        "in", "into", "is", "it", "no", "not", "of", "on", "or", "such",
        "that", "the", "their", "then", "there", "these", "they", "this",
        "to", "was", "will", "with"
    )

    private val totalSpanCountKey = "total_span_count"
    private val totalTokenCountKey = "total_token_count"

    private data class SpanWindow(
        val text: String,
        val start: Int,
        val length: Int,
        val normalized: String
    )

    private data class PreparedSpan(
        val normalizedText: String,
        val featureFrequencies: Map<String, Int>,
        val uniqueFeatures: Set<String>,
        val tokenFrequencies: Map<String, Int>,
        val uniqueTokens: Set<String>,
        val tokenCount: Int,
        val charTrigrams: Set<String>
    )

    data class SpanMeta(var documentId: Long = 0L, var start: Int = 0, var length: Int = 0) : BufferStreamable {
        override fun read(buffer: BufferStream) {
            documentId = buffer.long
            start = buffer.int
            length = buffer.int
        }

        override fun write(buffer: BufferStream) {
            buffer.putLong(documentId)
            buffer.putInt(start)
            buffer.putInt(length)
        }
    }

    private fun spanId(documentId: Long, spanIndex: Int): Long =
        (documentId shl 16) or (spanIndex.toLong() and 0xFFFF)

    // ---------------- Lifecycle ----------------

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        super.save(indexValue, oldReferenceId, newReferenceId)

        if (oldReferenceId > 0L) deleteVectorOnly(oldReferenceId)

        val valueForIndex = indexValue ?: extractIndexFieldValue(loadEntity(newReferenceId))
        indexValueForRecord(newReferenceId, valueForIndex)
    }

    private fun indexValueForRecord(recordId: Long, valueForIndex: Any?) {
        when (valueForIndex) {
            null -> return
            is String -> indexStringValue(recordId, valueForIndex)
            else -> {
                val vector = anyToVector(valueForIndex) ?: return
                require(vector.size == embeddingDimension) {
                    "Vector dimension mismatch: got ${vector.size}, expected $embeddingDimension"
                }
                documentSpanCounts.remove(recordId)
                storeVector(recordId, vector)
            }
        }
    }

    private fun indexStringValue(recordId: Long, rawText: String) {
        val spans = sliceTextIntoWindows(rawText)
        if (spans.isEmpty()) {
            documentSpanCounts.remove(recordId)
            return
        }

        var spanIndex = 0
        for (spanWindow in spans) {
            storeSpan(recordId, spanIndex++, spanWindow)
        }
        documentSpanCounts[recordId] = spans.size
    }

    private fun storeVector(recordId: Long, vector: FloatArray) {
        writeVector(recordId, vector)
    }

    private fun storeSpan(recordId: Long, spanIndex: Int, spanWindow: SpanWindow) {
        val prepared = prepareSpan(spanWindow.normalized)
        val spanId = spanId(recordId, spanIndex)

        updateTokenDocumentFrequency(prepared.uniqueTokens, 1)
        updateFeatureDocumentFrequency(prepared.uniqueFeatures, 1)
        incrementStat(totalSpanCountKey, 1)
        incrementStat(totalTokenCountKey, prepared.tokenCount.toLong())

        val vector = buildVectorFromFeatures(prepared.featureFrequencies)
        writeVector(spanId, vector)

        allSpanIds[spanId] = null
        spanMeta[spanId] = SpanMeta(recordId, spanWindow.start, spanWindow.length)
        spanText[spanId] = prepared.normalizedText
        spanTokenFrequency[spanId] = HashMap(prepared.tokenFrequencies)
        spanTokenCounts[spanId] = prepared.tokenCount

        addCharNgramsForSpan(spanId, prepared.charTrigrams)
    }

    private fun writeVector(recordId: Long, vector: FloatArray) {
        require(vector.size == embeddingDimension) {
            "Vector dimension mismatch: got ${vector.size}, expected $embeddingDimension"
        }
        vectors[recordId] = floatsToBytes(vector)

        for (tableIndex in 0 until lshTableCount) {
            val signature = calculateSignature(vector, planes[tableIndex], maxBitsPerTable)
            inverseTableMap(tableIndex)[recordId] = signature
            addRecordToPostingList(recordId, signature, tableIndex)
        }
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        super.delete(reference)
        deleteVectorOnly(reference)
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun rebuild() {
        super.rebuild()
        clearVectorOnly()

        val records = context.getDataFile(entityDescriptor)
            .getHashMap<DiskMap<Any, IManagedEntity>>(
                entityDescriptor.identifier!!.type,
                entityDescriptor.entityClass.name
            )
        records.entries.forEach { entry ->
            val recordId = records.getRecID(entry.key)
            if (recordId > 0) {
                val indexValue = extractIndexFieldValue(entry.value)
                indexValueForRecord(recordId, indexValue)
            }
        }
    }

    @Synchronized
    override fun clear() {
        super.clear()
        clearVectorOnly()
    }

    // ---------------- Search ----------------

    @Throws(OnyxException::class)
    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> {
        val queryTokenCount = countTokensForQuery(indexValue)
        if (queryTokenCount < minQueryTokens) return emptyMap()

        val queryVector = anyToVector(indexValue) ?: return emptyMap()
        require(queryVector.size == embeddingDimension) {
            "Query vector dimension mismatch: got ${queryVector.size}, expected $embeddingDimension"
        }

        val candidateCapacity = maxCandidates.coerceAtLeast(limit).coerceAtLeast(1)
        val candidates = LinkedHashSet<Long>(candidateCapacity.coerceAtLeast(256))

        val queryText = (indexValue as? String)
        val trimmedQuery = queryText?.trim()
        val charCandidates = if (trimmedQuery != null && shouldUseCharCandidates(trimmedQuery)) {
            gatherCharNgramCandidates(trimmedQuery, candidateCapacity)
        } else {
            emptyList()
        }

        for (candidate in charCandidates) {
            candidates.add(candidate)
            if (candidates.size >= candidateCapacity) break
        }

        if (candidates.isEmpty()) {
            candidates.addAll(gatherCandidatesFromLsh(queryVector, candidateCapacity))
        }

        if (candidates.isEmpty()) return emptyMap()

        val lexicalNormalized = trimmedQuery?.let {
            val literalContent = if (isQuoted(it)) it.substring(1, it.length - 1) else it
            normalizeForIndexing(literalContent)
        }

        val queryTokens = lexicalNormalized?.let { tokenizeForScoring(it) } ?: emptyList()
        val queryTokenFrequency = if (queryTokens.isEmpty()) emptyMap() else queryTokens.groupingBy { it }.eachCount()

        val lexicalScores = if (queryTokenFrequency.isNotEmpty()) {
            scoreCandidatesWithBm25(candidates, queryTokenFrequency)
        } else {
            emptyMap()
        }

        val rerankPool = selectRerankPool(candidates, lexicalScores, candidateCapacity)
        if (rerankPool.isEmpty()) return emptyMap()

        val docScores = computeCosineScores(rerankPool, queryVector)
        if (docScores.isEmpty()) return emptyMap()

        val sortedResults = docScores.entries
            .sortedByDescending { it.value }
            .filter { it.value >= minReturnedScore }
            .take(limit)

        if (sortedResults.isEmpty()) return emptyMap()

        val results = LinkedHashMap<Long, Any?>(sortedResults.size)
        for (entry in sortedResults) {
            results[entry.key] = entry.value
        }
        return results
    }

    // ---------------- Vector-Only Maintenance ----------------

    /** Deletes all vector-related data for a given record ID without touching the main index. */
    private fun deleteVectorOnly(referenceId: Long) {
        if (referenceId <= 0L) return
        removeSpansForDocument(referenceId)
        removeVector(referenceId)
        documentSpanCounts.remove(referenceId)
    }

    /** Clears all vector-related data from the index. */
    private fun clearVectorOnly() {
        vectors.clear()
        spanMeta.clear()
        spanText.clear()
        spanTokenFrequency.clear()
        spanTokenCounts.clear()
        documentSpanCounts.clear()
        allSpanIds.clear()
        tokenDocumentFrequency.clear()
        featureDocumentFrequency.clear()
        stats.clear()

        for (entry in charNgramIndex.entries.toList()) {
            val header = entry.value
            val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            postings.clear()
        }
        charNgramIndex.clear()

        for (tableIndex in 0 until lshTableCount) {
            tableMap(tableIndex).clear()
            inverseTableMap(tableIndex).clear()
        }
    }

    private fun removeSpansForDocument(documentId: Long) {
        val spanCount = documentSpanCounts[documentId] ?: return
        documentSpanCounts.remove(documentId)
        for (index in 0 until spanCount) {
            val sid = spanId(documentId, index)
            removeSpan(sid)
        }
    }

    private fun removeSpan(spanId: Long) {
        val normalized = spanText.remove(spanId)
        val prepared = normalized?.let { prepareSpan(it) }

        spanMeta.remove(spanId)
        spanTokenFrequency.remove(spanId)
        val tokenCount = spanTokenCounts.remove(spanId)
        allSpanIds.remove(spanId)

        prepared?.let {
            updateTokenDocumentFrequency(it.uniqueTokens, -1)
            updateFeatureDocumentFrequency(it.uniqueFeatures, -1)
            removeCharNgramsForSpan(spanId, it.charTrigrams)
            incrementStat(totalSpanCountKey, -1)
            incrementStat(totalTokenCountKey, -it.tokenCount.toLong())
        } ?: run {
            tokenCount?.let { incrementStat(totalTokenCountKey, -it.toLong()) }
            incrementStat(totalSpanCountKey, -1)
        }

        removeVector(spanId)
    }

    private fun removeVector(recordId: Long) {
        for (tableIndex in 0 until lshTableCount) {
            val inverseTable = inverseTableMap(tableIndex)
            val signature = inverseTable.remove(recordId) ?: continue

            val table = tableMap(tableIndex)
            table.computeIfPresent(signature) { _, header ->
                val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header!!)
                postings.remove(recordId)
                header.also {
                    it.firstNode = postings.reference.firstNode
                    it.position = postings.reference.position
                    it.recordCount.set(postings.reference.recordCount.get())
                }
            }
        }
        vectors.remove(recordId)
    }

    private fun addCharNgramsForSpan(spanId: Long, trigrams: Set<String>) {
        if (trigrams.isEmpty()) return
        for (gram in trigrams) {
            charNgramIndex.compute(gram) { _, existingHeader ->
                val header = existingHeader ?: dataFile.newMapHeader()
                val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
                postings[spanId] = null
                header.also {
                    it.firstNode = postings.reference.firstNode
                    it.position = postings.reference.position
                    it.recordCount.set(postings.reference.recordCount.get())
                }
                header
            }
        }
    }

    private fun removeCharNgramsForSpan(spanId: Long, trigrams: Set<String>) {
        if (trigrams.isEmpty()) return
        for (gram in trigrams) {
            val header = charNgramIndex[gram] ?: continue
            val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            if (postings.remove(spanId) != null) {
                header.also {
                    it.firstNode = postings.reference.firstNode
                    it.position = postings.reference.position
                    it.recordCount.set(postings.reference.recordCount.get())
                }
                if (postings.isEmpty()) {
                    charNgramIndex.remove(gram)
                }
            }
        }
    }

    private fun shouldUseCharCandidates(trimmedQuery: String): Boolean {
        if (trimmedQuery.isEmpty()) return false
        if (trimmedQuery.length <= 32) return true
        return isQuoted(trimmedQuery)
    }

    private fun isQuoted(trimmedQuery: String): Boolean =
        trimmedQuery.length >= 2 && trimmedQuery.first() == '"' && trimmedQuery.last() == '"'

    private fun gatherCharNgramCandidates(trimmedQuery: String, candidateCapacity: Int): List<Long> {
        val content = if (isQuoted(trimmedQuery)) {
            trimmedQuery.substring(1, trimmedQuery.length - 1)
        } else {
            trimmedQuery
        }
        val normalized = normalizeForIndexing(content)
        val trigrams = extractCharTrigrams(normalized)
        if (trigrams.isEmpty()) return emptyList()

        val orderedTrigrams = trigrams.sortedBy { gram: String ->
            (charNgramIndex[gram]?.recordCount?.get() ?: Int.MAX_VALUE).toLong()
        }

        var intersection: MutableSet<Long>? = null
        for (gram in orderedTrigrams) {
            val postings = getCharGramPostings(gram)
            if (postings.isEmpty()) return emptyList()
            intersection = if (intersection == null) {
                postings
            } else {
                intersection.apply { retainAll(postings) }
            }
            if (intersection!!.isEmpty()) return emptyList()
        }

        val resultSet = intersection ?: return emptyList()
        val limit = min(candidateCapacity, resultSet.size)
        val results = ArrayList<Long>(limit)
        var count = 0
        for (id in resultSet) {
            results.add(id)
            count++
            if (count >= limit) break
        }
        return results
    }

    private fun getCharGramPostings(gram: String): MutableSet<Long> {
        val header = charNgramIndex[gram] ?: return mutableSetOf()
        val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
        val estimatedSize = header.recordCount.get().coerceAtLeast(1)
        val result = LinkedHashSet<Long>()
        for (id in postings.keys) {
            result.add(id)
        }
        return result
    }

    private fun incrementStat(key: String, delta: Long) {
        if (delta == 0L) return
        val current = stats[key] ?: 0L
        val updated = current + delta
        if (updated <= 0L) {
            stats.remove(key)
        } else {
            stats[key] = updated
        }
    }

    private fun totalSpanCount(): Long = stats[totalSpanCountKey] ?: 0L

    private fun totalTokenCount(): Long = stats[totalTokenCountKey] ?: 0L

    private fun updateTokenDocumentFrequency(tokens: Collection<String>, delta: Int) {
        if (tokens.isEmpty() || delta == 0) return
        for (token in tokens) {
            val current = tokenDocumentFrequency[token] ?: 0
            val updated = current + delta
            if (updated <= 0) {
                tokenDocumentFrequency.remove(token)
            } else {
                tokenDocumentFrequency[token] = updated
            }
        }
    }

    private fun updateFeatureDocumentFrequency(features: Collection<String>, delta: Int) {
        if (features.isEmpty() || delta == 0) return
        for (feature in features) {
            val current = featureDocumentFrequency[feature] ?: 0
            val updated = current + delta
            if (updated <= 0) {
                featureDocumentFrequency.remove(feature)
            } else {
                featureDocumentFrequency[feature] = updated
            }
        }
    }

    private fun gatherCandidatesFromLsh(queryVector: FloatArray, candidateCapacity: Int): LinkedHashSet<Long> {
        val candidates = LinkedHashSet<Long>(candidateCapacity.coerceAtLeast(256))
        val bitsPerTable = maxBitsPerTable
        val baseSignatures = IntArray(lshTableCount) { tableIndex ->
            calculateSignature(queryVector, planes[tableIndex], bitsPerTable)
        }

        fun probe(tableIndex: Int, signature: Int) {
            val header = tableMap(tableIndex)[signature] ?: return
            val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            for (recordId in postings.keys) {
                if (candidates.add(recordId) && candidates.size >= candidateCapacity) return
            }
        }

        for (tableIndex in 0 until lshTableCount) {
            probe(tableIndex, baseSignatures[tableIndex])
            if (candidates.size >= candidateCapacity) return candidates
        }

        val bitsToFlip = min(bitsPerTable, 16)

        if (candidates.size < candidateCapacity) {
            for (tableIndex in 0 until lshTableCount) {
                val baseSignature = baseSignatures[tableIndex]
                for (bitIndex in 0 until bitsToFlip) {
                    probe(tableIndex, baseSignature xor (1 shl bitIndex))
                    if (candidates.size >= candidateCapacity) return candidates
                }
            }
        }

        if (candidates.size < candidateCapacity) {
            for (tableIndex in 0 until lshTableCount) {
                val baseSignature = baseSignatures[tableIndex]
                for (bitIndex1 in 0 until bitsToFlip) {
                    val signatureR1 = baseSignature xor (1 shl bitIndex1)
                    for (bitIndex2 in (bitIndex1 + 1) until bitsToFlip) {
                        probe(tableIndex, signatureR1 xor (1 shl bitIndex2))
                        if (candidates.size >= candidateCapacity) return candidates
                    }
                }
            }
        }

        return candidates
    }

    private fun scoreCandidatesWithBm25(
        candidates: Collection<Long>,
        queryTokenFrequency: Map<String, Int>
    ): Map<Long, Float> {
        if (queryTokenFrequency.isEmpty()) return emptyMap()
        val spanCount = totalSpanCount()
        if (spanCount <= 0L) return emptyMap()

        val averageLength = if (spanCount > 0) totalTokenCount().toDouble() / spanCount else 0.0
        val scores = LinkedHashMap<Long, Float>()

        for (candidate in candidates) {
            val tokenFrequency = spanTokenFrequency[candidate] ?: continue
            val spanLength = spanTokenCounts[candidate] ?: tokenFrequency.values.sum()
            val score = computeBm25Score(tokenFrequency, spanLength, queryTokenFrequency, spanCount, averageLength)
            if (score > 0f) {
                scores[candidate] = score
            }
        }

        return scores
    }

    private fun computeBm25Score(
        spanTokenFrequency: Map<String, Int>,
        spanLength: Int,
        queryTokenFrequency: Map<String, Int>,
        documentCount: Long,
        averageLength: Double
    ): Float {
        if (spanLength <= 0) return 0f
        val avgLength = if (averageLength > 0.0) averageLength else spanLength.toDouble()
        var score = 0.0
        for ((token, queryFreq) in queryTokenFrequency) {
            val termFrequency = spanTokenFrequency[token] ?: continue
            val documentFrequency = tokenDocumentFrequency[token]?.toLong() ?: 0L
            if (documentFrequency == 0L) continue
            val idf = ln((documentCount + 1.0) / (documentFrequency + 1.0))
            val lengthNormalization = 1.0 - bm25B + bm25B * (spanLength / avgLength)
            val denominator = termFrequency + bm25K1 * lengthNormalization
            if (denominator == 0.0) continue
            val tfComponent = (termFrequency * (bm25K1 + 1.0)) / denominator
            score += idf * tfComponent * queryFreq
        }
        return score.toFloat()
    }

    private fun selectRerankPool(
        candidates: LinkedHashSet<Long>,
        lexicalScores: Map<Long, Float>,
        candidateCapacity: Int
    ): List<Long> {
        val rerankLimit = min(maxRerankCandidates, candidateCapacity)
        if (rerankLimit <= 0) return emptyList()
        return if (lexicalScores.isNotEmpty()) {
            lexicalScores.entries
                .sortedByDescending { it.value }
                .take(rerankLimit)
                .map { it.key }
        } else {
            takeFirst(candidates, rerankLimit)
        }
    }

    private fun takeFirst(source: Collection<Long>, limit: Int): List<Long> {
        if (limit <= 0) return emptyList()
        val results = ArrayList<Long>(min(limit, source.size))
        var count = 0
        for (id in source) {
            results.add(id)
            count++
            if (count >= limit) break
        }
        return results
    }

    private fun computeCosineScores(
        candidates: Collection<Long>,
        queryVector: FloatArray
    ): Map<Long, Float> {
        val scores = LinkedHashMap<Long, Float>()
        for (candidate in candidates) {
            val vectorBytes = vectors[candidate] ?: continue
            val recordVector = bytesToFloats(vectorBytes)
            val score = dotProduct(queryVector, recordVector)
            if (score < minReturnedScore) continue
            val meta = spanMeta[candidate]
            val documentId = meta?.documentId ?: candidate
            val current = scores[documentId]
            if (current == null || score > current) {
                scores[documentId] = score
            }
        }
        return scores
    }

    private fun sliceTextIntoWindows(rawText: String): List<SpanWindow> {
        if (rawText.isEmpty()) {
            val normalized = normalizeForIndexing(rawText)
            return if (normalized.isEmpty()) emptyList() else listOf(SpanWindow(rawText, 0, rawText.length, normalized))
        }

        val matches = tokenPattern.findAll(rawText).toList()
        if (matches.isEmpty()) {
            val normalized = normalizeForIndexing(rawText)
            return if (normalized.isEmpty()) emptyList() else listOf(SpanWindow(rawText, 0, rawText.length, normalized))
        }

        val stride = if (matches.size <= spanWindowSizeTokens) spanWindowSizeTokens else max(1, spanStrideTokens)
        val windows = ArrayList<SpanWindow>()
        var startIndex = 0
        while (startIndex < matches.size) {
            val endExclusive = min(matches.size, startIndex + spanWindowSizeTokens)
            val spanStart = matches[startIndex].range.first
            val spanEnd = if (endExclusive < matches.size) matches[endExclusive].range.first else rawText.length
            val length = (spanEnd - spanStart).coerceAtLeast(0)
            val text = if (length > 0) rawText.substring(spanStart, spanEnd) else ""
            val normalized = normalizeForIndexing(text)
            if (normalized.isNotEmpty()) {
                windows.add(SpanWindow(text, spanStart, length, normalized))
            }
            if (endExclusive >= matches.size) break
            startIndex += stride
        }

        if (windows.isEmpty()) {
            val normalized = normalizeForIndexing(rawText)
            if (normalized.isNotEmpty()) {
                windows.add(SpanWindow(rawText, 0, rawText.length, normalized))
            }
        }

        return windows
    }

    private fun prepareSpan(normalizedText: String): PreparedSpan {
        val featureFrequencies = computeFeatureFrequencies(normalizedText)
        val (tokenFrequencies, tokenCount) = computeTokenFrequencies(normalizedText)
        val charTrigrams = extractCharTrigrams(normalizedText)

        return PreparedSpan(
            normalizedText = normalizedText,
            featureFrequencies = featureFrequencies,
            uniqueFeatures = featureFrequencies.keys.toSet(),
            tokenFrequencies = tokenFrequencies,
            uniqueTokens = tokenFrequencies.keys.toSet(),
            tokenCount = tokenCount,
            charTrigrams = charTrigrams
        )
    }

    private fun computeFeatureFrequencies(normalizedText: String): Map<String, Int> {
        val features = tokenizeForEmbeddingNormalized(normalizedText)
        if (features.isEmpty()) return emptyMap()
        val frequencies = LinkedHashMap<String, Int>(features.size)
        for (feature in features) {
            frequencies[feature] = (frequencies[feature] ?: 0) + 1
        }
        return frequencies
    }

    private fun computeTokenFrequencies(normalizedText: String): Pair<Map<String, Int>, Int> {
        val tokens = tokenizeForScoring(normalizedText)
        if (tokens.isEmpty()) return emptyMap<String, Int>() to 0
        val frequencies = LinkedHashMap<String, Int>(tokens.size)
        for (token in tokens) {
            frequencies[token] = (frequencies[token] ?: 0) + 1
        }
        return frequencies to tokens.size
    }

    private fun buildVectorFromFeatures(featureFrequencies: Map<String, Int>): FloatArray {
        if (featureFrequencies.isEmpty()) return FloatArray(embeddingDimension)

        val vector = FloatArray(embeddingDimension)
        val documentCount = max(1L, totalSpanCount())
        var hasNonZeroWeight = false
        val hashedFrequencies = ArrayList<Pair<Int, Float>>(featureFrequencies.size)

        for ((feature, frequency) in featureFrequencies) {
            val bucketIndex = getBucket(feature, embeddingDimension)
            val tfWeight = (1.0 + ln(frequency.toDouble())).toFloat()
            val df = featureDocumentFrequency[feature]?.toLong() ?: 0L
            val idf = ln((documentCount + 1.0) / (df + 1.0))
            val weighted = (tfWeight * idf.toFloat())
            if (weighted != 0f) {
                vector[bucketIndex] += weighted
                hasNonZeroWeight = true
            }
            hashedFrequencies.add(bucketIndex to tfWeight)
        }

        if (!hasNonZeroWeight) {
            for ((bucketIndex, tfWeight) in hashedFrequencies) {
                vector[bucketIndex] += tfWeight
            }
        }

        l2NormalizeInPlace(vector)
        return vector
    }

    private fun extractCharTrigrams(normalizedText: String): Set<String> {
        val builder = StringBuilder(normalizedText.length)
        for (ch in normalizedText) {
            if (ch.isLetterOrDigit()) builder.append(ch)
        }
        if (builder.length < 3) return emptySet()
        val trigrams = LinkedHashSet<String>()
        val cleaned = builder.toString()
        for (index in 0..(cleaned.length - 3)) {
            trigrams.add(cleaned.substring(index, index + 3))
        }
        return trigrams
    }

    // ---------------- Helpers ----------------

    /** Creates a unique namespace for disk map storage to avoid collisions. */
    private fun namespace(suffix: String): String =
        entityDescriptor.entityClass.name + indexDescriptor.name + ":" + suffix

    /** Extracts the value of the indexed field from an entity. */
    private fun extractIndexFieldValue(entity: IManagedEntity): Any? {
        val fieldName = indexDescriptor.name
        return try {
            val field = entity.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(entity)
        } catch (_: Throwable) {
            entity.identifier(context, entityDescriptor)
        }
    }
    

    /** Loads an entity from disk by its internal record ID. */
    private fun loadEntity(recordId: Long): IManagedEntity {
        val records = context.getDataFile(entityDescriptor)
            .getHashMap<DiskMap<Any, IManagedEntity>>(
                entityDescriptor.identifier!!.type,
                entityDescriptor.entityClass.name
            )
        for (entry in records.entries) {
            if (records.getRecID(entry.key) == recordId) return entry.value
        }
        throw OnyxException("Entity with recordId=$recordId not found for ${entityDescriptor.entityClass.name}")
    }

    /** Converts an arbitrary value to a normalized float vector. */
    private fun anyToVector(value: Any?): FloatArray? = when (value) {
        null -> null
        is FloatArray -> {
            require(value.size == embeddingDimension) {
                "Vector dimension mismatch: got ${value.size}, expected $embeddingDimension"
            }
            l2Normalize(value)
        }

        is ByteArray -> {
            val floatArray = bytesToFloats(value)
            require(floatArray.size == embeddingDimension) {
                "Vector dimension mismatch: got ${floatArray.size}, expected $embeddingDimension"
            }
            l2Normalize(floatArray)
        }

        is String -> embedText(value)
        else -> embedText(value.toString())
    }

    /** Adds a record ID to the posting list associated with a given signature. */
    private fun addRecordToPostingList(recordId: Long, signature: Int, tableIndex: Int) {
        val table = tableMap(tableIndex)
        table.compute(signature) { _, existingHeader ->
            val header = existingHeader ?: dataFile.newMapHeader()
            val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            postings[recordId] = null
            // Persist changes to the header
            header.also {
                it.firstNode = postings.reference.firstNode
                it.position = postings.reference.position
                it.recordCount.set(postings.reference.recordCount.get())
            }
        }
    }

    /** Counts the number of tokens in a query value, used for gating trivial queries. */
    private fun countTokensForQuery(value: Any?): Int = when (value) {
        null -> 0
        is String -> tokenizeForQuery(value).size
        else -> tokenizeForQuery(value.toString()).size
    }

    // --- Text to Vector Embedding ---

    /**
     * Converts raw text into a normalized vector using a bag-of-words and n-grams model
     * weighted with tf-idf and feature hashing.
     */
    private fun embedText(rawText: String): FloatArray {
        val normalized = normalizeForIndexing(rawText)
        val featureFrequencies = computeFeatureFrequencies(normalized)
        if (featureFrequencies.isEmpty()) return FloatArray(embeddingDimension)
        return buildVectorFromFeatures(featureFrequencies)
    }

    /** Tokenizes text for query gating purposes (simple word splitting). */
    private fun tokenizeForQuery(text: String): List<String> =
        tokenizeForScoring(normalizeForIndexing(text))

    /** Tokenizes text for embedding, potentially including character n-grams. */
    private fun tokenizeForEmbedding(text: String): List<String> =
        tokenizeForEmbeddingNormalized(normalizeForIndexing(text))

    private fun tokenizeForEmbeddingNormalized(normalizedText: String): List<String> {
        val baseTokens = tokenizeForScoring(normalizedText)
        if (!useCharNgrams || normalizedText.length < shortQueryNgramThreshold) return baseTokens

        val features = ArrayList<String>(baseTokens.size * 2)
        features.addAll(baseTokens)
        for (word in baseTokens) {
            for (ngramLength in ngramMinLength..ngramMaxLength) {
                if (word.length >= ngramLength) {
                    for (index in 0..(word.length - ngramLength)) {
                        features.add("§$ngramLength:${word.substring(index, index + ngramLength)}")
                    }
                }
            }
        }
        return features
    }

    private fun tokenizeForScoring(normalizedText: String): List<String> {
        val tokens = ArrayList<String>()
        for (match in tokenPattern.findAll(normalizedText)) {
            val token = match.value
            if (token.length >= tokenMinLength && token !in stopWords) {
                tokens.add(token)
            }
        }
        return tokens
    }

    private fun normalizeForIndexing(rawText: String): String =
        Normalizer.normalize(rawText, Normalizer.Form.NFKC).lowercase()

    /** Hashes a feature string to a dimension index (bucket). */
    private fun getBucket(feature: String, dimension: Int): Int {
        val hash = mix64(feature.hashCode().toLong())
        return ((hash and Long.MAX_VALUE) % dimension.toLong()).toInt()
    }

    // --- LSH and Math Helpers ---

    /** Normalizes a vector to unit length (L2 norm), returning a new array. */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        val resultVector = vector.clone()
        l2NormalizeInPlace(resultVector)
        return resultVector
    }

    /** Normalizes a vector to unit length (L2 norm) in-place. */
    private fun l2NormalizeInPlace(vector: FloatArray) {
        var sumOfSquares = 0.0
        for (value in vector) sumOfSquares += value * value
        val norm = sqrt(sumOfSquares).let { if (it == 0.0) 1.0 else it }
        val inverseNorm = (1.0 / norm).toFloat()
        for (i in vector.indices) vector[i] *= inverseNorm
    }

    /** Generates an LSH signature (hash) for a vector using the given projection planes. */
    private fun calculateSignature(vector: FloatArray, projectionPlanes: Array<FloatArray>, bitsToUse: Int): Int {
        var signature = 0
        val limit = min(bitsToUse, projectionPlanes.size)
        for (index in 0 until limit) {
            val dotProduct = dotProduct(vector, projectionPlanes[index])
            if (dotProduct >= 0f) {
                signature = signature or (1 shl index)
            }
        }
        return signature
    }

    /** Creates a deterministic seed for a specific hyperplane. */
    private fun seedFor(tableIndex: Int, planeIndex: Int): Long {
        var seed = randomSeed
        seed = mix64(seed xor (tableIndex.toLong() + 0x9E0001))
        seed = mix64(seed xor (planeIndex.toLong() + 0x9F0007))
        return seed
    }

    /** Generates a random unit vector of a given dimension. */
    private fun randUnitVector(dimension: Int, seed: Long): FloatArray {
        val random = Random(seed)
        val vector = FloatArray(dimension) { random.nextFloat() * 2f - 1f }
        return l2Normalize(vector)
    }

    /** A 64-bit mixing function to improve hash quality. */
    private fun mix64(value: Long): Long {
        var mixed = value + -7046029254386353131L
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        return mixed xor (mixed ushr 31)
    }

    /** Calculates the dot product of two vectors. */
    private fun dotProduct(vectorA: FloatArray, vectorB: FloatArray): Float {
        var sum = 0f
        for (i in vectorA.indices) {
            sum += vectorA[i] * vectorB[i]
        }
        return sum
    }

    // --- Serialization Helpers ---

    /** Serializes a float array to a byte array. */
    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (floatValue in floats) byteBuffer.putFloat(floatValue)
        return byteBuffer.array()
    }

    /** Deserializes a byte array into a float array. */
    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(bytes.size / 4)
        for (i in floatArray.indices) {
            floatArray[i] = byteBuffer.getFloat()
        }
        return floatArray
    }
}
