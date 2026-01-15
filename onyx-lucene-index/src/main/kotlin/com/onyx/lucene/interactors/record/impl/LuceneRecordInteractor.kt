package com.onyx.lucene.interactors.record.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.data.PutResult
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.interactors.record.impl.DefaultRecordInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
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
import java.lang.reflect.Field as ReflectField
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path

/**
 * Base Lucene "whole record" implementation that extends DefaultRecordInteractor.
 * Provides common Lucene indexing and search functionality that can be shared
 * by both sequence-based and UUID-based record interactors.
 *
 * Index key: record referenceId (DiskMap recID)
 * Lucene doc fields:
 *  - record_id (stored, exact match)
 *  - content   (tokenized, indexed)
 */
open class LuceneRecordInteractor(
    entityDescriptor: EntityDescriptor,
    context: SchemaContext
) : DefaultRecordInteractor(entityDescriptor, context) {

    private val contextRef = WeakReference(context)
    private val ctx: SchemaContext
        get() = contextRef.get() ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    private val analyzer: Analyzer = StandardAnalyzer()
    private lateinit var directory: Directory
    private lateinit var indexWriter: IndexWriter
    private lateinit var searcherManager: SearcherManager
    private lateinit var reopenThread: ControlledRealTimeReopenThread<IndexSearcher>
    private lateinit var queryParser: QueryParser
    private lateinit var updateCountSinceCommit: AtomicLong

    init {
        hydrateStates()
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(entity: IManagedEntity): PutResult {
        val result = super.save(entity)

        val pk = entity.identifier(ctx) ?: return result
        val referenceId = records.getRecID(pk)
        if (referenceId > 0L) {
            updateDocument(referenceId, entity)
        }

        return result
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(entity: IManagedEntity) {
        val pk = entity.identifier(ctx)
        val referenceId = if (pk != null) records.getRecID(pk) else -1L

        super.delete(entity)

        if (referenceId > 0L) {
            indexWriter.deleteDocuments(Term(ID_FIELD, referenceId.toString()))
            maybeCommitPeriodically()
        }
    }

    @Synchronized
    override fun deleteWithId(primaryKey: Any): IManagedEntity? {
        val referenceId = records.getRecID(primaryKey)
        val removed = super.deleteWithId(primaryKey)

        if (referenceId > 0L) {
            indexWriter.deleteDocuments(Term(ID_FIELD, referenceId.toString()))
            maybeCommitPeriodically()
        }

        return removed
    }

    @Synchronized
    override fun clear() {
        super.clear()
        indexWriter.deleteAll()
        runCatching { indexWriter.commit() }
        runCatching { searcherManager.maybeRefreshBlocking() }
    }

    /**
     * Full rebuild of Lucene record index from DiskMap contents.
     * Use when you first enable this interactor or detect drift.
     */
    @Synchronized
    open fun rebuildLucene() {
        indexWriter.deleteAll()

        val batch = ArrayList<Document>(BATCH_SIZE)
        for (entry in records.entries) {
            val referenceId = records.getRecID(entry.key)
            if (referenceId <= 0L) continue

            val text = entityToText(entry.value)
            if (text.isBlank()) continue

            batch.add(createDocument(referenceId, text))
            if (batch.size >= BATCH_SIZE) {
                indexWriter.addDocuments(batch)
                batch.clear()
            }
        }

        if (batch.isNotEmpty()) indexWriter.addDocuments(batch)

        indexWriter.commit()
        searcherManager.maybeRefreshBlocking()
    }

    /**
     * "Search anywhere" entry point for query engine routing.
     * Returns referenceId (recID) -> score.
     */
    fun searchAll(queryText: String, limit: Int): Map<Long, Float> {
        val q = queryText.trim()
        if (q.isEmpty()) return emptyMap()

        val parsed = parseQuery(q)

        searcherManager.maybeRefresh()

        val searcher = searcherManager.acquire()
        try {
            val topDocs = searcher.search(parsed, limit)
            val results = LinkedHashMap<Long, Float>(topDocs.scoreDocs.size)
            for (sd in topDocs.scoreDocs) {
                val doc = searcher.storedFields().document(sd.doc, setOf(ID_FIELD))
                val referenceId = doc.get(ID_FIELD)?.toLongOrNull() ?: continue
                results[referenceId] = sd.score
                if (results.size == limit) break
            }
            return results
        } finally {
            searcherManager.release(searcher)
        }
    }

    /**
     * Optional helper: hydrate entities (ordered by Lucene score).
     */
    open fun searchEntities(queryText: String, limit: Int = 100): List<IManagedEntity> {
        val hits = searchAll(queryText, limit)
        if (hits.isEmpty()) return emptyList()

        val out = ArrayList<IManagedEntity>(hits.size)
        for (referenceId in hits.keys) {
            val e = runCatching { getWithReferenceId(referenceId) }.getOrNull()
            if (e != null) out.add(e)
        }
        return out
    }

    /**
     * Shut down Lucene resources for this entity index.
     * Call from your schema shutdown path.
     */
    override fun shutdown() {
        synchronized(luceneDirectories) {
            val key = generateKey(entityDescriptor, ctx)

            runCatching {
                reopenThread.interrupt()
                reopenThread.join(2000)
            }
            runCatching { searcherManager.close() }
            runCatching { indexWriter.commit() }
            runCatching { indexWriter.close() }
            runCatching { directory.close() }

            luceneStates.remove(key)
            luceneDirectories.remove(key)
            sharedUpdateCounters.remove(key)
        }
    }

    /* ─────────────────────────── internals ─────────────────────────── */

    private data class LuceneRecordState(
        val indexWriter: IndexWriter,
        val searcherManager: SearcherManager,
        val reopenThread: ControlledRealTimeReopenThread<IndexSearcher>,
        val queryParser: QueryParser
    )

    private fun hydrateStates() {
        val key = generateKey(entityDescriptor, ctx)

        val state = luceneStates.computeIfAbsent(key) {
            directory = createDirectory(key)

            val writerConfig = IndexWriterConfig(analyzer).apply {
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
                name = "lucene-record-nrt-reopen-$key"
                start()
            }

            val parser = QueryParser(CONTENT_FIELD, analyzer).apply {
                defaultOperator = QueryParser.Operator.OR
                allowLeadingWildcard = true
            }

            LuceneRecordState(writer, manager, thread, parser)
        }

        indexWriter = state.indexWriter
        searcherManager = state.searcherManager
        reopenThread = state.reopenThread
        queryParser = state.queryParser
        updateCountSinceCommit = sharedUpdateCounters.getOrPut(key) { AtomicLong(0L) }
    }

    private fun updateDocument(referenceId: Long, entity: IManagedEntity) {
        val text = entityToText(entity)
        if (text.isBlank()) {
            indexWriter.deleteDocuments(Term(ID_FIELD, referenceId.toString()))
        } else {
            indexWriter.updateDocument(
                Term(ID_FIELD, referenceId.toString()),
                createDocument(referenceId, text)
            )
        }
        maybeCommitPeriodically()
    }

    private fun maybeCommitPeriodically() {
        if (updateCountSinceCommit.incrementAndGet().rem(COMMIT_EVERY) == 0L) {
            indexWriter.commit()
        }
    }

    private fun createDocument(referenceId: Long, text: String): Document {
        val doc = Document()
        doc.add(StringField(ID_FIELD, referenceId.toString(), Field.Store.YES))
        doc.add(TextField(CONTENT_FIELD, text, Field.Store.NO))
        return doc
    }

    private fun parseQuery(queryText: String) = try {
        queryParser.parse(queryText)
    } catch (_: ParseException) {
        queryParser.parse(QueryParser.escape(queryText))
    }

    /**
     * Convert entity to "whole record" text.
     * Uses cached reflect fields to avoid re-scanning on every save.
     */
    private fun entityToText(entity: IManagedEntity): String {
        val parts = ArrayList<String>(64)

        runCatching { parts.add(valueToText(entity.identifier(ctx))) }
        runCatching { parts.add(valueToText(entity.partitionId(ctx))) }

        val fields = fieldCache.computeIfAbsent(entity.javaClass) { cls ->
            buildList {
                var c: Class<*>? = cls
                while (c != null && c != Any::class.java) {
                    for (f in c.declaredFields) {
                        if (f.isSynthetic) continue
                        val m = f.modifiers
                        if (Modifier.isStatic(m)) continue
                        if (Modifier.isTransient(m)) continue
                        if (f.name.startsWith("\$")) continue
                        if (f.name.startsWith("this\$")) continue
                        runCatching { f.isAccessible = true }
                        add(f)
                    }
                    c = c.superclass
                }
            }
        }

        for (f in fields) {
            runCatching {
                val v = f.get(entity)
                val t = valueToText(v)
                if (t.isNotBlank()) parts.add(t)
            }
        }

        return parts.joinToString(" ").trim()
    }

    private fun valueToText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is CharSequence -> value.toString()
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Enum<*> -> value.name
        is Map<*, *> -> value.entries.joinToString(" ") { "${it.key}:${it.value}" }
        is Collection<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
        is Array<*> -> value.joinToString(" ") { it?.toString().orEmpty() }
        is IntArray -> value.joinToString(" ")
        is LongArray -> value.joinToString(" ")
        is FloatArray -> value.joinToString(" ") { String.format(Locale.US, "%.6f", it) }
        is DoubleArray -> value.joinToString(" ") { String.format(Locale.US, "%.6f", it) }
        else -> value.toString()
    }

    companion object {
        private const val ID_FIELD = "record_id"
        private const val CONTENT_FIELD = "content"

        private const val COMMIT_EVERY = 5_000L
        private const val BATCH_SIZE = 8_192

        private val fieldCache = ConcurrentHashMap<Class<*>, List<ReflectField>>()

        private val luceneDirectories = ConcurrentHashMap<String, Directory>()
        private val luceneStates = ConcurrentHashMap<String, LuceneRecordState>()
        private val sharedUpdateCounters = ConcurrentHashMap<String, AtomicLong>()

        private fun generateKey(entityDescriptor: EntityDescriptor, context: SchemaContext): String {
            return buildString {
                append(context.location)
                append(File.separator)
                append(entityDescriptor.fileName)
                append("_${entityDescriptor.entityClass.simpleName}")
                append("_${entityDescriptor.partition?.partitionValue ?: ""}")
                append(".rec.idx")
            }
        }

        private fun createDirectory(key: String): Directory = synchronized(luceneDirectories){
            return luceneDirectories.computeIfAbsent(key) {
                    val path = Path(key)
                    Files.createDirectories(path)
                    return@computeIfAbsent FSDirectory.open(path)
            }
        }
    }
}
