package com.onyx.lucene.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.exception.OnyxException
import com.onyx.extension.get
import com.onyx.interactors.index.impl.DefaultIndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StringField
import org.apache.lucene.index.*
import org.apache.lucene.search.ControlledRealTimeReopenThread
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.TieredMergePolicy
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path

/**
 * Holds the shared, thread-safe Lucene components for a specific index.
 * These components are expensive to create and are shared across all interactor
 * instances pointing to the same physical index path.
 *
 * @property indexWriter The central component for adding, updating, and deleting documents.
 * @property searcherManager Manages [IndexSearcher] instances, handling reopening for NRT search.
 * @property reopenThread A background thread that periodically reopens the [searcherManager] to make recent changes visible.
 * @property indexWriterConfig The configuration used to create the [indexWriter].
 */
data class LuceneVectorState(
    val indexWriter: IndexWriter,
    val searcherManager: SearcherManager,
    val reopenThread: ControlledRealTimeReopenThread<IndexSearcher>,
    val indexWriterConfig: IndexWriterConfig
)

/**
 * A Lucene-backed implementation of [DefaultIndexInteractor] that provides Vector (KNN) search
 * capabilities for indexed fields.
 *
 * This implementation acts as a vector store, accepting arrays/lists of numbers, converting them
 * to FloatArrays, and indexing them using [KnnFloatVectorField].
 *
 * @param entityDescriptor Descriptor for the entity being indexed.
 * @param indexDescriptor Descriptor for the specific index field.
 * @param context The schema context, used to access data files and index locations.
 * @throws OnyxException if the index directory cannot be created.
 */
class VectorIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : DefaultIndexInteractor(entityDescriptor, indexDescriptor, context) {

    /**
     * Holds a [WeakReference] to the [SchemaContext] to prevent this interactor
     * from causing a memory leak by holding onto a closed schema.
     */
    private val contextRef = WeakReference(context)

    /**
     * Safely retrieves the [SchemaContext], throwing an [IllegalStateException]
     * if the context has already been garbage collected.
     */
    private val context: SchemaContext
        get() = contextRef.get() ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    private lateinit var directory: Directory
    private lateinit var indexWriter: IndexWriter
    private lateinit var searcherManager: SearcherManager

    /**
     * Near-Real-Time (NRT) background thread.
     */
    private lateinit var reopenThread: ControlledRealTimeReopenThread<IndexSearcher>

    /**
     * A counter to track updates since the last explicit commit.
     * Shared across interactors for the same index.
     */
    private lateinit var updateCountSinceCommit: AtomicLong

    init {
        hydrateStates()
    }

    /**
     * Initializes or retrieves the shared [LuceneVectorState] for this specific index.
     */
    private fun hydrateStates() {
        val key = generateKey(entityDescriptor, indexDescriptor, context)
        val luceneState = luceneStates.computeIfAbsent(key) {
            directory = createDirectory(key)
            // Configuration optimized for write throughput
            val writerConfig = IndexWriterConfig().apply {
                openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
                ramBufferSizeMB = 256.0
                useCompoundFile = false
                mergePolicy = (mergePolicy as TieredMergePolicy).apply {
                    segmentsPerTier = 10.0
                    maxMergeAtOnce = 10
                    floorSegmentMB = 16.0
                }
            }

            val writer = IndexWriter(directory, writerConfig)
            val manager = SearcherManager(writer, null)

            val thread = ControlledRealTimeReopenThread(
                writer,
                manager,
                5.0,
                0.1
            ).apply {
                isDaemon = true
                name = "lucene-vector-nrt-reopen-$key"
                start()
            }

            LuceneVectorState(writer, manager, thread, writerConfig)
        }

        this.indexWriter = luceneState.indexWriter
        this.searcherManager = luceneState.searcherManager
        this.reopenThread = luceneState.reopenThread
        this.updateCountSinceCommit = sharedUpdateCounters.getOrPut(key) { AtomicLong(0L) }
    }

    /**
     * Saves or updates an entity in the Lucene vector index.
     *
     * @param indexValue The value of the field being indexed. Must be convertible to FloatArray.
     * @param oldReferenceId The previous record ID.
     * @param newReferenceId The new record ID for the entity.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        super.save(indexValue, oldReferenceId, newReferenceId)
        updateDocument(newReferenceId, indexValue)
    }

    /**
     * Deletes an entity from the Lucene index using its record ID.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        super.delete(reference)
        indexWriter.deleteDocuments(Term(ID_FIELD, reference.toString()))
    }

    /**
     * Triggers a full rebuild of the Lucene index.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun rebuild() {
        super.rebuild()
        rebuildLuceneIndex()
    }

    /**
     * Clears all documents from the Lucene index.
     */
    @Synchronized
    override fun clear() {
        super.clear()
        indexWriter.deleteAll()
    }

    /**
     * Performs a K-Nearest Neighbors (KNN) search against the Lucene index.
     *
     * @param indexValue The query vector (List, Array, etc.).
     * @param limit The maximum number of nearest neighbors to return (K).
     * @param maxCandidates (Not used explicitly, Lucene handles candidate selection).
     * @return A map of [Long, Any?] where the key is the matching entity's record ID
     * and the value is the similarity score (Float).
     */
    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> {
        val queryVector = valueToVector(indexValue) ?: return emptyMap()

        // Ensure the searcher is up-to-date with recent writes
        searcherManager.maybeRefresh()

        val searcher = searcherManager.acquire()
        try {
            // Create KNN Query
            val knnQuery = KnnFloatVectorQuery(CONTENT_FIELD, queryVector, limit)

            val topDocs = searcher.search(knnQuery, limit)
            val results = LinkedHashMap<Long, Any?>(topDocs.scoreDocs.size)

            for (scoreDoc in topDocs.scoreDocs) {
                val documentId = scoreDoc.doc
                val document = searcher.storedFields().document(documentId, setOf(ID_FIELD))
                val recordId = document.get(ID_FIELD)?.toLongOrNull() ?: continue
                results[recordId] = scoreDoc.score
            }
            return results
        } finally {
            searcherManager.release(searcher)
        }
    }

    private fun rebuildLuceneIndex() {
        indexWriter.deleteAll()

        val records = context.getDataFile(entityDescriptor)
            .getHashMap<DiskMap<Any, IManagedEntity>>(
                entityDescriptor.identifier!!.type,
                entityDescriptor.entityClass.name
            )

        val documentBatch = ArrayList<Document>(8192)
        for (entry in records.entries) {
            val recordId = records.getRecID(entry.key)
            if (recordId <= 0) continue
            val value = entry.value.get<Any?>(context, entityDescriptor, indexDescriptor.name) ?: continue

            val vector = valueToVector(value)
            if (vector != null) {
                documentBatch.add(createDocument(recordId, vector))
            }

            if (documentBatch.size >= 8192) {
                indexWriter.addDocuments(documentBatch)
                documentBatch.clear()
            }
        }
        if (documentBatch.isNotEmpty()) indexWriter.addDocuments(documentBatch)

        indexWriter.commit()
        searcherManager.maybeRefreshBlocking()
    }

    private fun updateDocument(recordId: Long, value: Any?) {
        val vector = valueToVector(value)

        if (vector == null) {
            indexWriter.deleteDocuments(Term(ID_FIELD, recordId.toString()))
        } else {
            // Atomically update
            indexWriter.updateDocument(Term(ID_FIELD, recordId.toString()), createDocument(recordId, vector))
        }

        if (updateCountSinceCommit.incrementAndGet().rem(5000L) == 0L) {
            indexWriter.commit()
        }
    }

    private fun createDocument(recordId: Long, vector: FloatArray): Document {
        val document = Document()
        document.add(StringField(ID_FIELD, recordId.toString(), Field.Store.YES))
        // KnnFloatVectorField: Indexed for KNN search, COSINE similarity is standard for embeddings
        document.add(KnnFloatVectorField(CONTENT_FIELD, vector, VectorSimilarityFunction.COSINE))
        return document
    }

    /**
     * Converts a numerical list/array to a FloatArray.
     * Returns null if conversion fails or input is null.
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
                    // Check if empty or contains non-numbers
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
            // Fail safe for mixed types or conversion errors
            null
        }
    }

    override fun shutdown() {
        runCatching {
            reopenThread.interrupt()
            reopenThread.join(2000)
        }
        runCatching { searcherManager.close() }
        runCatching { indexWriter.commit() }
        runCatching { indexWriter.close() }
        runCatching { directory.close() }

        val key = generateKey(entityDescriptor, indexDescriptor, context)

        luceneStates.remove(key)
        luceneDirectories.remove(key)
    }

    companion object {
        private const val ID_FIELD = "record_id"
        private const val CONTENT_FIELD = "vector_content"

        val luceneDirectories = ConcurrentHashMap<String, Directory>()
        val luceneStates = ConcurrentHashMap<String, LuceneVectorState>()
        private val sharedUpdateCounters = ConcurrentHashMap<String, AtomicLong>()

        private fun generateKey(entityDescriptor: EntityDescriptor, indexDescriptor: IndexDescriptor, context: SchemaContext): String {
            return buildString {
                append(context.location)
                append(File.separator)
                append(entityDescriptor.fileName)
                append("_${entityDescriptor.entityClass.simpleName}")
                append("_${entityDescriptor.partition?.partitionValue ?: ""}")
                append("_${indexDescriptor.name}")
                append(".vec_idx") // Changed extension to differentiate
            }
        }

        private fun createDirectory(
            key: String,
        ): Directory = luceneDirectories.computeIfAbsent(key) {
            val path = Path(key)
            Files.createDirectories(path)
            return@computeIfAbsent FSDirectory.open(path)
        }
    }
}