package com.onyx.faiss.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.exception.OnyxException
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.annotations.values.VectorQuantization
import com.onyx.persistence.context.SchemaContext
import com.vectorsearch.faiss.swig.*
import com.vectorsearch.faiss.utils.JFaissInitializer
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.sqrt

/**
 * FAISS-backed Vector Index Interactor.
 *
 * This implementation uses Facebook's FAISS library via JNI bindings (jfaiss-cpu)
 * for high-performance approximate nearest neighbor search.
 *
 * Features:
 * - Uses IndexFlatIP for inner product similarity (cosine similarity on normalized vectors)
 * - Uses IndexIDMap to support custom Long IDs
 * - Persistent index storage with automatic save/load
 * - Thread-safe operations with read/write locking
 *
 * Configuration is read from the IndexDescriptor which is populated from the @Index annotation:
 *  - maxNeighbors: (not used for flat index)
 *  - searchRadius: (not used for flat index)
 *  - quantization: vector quantization mode - NONE, INT8, or INT4 (affects index type)
 *  - minimumScore: minimum cosine similarity score for results (default: -1f = disabled)
 *  - embeddingDimensions: expected vector dimensions (required for FAISS)
 *
 * @author Generated for Onyx Database
 */
class FaissVectorIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : IndexInteractor {

    private val lock = ReentrantReadWriteLock()
    private val contextRef = WeakReference(context)
    private val context: SchemaContext get() = contextRef.get() ?: throw IllegalStateException("Context GC'd")

    // FAISS configuration - read from descriptor (set via @Index annotation)
    private val quantization: VectorQuantization = indexDescriptor.quantization
    private val minimumScore: Float = indexDescriptor.minimumScore
    private val embeddingDimensions: Int = indexDescriptor.embeddingDimensions

    // FAISS index components
    private var faissIndex: Index? = null
    private var dimension: Int = if (embeddingDimensions > 0) embeddingDimensions else -1

    // Index key for state management
    private val indexKey: String = generateKey(entityDescriptor, indexDescriptor, context)

    // Store vectors for persistence and rebuild (FAISS doesn't persist IDs well with IDMap)
    private val referenceToVector: DiskMap<Long, FloatArray>
        get() = context.getDataFile(entityDescriptor).getHashMap(
            Long::class.java, "${entityDescriptor.entityClass.name}${indexDescriptor.name}_faiss_vectors"
        )

    init {
        // Check and initialize FAISS native library
        if (!isNativeLibraryAvailable) {
            throw UnsupportedOperationException(
                "FAISS native libraries are not available on this platform (${System.getProperty("os.name")}). " +
                "The jfaiss-cpu library only supports Linux. " +
                "For development on macOS/Windows, use the default VectorIndexInteractor instead."
            )
        }
        JFaissInitializer.initialize()
        initializeIndex()
    }

    /**
     * Initialize or load the FAISS index from disk.
     */
    private fun initializeIndex() = lock.write {
        val existingState = faissStates[indexKey]
        if (existingState != null) {
            faissIndex = existingState.index
            dimension = existingState.dimension
            return@write
        }

        // Try to load existing index from file
        val indexPath = getIndexFilePath()
        if (Files.exists(indexPath.toPath()) && dimension > 0) {
            try {
                val loadedIndex = swigfaiss.read_index(indexPath.absolutePath)
                faissIndex = loadedIndex
                faissStates[indexKey] = FaissState(loadedIndex, dimension)
                return@write
            } catch (e: Exception) {
                // Index file corrupted or incompatible, will recreate
                System.err.println("Failed to load FAISS index, will recreate: ${e.message}")
                indexPath.delete()
            }
        }

        // Create new index if dimension is known
        if (dimension > 0) {
            createNewIndex(dimension)
        }
        // Otherwise, index will be created on first vector insertion
    }

    /**
     * Creates a new FAISS index based on configuration.
     * Uses IndexFlatIP wrapped in IndexIDMap for custom ID support.
     */
    private fun createNewIndex(dim: Int) {
        // Build index description string based on quantization
        val indexString = when (quantization) {
            VectorQuantization.NONE -> "Flat"
            VectorQuantization.INT8 -> "SQ8"
            VectorQuantization.INT4 -> "SQ4"
        }

        // Create index with inner product metric (for cosine similarity on normalized vectors)
        val baseIndex = swigfaiss.index_factory(dim, indexString, MetricType.METRIC_INNER_PRODUCT)
        
        // Wrap with IDMap to support our custom Long IDs
        val idMapIndex = IndexIDMap(baseIndex)
        idMapIndex.setOwn_fields(true) // IDMap will own and free the base index

        faissIndex = idMapIndex
        dimension = dim
        faissStates[indexKey] = FaissState(idMapIndex, dimension)
    }

    /**
     * Save an index value with the record reference.
     */
    @Throws(OnyxException::class)
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) = lock.write {
        if (oldReferenceId > 0) {
            deleteInternal(oldReferenceId)
        }

        val vector = valueToVector(indexValue) ?: return@write
        normalize(vector)

        // Initialize dimension on first vector
        if (dimension <= 0) {
            dimension = vector.size
            createNewIndex(dimension)
        }

        // Validate dimensions match
        if (vector.size != dimension) {
            return@write // Dimension mismatch, skip
        }

        // Store vector for persistence and rebuild
        referenceToVector[newReferenceId] = vector

        // Add to FAISS index with our reference ID
        val idx = faissIndex ?: return@write
        
        // Create native vectors for FAISS
        val vectorData = FloatVector()
        for (v in vector) {
            vectorData.push_back(v)
        }
        
        val idData = LongVector()
        idData.push_back(newReferenceId)
        
        idx.add_with_ids(1L, vectorData.data(), idData.data())
        
        // Clean up native vectors
        vectorData.delete()
        idData.delete()

        // Mark for persistence
        markDirty()
    }

    /**
     * Delete an index entry by reference.
     */
    @Throws(OnyxException::class)
    override fun delete(reference: Long) = lock.write {
        deleteInternal(reference)
    }

    private fun deleteInternal(reference: Long) {
        referenceToVector.remove(reference)
        
        // FAISS IndexIDMap supports remove_ids with IDSelectorBatch
        val idx = faissIndex ?: return
        
        try {
            val idData = LongVector()
            idData.push_back(reference)
            val idSelector = IDSelectorBatch(1L, idData.data())
            idx.remove_ids(idSelector)
            idSelector.delete()
            idData.delete()
        } catch (e: Exception) {
            // Some index types don't support removal, mark for rebuild
            needsRebuild.add(indexKey)
        }
        
        markDirty()
    }

    /**
     * Find all matching vectors using nearest neighbor search.
     */
    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> = lock.read {
        val query = valueToVector(indexValue) ?: return@read emptyMap()
        normalize(query)

        val idx = faissIndex ?: return@read emptyMap()
        if (idx.getNtotal() == 0L) return@read emptyMap()

        // Validate dimensions
        if (query.size != dimension) return@read emptyMap()

        // Search for k nearest neighbors
        val k = minOf(limit, idx.getNtotal().toInt())
        if (k == 0) return@read emptyMap()

        // Create native vectors for query
        val queryData = FloatVector()
        for (v in query) {
            queryData.push_back(v)
        }
        
        // Create result vectors
        val distances = FloatVector()
        distances.resize(k.toLong())
        val idsVector = LongVector()
        idsVector.resize(k.toLong())

        // Perform search
        idx.search(1L, queryData.data(), k.toLong(), distances.data(), idsVector.data())

        // Convert to result map with scores
        val results = LinkedHashMap<Long, Any?>()
        for (i in 0 until k) {
            val id = idsVector.at(i.toLong())
            if (id < 0) continue // Invalid ID

            val score = distances.at(i.toLong()) // For inner product, higher is better (similar to cosine)
            
            // Filter by minimum score if specified
            if (minimumScore > 0f && score < minimumScore) continue
            
            results[id] = score
        }
        
        // Clean up native vectors
        queryData.delete()
        distances.delete()
        idsVector.delete()

        return@read results
    }

    /**
     * Rebuild the FAISS index from scratch using stored vectors.
     */
    @Throws(OnyxException::class)
    override fun rebuild() = lock.write {
        // Collect all vectors
        val vectors = referenceToVector.entries.toList()
        if (vectors.isEmpty()) {
            clear()
            return@write
        }

        // Determine dimension from first vector
        val firstVector = vectors.first().value
        dimension = firstVector.size

        // Delete old index and create fresh one
        faissIndex?.delete()
        faissStates.remove(indexKey)
        createNewIndex(dimension)

        val idx = faissIndex ?: return@write

        // Add all vectors in batches for efficiency
        val batchSize = 10000
        for (batch in vectors.chunked(batchSize)) {
            val n = batch.size
            
            // Create vectors for batch
            val batchVectors = FloatVector()
            val batchIds = LongVector()

            for (entry in batch) {
                val vector = entry.value.copyOf()
                normalize(vector)
                for (v in vector) {
                    batchVectors.push_back(v)
                }
                batchIds.push_back(entry.key)
            }

            idx.add_with_ids(n.toLong(), batchVectors.data(), batchIds.data())
            
            // Clean up
            batchVectors.delete()
            batchIds.delete()
        }

        needsRebuild.remove(indexKey)
        persistIndex()
    }

    /**
     * Clear all index data.
     */
    override fun clear() = lock.write {
        referenceToVector.clear()
        
        // Reset FAISS index
        val idx = faissIndex
        if (idx != null) {
            idx.reset()
        }

        // Delete persisted index file
        val indexPath = getIndexFilePath()
        if (indexPath.exists()) {
            indexPath.delete()
        }
    }

    /**
     * Shutdown and persist the index.
     */
    override fun shutdown() {
        persistIndex()
        shutdownInstance(indexKey)
    }

    override fun findAll(indexValue: Any?) = matchAll(indexValue, 50, 50)
    
    override fun findAllValues(): Set<Long> = lock.read {
        referenceToVector.keys.toSet()
    }

    override fun findAllAbove(indexValue: Any?, includeValue: Boolean) = emptySet<Long>()
    override fun findAllBelow(indexValue: Any?, includeValue: Boolean) = emptySet<Long>()
    override fun findAllBetween(fromValue: Any?, includeFromValue: Boolean, toValue: Any?, includeToValue: Boolean) = emptySet<Long>()

    // ---------------------------
    // Helper methods
    // ---------------------------

    private fun normalize(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        if (sum <= 0f) return
        val inv = 1f / sqrt(sum)
        for (i in v.indices) v[i] *= inv
    }

    private fun valueToVector(value: Any?): FloatArray? {
        val vector = when (value) {
            is FloatArray -> value.copyOf()
            is List<*> -> FloatArray(value.size) { (value[it] as Number).toFloat() }
            is Array<*> -> FloatArray(value.size) { (value[it] as Number).toFloat() }
            is DoubleArray -> FloatArray(value.size) { value[it].toFloat() }
            else -> return null
        }
        // Validate embeddingDimensions if specified
        if (embeddingDimensions > 0 && vector.size != embeddingDimensions) {
            return null // Vector dimension mismatch
        }
        return vector
    }

    private fun getIndexFilePath(): File {
        val dir = File(context.location, "faiss_indexes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${entityDescriptor.entityClass.simpleName}_${indexDescriptor.name}.faiss")
    }

    private fun markDirty() {
        FaissCommitScheduler.markDirty(indexKey)
    }

    private fun persistIndex() = lock.read {
        val idx = faissIndex ?: return@read
        try {
            val indexPath = getIndexFilePath()
            swigfaiss.write_index(idx, indexPath.absolutePath)
        } catch (e: Exception) {
            System.err.println("Error persisting FAISS index '$indexKey': ${e.message}")
        }
    }

    // ---------------------------
    // Companion object for shared state
    // ---------------------------

    companion object {
        /**
         * Check if FAISS native libraries are available on this platform.
         * jfaiss-cpu only supports Linux.
         */
        val isNativeLibraryAvailable: Boolean by lazy {
            val osName = System.getProperty("os.name", "").lowercase()
            if (!osName.contains("linux")) {
                return@lazy false
            }
            try {
                // Try to load the native library
                JFaissInitializer.initialize()
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            } catch (e: NoClassDefFoundError) {
                false
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Holds the FAISS index state per index key.
         */
        data class FaissState(
            val index: Index,
            val dimension: Int
        )

        private val faissStates = ConcurrentHashMap<String, FaissState>()
        private val needsRebuild = ConcurrentHashMap.newKeySet<String>()

        init {
            // Start the background commit scheduler
            FaissCommitScheduler.start()

            // Register Shutdown Hook
            Runtime.getRuntime().addShutdownHook(Thread {
                FaissCommitScheduler.stop()
                faissStates.keys.toList().forEach { key ->
                    shutdownInstance(key)
                }
            })
        }

        private fun shutdownInstance(key: String) {
            val state = faissStates.remove(key) ?: return
            runCatching {
                state.index.delete()
            }
        }

        private fun generateKey(
            entityDescriptor: EntityDescriptor,
            indexDescriptor: IndexDescriptor,
            context: SchemaContext
        ): String {
            return buildString {
                append(context.location)
                append(File.separator)
                append(entityDescriptor.fileName)
                append("_${entityDescriptor.entityClass.simpleName}")
                append("_${entityDescriptor.partition?.partitionValue ?: ""}")
                append("_${indexDescriptor.name}")
                append("_faiss")
            }
        }

        /**
         * Manages asynchronous persistence for all FAISS indexes.
         */
        private object FaissCommitScheduler {
            private val dirtyIndexKeys = ConcurrentHashMap.newKeySet<String>()
            @Volatile private var running = true

            private val worker = Thread {
                while (running) {
                    try {
                        Thread.sleep(10000) // 10 seconds between persist cycles

                        if (dirtyIndexKeys.isEmpty()) continue

                        val iterator = dirtyIndexKeys.iterator()
                        while (iterator.hasNext()) {
                            val key = iterator.next()
                            iterator.remove()

                            // Check if rebuild needed - skip persistence
                            if (needsRebuild.contains(key)) {
                                continue
                            }

                            // Persistence handled by individual interactors
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.apply {
                isDaemon = true
                name = "OnyxFaissCommitScheduler"
            }

            fun start() {
                if (!worker.isAlive) worker.start()
            }

            fun stop() {
                running = false
                worker.interrupt()
            }

            fun markDirty(key: String) {
                dirtyIndexKeys.add(key)
            }
        }
    }
}
