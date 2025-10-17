package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.extension.identifier
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.LinkedHashSet
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
    private val embeddingDimension: Int = if (indexDescriptor.embeddingDimensions > 0) indexDescriptor.embeddingDimensions else 512

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

    /** Stores a set of all indexed record IDs, used for fallback searches. */
    private val allRecordIds: DiskMap<Long, Byte?>
        get() = dataFile.getHashMap(Long::class.java, namespace("all_ids"))

    /** Pre-computed random hyperplanes for LSH, generated deterministically from the seed. */
    private val planes: Array<Array<FloatArray>> = Array(lshTableCount) { tableIndex ->
        Array(maxBitsPerTable) { planeIndex ->
            randUnitVector(embeddingDimension, seedFor(tableIndex, planeIndex))
        }
    }

    // ---------------- Lifecycle ----------------

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        super.save(indexValue, oldReferenceId, newReferenceId)

        if (oldReferenceId > 0L) deleteVectorOnly(oldReferenceId)

        val valueForIndex = indexValue ?: extractIndexFieldValue(loadEntity(newReferenceId))
        val vector = anyToVector(valueForIndex) ?: return
        require(vector.size == embeddingDimension) {
            "Vector dimension mismatch: got ${vector.size}, expected $embeddingDimension"
        }

        vectors[newReferenceId] = floatsToBytes(vector)
        allRecordIds[newReferenceId] = null

        for (tableIndex in 0 until lshTableCount) {
            val signature = calculateSignature(vector, planes[tableIndex], maxBitsPerTable)
            inverseTableMap(tableIndex)[newReferenceId] = signature
            addRecordToPostingList(newReferenceId, signature, tableIndex)
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
                val vector = anyToVector(indexValue) ?: return@forEach
                vectors[recordId] = floatsToBytes(vector)
                allRecordIds[recordId] = null

                for (tableIndex in 0 until lshTableCount) {
                    val signature = calculateSignature(vector, planes[tableIndex], maxBitsPerTable)
                    inverseTableMap(tableIndex)[recordId] = signature
                    addRecordToPostingList(recordId, signature, tableIndex)
                }
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
        val candidates = LinkedHashSet<Long>(candidateCapacity.coerceAtLeast(1024))

        // The number of bits used for probing must match the number used at index time.
        val bitsPerTable = maxBitsPerTable

        val baseSignatures = IntArray(lshTableCount) { tableIndex ->
            calculateSignature(queryVector, planes[tableIndex], bitsPerTable)
        }

        // Helper to probe a bucket and add results to the candidate set.
        fun probe(tableIndex: Int, signature: Int) {
            val header = tableMap(tableIndex)[signature] ?: return
            val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            for (recordId in postings.keys) {
                if (candidates.add(recordId) && candidates.size >= candidateCapacity) return
            }
        }

        // --- LSH Probing Stage 1: Exact Buckets ---
        for (tableIndex in 0 until lshTableCount) {
            probe(tableIndex, baseSignatures[tableIndex])
            if (candidates.size >= candidateCapacity) break
        }

        // --- LSH Probing Stage 2 & 3: Multi-probe adjacent buckets ---
        // Explore buckets within a small Hamming distance to find near misses.
        val bitsToFlip = min(bitsPerTable, 16) // Cap flips for performance.

        // Hamming distance r=1
        if (candidates.size < candidateCapacity) {
            for (tableIndex in 0 until lshTableCount) {
                val baseSignature = baseSignatures[tableIndex]
                for (bitIndex in 0 until bitsToFlip) {
                    probe(tableIndex, baseSignature xor (1 shl bitIndex))
                    if (candidates.size >= candidateCapacity) break
                }
                if (candidates.size >= candidateCapacity) break
            }
        }

        // Hamming distance r=2
        if (candidates.size < candidateCapacity) {
            for (tableIndex in 0 until lshTableCount) {
                val baseSignature = baseSignatures[tableIndex]
                for (bitIndex1 in 0 until bitsToFlip) {
                    val signatureR1 = baseSignature xor (1 shl bitIndex1)
                    for (bitIndex2 in (bitIndex1 + 1) until bitsToFlip) {
                        probe(tableIndex, signatureR1 xor (1 shl bitIndex2))
                        if (candidates.size >= candidateCapacity) break
                    }
                    if (candidates.size >= candidateCapacity) break
                }
                if (candidates.size >= candidateCapacity) break
            }
        }

        if (candidates.isEmpty()) return emptyMap()

        // --- Scoring and Ranking Stage ---
        // Use a priority queue to find the top-k candidates based on cosine similarity.
        val topCandidatesQueue = java.util.PriorityQueue<Pair<Float, Long>>(limit, compareBy { it.first })
        for (recordId in candidates) {
            val vectorBytes = vectors[recordId] ?: continue
            val recordVector = bytesToFloats(vectorBytes)
            val score = dotProduct(queryVector, recordVector)

            if (topCandidatesQueue.size < limit) {
                topCandidatesQueue.offer(score to recordId)
            } else if (score > topCandidatesQueue.peek().first) {
                topCandidatesQueue.poll()
                topCandidatesQueue.offer(score to recordId)
            }
        }

        if (topCandidatesQueue.isEmpty() || topCandidatesQueue.maxByOrNull { it.first }!!.first < minReturnedScore) {
            return emptyMap()
        }

        val sortedResults = ArrayList<Pair<Float, Long>>(topCandidatesQueue.size)
        while (topCandidatesQueue.isNotEmpty()) sortedResults.add(topCandidatesQueue.poll())
        sortedResults.sortByDescending { it.first }

        val results = LinkedHashMap<Long, Any?>(sortedResults.size)
        for ((score, recordId) in sortedResults) {
            if (score >= minReturnedScore) results[recordId] = score
        }
        return results
    }

    // ---------------- Vector-Only Maintenance ----------------

    /** Deletes all vector-related data for a given record ID without touching the main index. */
    private fun deleteVectorOnly(referenceId: Long) {
        if (referenceId <= 0L) return
        for (tableIndex in 0 until lshTableCount) {
            val inverseTable = inverseTableMap(tableIndex)
            val signature = inverseTable.remove(referenceId) ?: continue

            val table = tableMap(tableIndex)
            table.computeIfPresent(signature) { _, header ->
                val postings: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header!!)
                postings.remove(referenceId)
                // Persist changes to the header
                header.also {
                    it.firstNode = postings.reference.firstNode
                    it.position = postings.reference.position
                    it.recordCount.set(postings.reference.recordCount.get())
                }
            }
        }
        vectors.remove(referenceId)
        allRecordIds.remove(referenceId)
    }

    /** Clears all vector-related data from the index. */
    private fun clearVectorOnly() {
        vectors.clear()
        allRecordIds.clear()
        for (tableIndex in 0 until lshTableCount) {
            tableMap(tableIndex).clear()
            inverseTableMap(tableIndex).clear()
        }
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
        is String -> tokenizeForQuery(value.lowercase()).size
        else -> tokenizeForQuery(value.toString().lowercase()).size
    }

    // --- Text to Vector Embedding ---

    /**
     * Converts raw text into a normalized vector using a bag-of-words and n-grams model.
     * Each token/n-gram hashes to a dimension and contributes +1 or -1 to it.
     */
    private fun embedText(rawText: String): FloatArray {
        val text = rawText.lowercase()
        val tokens = tokenizeForEmbedding(text)
        if (tokens.isEmpty()) return FloatArray(embeddingDimension)

        val vector = FloatArray(embeddingDimension)
        for (token in tokens) {
            val bucketIndex = getBucket(token, embeddingDimension)
            vector[bucketIndex] += getSign(token)
        }
        l2NormalizeInPlace(vector)
        return vector
    }

    /** Tokenizes text for query gating purposes (simple word splitting). */
    private fun tokenizeForQuery(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= tokenMinLength }

    /** Tokenizes text for embedding, potentially including character n-grams. */
    private fun tokenizeForEmbedding(text: String): List<String> {
        val baseTokens = tokenizeForQuery(text)
        val useNgrams = useCharNgrams && text.length >= shortQueryNgramThreshold
        if (!useNgrams) return baseTokens

        val ngrams = ArrayList<String>(baseTokens.size * 2)
        for (word in baseTokens) {
            for (ngramLength in ngramMinLength..ngramMaxLength) {
                if (word.length >= ngramLength) {
                    for (index in 0..(word.length - ngramLength)) {
                        ngrams.add("§$ngramLength:${word.substring(index, index + ngramLength)}")
                    }
                }
            }
        }
        return baseTokens + ngrams
    }

    /** Hashes a feature string to a dimension index (bucket). */
    private fun getBucket(feature: String, dimension: Int): Int {
        val hash = mix64(feature.hashCode().toLong())
        return ((hash and Long.MAX_VALUE) % dimension.toLong()).toInt()
    }

    /** Determines the sign (+1.0f or -1.0f) for a feature's contribution to its bucket. */
    private fun getSign(feature: String): Float {
        val hash = mix64(feature.hashCode().toLong() xor 0x9E3779B97F4A7L)
        return if ((hash and 1L) == 0L) 1.0f else -1.0f
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
