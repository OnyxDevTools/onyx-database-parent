package com.onyx.lucene.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.exception.OnyxException
import com.onyx.extension.get
import com.onyx.interactors.index.impl.DefaultIndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.ParseException
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.ControlledRealTimeReopenThread
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.TieredMergePolicy
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.Locale
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
 * @property queryParser The parser used to convert user-facing query strings into Lucene Queries.
 */
data class LuceneState(
    val indexWriter: IndexWriter,
    val searcherManager: SearcherManager,
    val reopenThread: ControlledRealTimeReopenThread<IndexSearcher>,
    val indexWriterConfig: IndexWriterConfig,
    val queryParser: QueryParser
)

/**
 * A Lucene-backed implementation of [DefaultIndexInteractor] that provides full-text search
 * capabilities for indexed fields.
 *
 * This implementation is optimized for high write throughput by using Lucene's
 * Near-Real-Time (NRT) search features. It batches writes and uses a background
 * thread to refresh searchers, avoiding costly per-document commits.
 *
 * Index components ([IndexWriter], [SearcherManager], etc.) are shared per-index
 * via the [luceneStates] companion object map to ensure thread safety and resource efficiency.
 *
 * @param entityDescriptor Descriptor for the entity being indexed.
 * @param indexDescriptor Descriptor for the specific index field.
 * @param context The schema context, used to access data files and index locations.
 * @throws OnyxException if the index directory cannot be created.
 */
class LuceneIndexInteractor @Throws(OnyxException::class) constructor(
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

    private val analyzer: Analyzer = StandardAnalyzer()
    private lateinit var directory: Directory
    private lateinit var indexWriter: IndexWriter
    private lateinit var searcherManager: SearcherManager
    private lateinit var queryParser: QueryParser

    /**
     * Near-Real-Time (NRT) background thread.
     * This thread periodically calls [SearcherManager.maybeRefreshBlocking] to make
     * recent index changes visible to new searchers.
     */
    private lateinit var reopenThread: ControlledRealTimeReopenThread<IndexSearcher>

    /**
     * A counter to track updates since the last explicit commit.
     * This counter is *shared* across all interactor instances pointing to the
     * same physical index to ensure commits happen periodically.
     */
    private lateinit var updateCountSinceCommit: AtomicLong

    init {
        // Hydrate the stateful Lucene components from the shared companion map
        hydrateStates()
    }

    /**
     * Initializes or retrieves the shared [LuceneState] for this specific index.
     * This method ensures that only one set of [IndexWriter], [SearcherManager], etc.,
     * exists per physical index, allowing multiple interactors to safely share resources.
     */
    private fun hydrateStates() {
        val key = generateKey(entityDescriptor, indexDescriptor, context)
        val luceneState = luceneStates.computeIfAbsent(key) {
            directory = createDirectory(key)
            // Configuration optimized for write throughput
            val writerConfig = IndexWriterConfig(analyzer).apply {
                openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
                ramBufferSizeMB = 256.0                          // Large in-memory buffer for documents
                useCompoundFile = false                          // Write multiple files (faster merges)
                mergePolicy = (mergePolicy as TieredMergePolicy).apply {
                    segmentsPerTier = 10.0
                    maxMergeAtOnce = 10
                    floorSegmentMB = 16.0
                }
            }

            val writer = IndexWriter(directory, writerConfig)
            val manager = SearcherManager(writer, null)

            // NRT: Keep searchers reasonably fresh (max 5s stale)
            // without blocking every write operation.
            val thread = ControlledRealTimeReopenThread(
                writer,
                manager,
                /* targetMaxStaleSec = */ 5.0,   // At most ~5s stale
                /* targetMinStaleSec = */ 0.1    // But try to reopen quickly if write pressure is high
            ).apply {
                isDaemon = true
                name = "lucene-nrt-reopen-$key"
                start()
            }

            val parser = QueryParser(CONTENT_FIELD, analyzer).apply {
                defaultOperator = QueryParser.Operator.OR
                allowLeadingWildcard = true
            }

            LuceneState(writer, manager, thread, writerConfig, parser)
        }

        // Assign the shared components to this instance
        this.indexWriter = luceneState.indexWriter
        this.searcherManager = luceneState.searcherManager
        this.reopenThread = luceneState.reopenThread
        this.queryParser = luceneState.queryParser
        // Retrieve the shared counter for periodic commits
        this.updateCountSinceCommit = sharedUpdateCounters.getOrPut(key) { AtomicLong(0L) }
    }

    /**
     * Saves or updates an entity in the Lucene index.
     * This method maps the entity's record ID to its indexed value.
     *
     * @param indexValue The value of the field being indexed (can be null).
     * @param oldReferenceId The previous record ID (not used by Lucene, handled by superclass).
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
     *
     * @param reference The record ID of the entity to delete.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        super.delete(reference)
        indexWriter.deleteDocuments(Term(ID_FIELD, reference.toString()))
    }

    /**
     * Triggers a full rebuild of the Lucene index, clearing all existing
     * documents and re-indexing every entity from the primary data file.
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
     * Performs a full-text search against the Lucene index.
     *
     * @param indexValue The query string to search for.
     * @param limit The maximum number of results to return.
     * @param maxCandidates (Not used in this implementation)
     * @return A map of [Long, Any?] where the key is the matching entity's record ID
     * and the value is the search score (as a [Float]).
     */
    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> {
        val queryText = indexValue?.toString()?.trim().orEmpty()
        if (queryText.isEmpty()) return emptyMap()

        val parsedQuery = parseQuery(queryText)

        // Ensure the searcher is up-to-date with recent writes
        searcherManager.maybeRefresh()

        val searcher = searcherManager.acquire()
        try {
            val topDocs = searcher.search(parsedQuery, limit)
            val results = LinkedHashMap<Long, Any?>(topDocs.scoreDocs.size)
            for (scoreDoc in topDocs.scoreDocs) {
                val documentId = scoreDoc.doc
                // Retrieve only the ID_FIELD, which is all we need
                val document = searcher.storedFields().document(documentId, setOf(ID_FIELD))
                val recordId = document.get(ID_FIELD)?.toLongOrNull() ?: continue
                results[recordId] = scoreDoc.score
                if (results.size == limit) break
            }
            return results
        } finally {
            searcherManager.release(searcher)
        }
    }

    /**
     * Helper method to perform a full index rebuild.
     * It iterates over all entities in the main data file, converts them to
     * Lucene documents, and adds them to the index in batches.
     */
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
            val text = valueToText(value)
            if (text.isNotBlank()) documentBatch.add(createDocument(recordId, text))

            if (documentBatch.size >= 8192) {
                indexWriter.addDocuments(documentBatch)
                documentBatch.clear()
            }
        }
        if (documentBatch.isNotEmpty()) indexWriter.addDocuments(documentBatch)

        // Single commit at end of rebuild
        indexWriter.commit()
        // Make results visible
        searcherManager.maybeRefreshBlocking()
    }

    /**
     * Updates or deletes a single document in the index based on its record ID and new value.
     * If the value is null or converts to blank text, the document is deleted.
     * Otherwise, it is updated.
     *
     * This method also triggers a periodic [IndexWriter.commit] every 5000 updates
     * to manage memory and persist changes to disk.
     *
     * @param recordId The entity's unique record ID.
     * @param value The new value for the indexed field.
     */
    private fun updateDocument(recordId: Long, value: Any?) {
        if (value == null) {
            indexWriter.deleteDocuments(Term(ID_FIELD, recordId.toString()))
        } else {
            val text = valueToText(value)
            if (text.isBlank()) {
                // If the new value is blank, treat it as a deletion
                indexWriter.deleteDocuments(Term(ID_FIELD, recordId.toString()))
            } else {
                // Atomically update the document or add it if it doesn't exist
                indexWriter.updateDocument(Term(ID_FIELD, recordId.toString()), createDocument(recordId, text))
            }
        }
        // Periodically commit to disk. NRT refresh is handled by the background thread,
        // but committing flushes segments to disk and controls RAM usage.
        if (updateCountSinceCommit.incrementAndGet().rem(5000L) == 0L) {
            indexWriter.commit()
        }
    }

    /**
     * Creates a Lucene [Document] from an entity's record ID and indexed text content.
     *
     * @param recordId The entity's record ID. This is stored and indexed to link search results back to the entity.
     * @param text The text content to be indexed for full-text search. This is not stored.
     * @return A new [Document] instance.
     */
    private fun createDocument(recordId: Long, text: String): Document {
        val document = Document()
        // ID_FIELD: Stored and indexed for exact-match lookups and retrieval
        document.add(StringField(ID_FIELD, recordId.toString(), Field.Store.YES))
        // CONTENT_FIELD: Tokenized and indexed for search, but not stored to save space
        document.add(TextField(CONTENT_FIELD, text, Field.Store.NO))
        return document
    }

    /**
     * Parses a query string, with a fallback mechanism.
     * If the initial parse fails (e.g., due to syntax errors),
     * it escapes the query text and tries again.
     *
     * @param queryText The raw user-provided query string.
     * @return A Lucene [Query] object.
     */
    private fun parseQuery(queryText: String) = try {
        queryParser.parse(queryText)
    } catch (_: ParseException) {
        // Fallback: escape special characters and parse again
        queryParser.parse(QueryParser.escape(queryText))
    }

    /**
     * Converts an arbitrary value into a space-separated string suitable for indexing.
     * Handles collections, arrays, and various primitive types.
     *
     * @param value The value to convert.
     * @return A string representation.
     */
    private fun valueToText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is CharSequence -> value.toString()
        is Collection<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
        is Array<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
        is IntArray -> value.joinToString(" ")
        is LongArray -> value.joinToString(" ")
        is FloatArray -> value.joinToString(" ") { String.format(Locale.US, "%.6f", it) }
        is DoubleArray -> value.joinToString(" ") { String.format(Locale.US, "%.6f", it) }
        else -> value.toString()
    }

    /**
     * Shuts down the interactor and releases all associated Lucene resources.
     * This includes stopping the NRT thread, closing the searcher,
     * committing final changes, and closing the index writer and directory.
     */
    override fun shutdown() {
        // Gracefully shut down the NRT thread
        runCatching {
            reopenThread.interrupt()
            reopenThread.join(2000)
        }
        // Close all other components, swallowing exceptions
        runCatching { searcherManager.close() }
        runCatching { indexWriter.commit() } // Final fsync
        runCatching { indexWriter.close() }
        runCatching { directory.close() }

        val key = generateKey(entityDescriptor, indexDescriptor, context)

        luceneStates.remove(key)
        luceneDirectories.remove(key)
    }

    companion object {
        /** The document field name for the entity's record ID. */
        private const val ID_FIELD = "record_id"

        /** The document field name for the indexed text content. */
        private const val CONTENT_FIELD = "content"

        /**
         * Caches [Directory] instances by their folder name.
         * This prevents multiple `FSDirectory.open()` calls on the same path,
         * which can cause locking issues.
         */
        val luceneDirectories = ConcurrentHashMap<String, Directory>()

        /**
         * A map to store and share [LuceneState] instances.
         * The key is a unique string generated by [generateKey], ensuring that
         * all interactors for the same index share the same [IndexWriter] and [SearcherManager].
         */
        val luceneStates = ConcurrentHashMap<String, LuceneState>()

        /**
         * A map to store and share [AtomicLong] counters for periodic commits.
         * This ensures that the commit logic works correctly even with multiple
         * interactor instances pointing to the same shared [IndexWriter].
         */
        private val sharedUpdateCounters = ConcurrentHashMap<String, AtomicLong>()

        /**
         * Generates a unique, file-system-safe key for a given entity and index.
         *
         * @return A string key, e.g., "MyDataFile_MyEntity__MyIndexName"
         */
        private fun generateKey(entityDescriptor: EntityDescriptor, indexDescriptor: IndexDescriptor, context: SchemaContext): String {
            return buildString {
                append(context.location)
                append(File.separator)
                append(entityDescriptor.fileName)
                append("_${entityDescriptor.entityClass.simpleName}")
                append("_${entityDescriptor.partition?.partitionValue ?: ""}")
                append("_${indexDescriptor.name}")
                append(".idx")
            }
        }

        /**
         * Creates or opens the [FSDirectory] for the Lucene index.
         * The directory is placed in a subfolder named after the generated key.
         * This method is synchronized and caches the [Directory] instance in [luceneDirectories]
         * to prevent concurrent access issues.
         *
         * @return The cached [Directory] instance.
         */
        private fun createDirectory(
            key: String,
        ): Directory = luceneDirectories.computeIfAbsent(key) {
            val path = Path(key)
            Files.createDirectories(path)
            return@computeIfAbsent FSDirectory.open(path)
        }
    }
}
