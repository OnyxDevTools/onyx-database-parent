package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.exception.OnyxException
import com.onyx.extension.get
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.sqrt

/**
 * Native vector index interactor that provides vector similarity search capabilities
 * without requiring external libraries like Lucene.
 *
 * This implementation uses Locality-Sensitive Hashing (LSH) with random projections
 * for efficient approximate nearest neighbor (ANN) search.
 *
 * LSH works by hashing similar vectors to the same buckets with high probability,
 * allowing us to only compare vectors in the same buckets rather than scanning all vectors.
 *
 * @param entityDescriptor Descriptor for the entity being indexed.
 * @param indexDescriptor Descriptor for the specific index field.
 * @param context The schema context, used to access data files and index locations.
 *
 * @since 3.0.0
 */
class VectorIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : IndexInteractor {

    private val readWriteLock = ReentrantReadWriteLock()

    private val contextRef = WeakReference(context)

    private val context: SchemaContext
        get() = contextRef.get() ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    /**
     * Number of hash tables for LSH. More tables = better recall, but more storage.
     */
    private val numHashTables: Int = if (indexDescriptor.hashTableCount > 0) indexDescriptor.hashTableCount else DEFAULT_HASH_TABLES

    /**
     * Number of hash functions per table. More functions = more precise buckets.
     */
    private val numHashFunctions: Int = DEFAULT_HASH_FUNCTIONS

    /**
     * Expected vector dimensions
     */
    private val dimensions: Int = if (indexDescriptor.embeddingDimensions > 0) indexDescriptor.embeddingDimensions else DEFAULT_DIMENSIONS

    /**
     * Random projection vectors for LSH hashing.
     * Structure: projections[tableIndex][hashFunctionIndex] = projection vector
     */
    private val projections: Array<Array<FloatArray>> by lazy {
        initializeProjections()
    }

    /**
     * Returns the DiskMap that stores vectors keyed by record ID.
     */
    private val vectorStore: DiskMap<Long, Any>
        get() = context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java,
            entityDescriptor.entityClass.name + indexDescriptor.name + "_vectors"
        )

    /**
     * Returns hash bucket maps for each hash table.
     * Key: bucket hash (Int), Value: Header pointing to a set of record IDs
     */
    private fun getHashBucket(tableIndex: Int): DiskMap<Int, Header> {
        return context.getDataFile(entityDescriptor).getHashMap(
            Int::class.java,
            entityDescriptor.entityClass.name + indexDescriptor.name + "_lsh_table_$tableIndex"
        )
    }

    /**
     * Gets or creates a set of record IDs for a given bucket
     */
    private fun getBucketRecords(tableIndex: Int, bucketHash: Int): DiskMap<Long, Any?> = readWriteLock.write {
        val hashBucket = getHashBucket(tableIndex)
        val header = hashBucket.compute(bucketHash) { _, existingHeader ->
            existingHeader ?: context.getDataFile(entityDescriptor).newMapHeader()
        }
        return context.getDataFile(entityDescriptor).getHashMap(Long::class.java, header!!)
    }

    /**
     * Initialize random projection vectors for LSH.
     * Uses a fixed seed for reproducibility across restarts.
     */
    private fun initializeProjections(): Array<Array<FloatArray>> {
        val seed = (entityDescriptor.entityClass.name + indexDescriptor.name).hashCode().toLong()
        val random = Random(seed)
        
        return Array(numHashTables) { tableIndex ->
            Array(numHashFunctions) { 
                // Generate random unit vector for projection
                val projection = FloatArray(dimensions) { random.nextGaussian().toFloat() }
                normalize(projection)
                projection
            }
        }
    }

    /**
     * Computes the LSH hash for a vector in a specific hash table.
     * Uses random projection and sign-based hashing.
     */
    private fun computeHash(vector: FloatArray, tableIndex: Int): Int {
        var hash = 0
        val tableProjections = projections[tableIndex]
        
        for (i in 0 until numHashFunctions) {
            // Compute dot product with projection vector
            val dotProduct = dotProduct(vector, tableProjections[i])
            // Sign bit contributes to hash
            if (dotProduct >= 0) {
                hash = hash or (1 shl i)
            }
        }
        return hash
    }

    /**
     * Saves or updates a vector in the index.
     */
    @Throws(OnyxException::class)
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) = readWriteLock.write {
        // Delete the old entry if exists
        if (oldReferenceId > 0) {
            delete(oldReferenceId)
        }

        val vector = valueToVector(indexValue) ?: return
        
        // Normalize the vector for cosine similarity
        val normalizedVector = vector.copyOf()
        normalize(normalizedVector)

        // Store the normalized vector
        vectorStore[newReferenceId] = normalizedVector

        // Add to LSH hash buckets
        for (tableIndex in 0 until numHashTables) {
            val bucketHash = computeHash(normalizedVector, tableIndex)
            val bucketRecords = getBucketRecords(tableIndex, bucketHash)
            bucketRecords[newReferenceId] = null
        }
    }

    /**
     * Deletes a vector from the index.
     */
    @Throws(OnyxException::class)
    override fun delete(reference: Long) = readWriteLock.write {
        if (reference <= 0) return@write

        val storedVector = vectorStore[reference] as? FloatArray
        if (storedVector != null) {
            // Remove from all LSH hash buckets
            for (tableIndex in 0 until numHashTables) {
                val bucketHash = computeHash(storedVector, tableIndex)
                val bucketRecords = getBucketRecords(tableIndex, bucketHash)
                bucketRecords.remove(reference)
            }
        }

        vectorStore.remove(reference)
    }

    /**
     * Find all - delegates to matchAll for vector indexes.
     */
    @Throws(OnyxException::class)
    override fun findAll(indexValue: Any?): Map<Long, Any?> {
        return matchAll(indexValue, DEFAULT_LIMIT, DEFAULT_MAX_CANDIDATES)
    }

    /**
     * Find all index values.
     */
    @Throws(OnyxException::class)
    override fun findAllValues(): Set<Any> = readWriteLock.read { vectorStore.keys.mapTo(HashSet()) { it as Any } }

    /**
     * Not applicable for vector indexes.
     */
    @Throws(OnyxException::class)
    override fun findAllAbove(indexValue: Any?, includeValue: Boolean): Set<Long> = emptySet()

    /**
     * Not applicable for vector indexes.
     */
    @Throws(OnyxException::class)
    override fun findAllBelow(indexValue: Any?, includeValue: Boolean): Set<Long> = emptySet()

    /**
     * Not applicable for vector indexes.
     */
    override fun findAllBetween(fromValue: Any?, includeFromValue: Boolean, toValue: Any?, includeToValue: Boolean): Set<Long> = emptySet()

    /**
     * Performs approximate K-Nearest Neighbors search using LSH.
     *
     * 1. Hash the query vector into buckets for each hash table
     * 2. Collect candidate vectors from those buckets
     * 3. Compute exact similarity only for candidates
     * 4. Return top K results
     */
    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> = readWriteLock.read {
        val queryVector = valueToVector(indexValue) ?: return emptyMap()
        val minimumScore = if (indexDescriptor.minimumScore >= 0) indexDescriptor.minimumScore else DEFAULT_MIN_SCORE

        // Normalize query vector
        val normalizedQuery = queryVector.copyOf()
        normalize(normalizedQuery)

        // Collect candidates from all hash tables
        val candidates = HashSet<Long>()
        for (tableIndex in 0 until numHashTables) {
            val bucketHash = computeHash(normalizedQuery, tableIndex)
            val bucketRecords = getBucketRecords(tableIndex, bucketHash)
            candidates.addAll(bucketRecords.keys)
            
            // Stop if we have enough candidates
            if (candidates.size >= maxCandidates) break
        }

        // Compute exact similarities for candidates only
        val scores = mutableListOf<Pair<Long, Float>>()
        for (recordId in candidates) {
            val storedVector = vectorStore[recordId] as? FloatArray ?: continue
            
            // Since vectors are already normalized, dot product = cosine similarity
            val similarity = dotProduct(normalizedQuery, storedVector)
            
            if (similarity >= minimumScore) {
                scores.add(recordId to similarity)
            }
        }

        // Sort by similarity (descending) and take top K
        scores.sortByDescending { it.second }
        val topK = scores.take(limit)

        // Return as LinkedHashMap to preserve order
        val results = LinkedHashMap<Long, Any?>(topK.size)
        for ((recordId, score) in topK) {
            results[recordId] = score
        }
        return results
    }

    /**
     * Rebuilds the LSH index from the vector store.
     */
    @Throws(OnyxException::class)
    override fun rebuild() = readWriteLock.write {
        // Clear all LSH buckets
        for (tableIndex in 0 until numHashTables) {
            getHashBucket(tableIndex).clear()
        }

        // Rebuild from entity records
        val dataFile = context.getDataFile(entityDescriptor)
        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(
            entityDescriptor.identifier!!.type,
            entityDescriptor.entityClass.name
        )

        vectorStore.clear()

        for (entry in records.entries) {
            val recId = records.getRecID(entry.key)
            if (recId <= 0) continue

            val value = entry.value.get<Any?>(context, entityDescriptor, indexDescriptor.name)
            if (value != null) {
                save(value, 0, recId)
            }
        }
    }

    /**
     * Clears all vectors and LSH buckets.
     */
    override fun clear() = readWriteLock.write {
        vectorStore.clear()
        for (tableIndex in 0 until numHashTables) {
            getHashBucket(tableIndex).clear()
        }
    }

    /**
     * Shutdown - no resources to release.
     */
    override fun shutdown() {
        // DiskMap handles its own cleanup
    }

    /**
     * Computes dot product of two vectors.
     */
    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /**
     * Normalizes a vector in-place to unit length.
     */
    private fun normalize(vector: FloatArray) {
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val magnitude = sqrt(sumSquares)
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
    }

    /**
     * Converts various numerical types to a FloatArray.
     */
    private fun valueToVector(value: Any?): FloatArray? {
        if (value == null) return null

        return try {
            when (value) {
                is FloatArray -> value
                is DoubleArray -> FloatArray(value.size) { i -> value[i].toFloat() }
                is IntArray -> FloatArray(value.size) { i -> value[i].toFloat() }
                is LongArray -> FloatArray(value.size) { i -> value[i].toFloat() }
                is Array<*> -> {
                    if (value.isEmpty()) return null
                    val first = value[0]
                    if (first !is Number) return null
                    FloatArray(value.size) { i -> (value[i] as Number).toFloat() }
                }
                is Collection<*> -> {
                    if (value.isEmpty()) return null
                    val list = value.toList()
                    val first = list[0]
                    if (first !is Number) return null
                    FloatArray(list.size) { i -> (list[i] as Number).toFloat() }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 50
        private const val DEFAULT_MAX_CANDIDATES = 1000
        private const val DEFAULT_MIN_SCORE = 0.0f
        private const val DEFAULT_HASH_TABLES = 8
        private const val DEFAULT_HASH_FUNCTIONS = 12
        private const val DEFAULT_DIMENSIONS = 256
    }
}

fun FloatArray.similarity(vectorB: FloatArray): Float {
    if (this.size != vectorB.size) return 0f

    var dotProduct = 0f
    var normA = 0f
    var normB = 0f

    for (i in this.indices) {
        dotProduct += this[i] * vectorB[i]
        normA += this[i] * this[i]
        normB += vectorB[i] * vectorB[i]
    }

    val magnitude = sqrt(normA) * sqrt(normB)
    return if (magnitude > 0) dotProduct / magnitude else 0f
}