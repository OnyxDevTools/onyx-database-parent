package com.onyx.lucene.interactors.record.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.data.PutResult
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.extension.common.castTo
import com.onyx.interactors.record.FullTextRecordInteractor
import com.onyx.interactors.record.impl.DefaultRecordInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
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
) : DefaultRecordInteractor(entityDescriptor, context), FullTextRecordInteractor {

    private val contextRef = WeakReference(context)
    private val ctx: SchemaContext
        get() = contextRef.get() ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    private val analyzer: Analyzer = PerFieldAnalyzerWrapper(
        KeywordAnalyzer(),
        mapOf(CONTENT_FIELD to StandardAnalyzer())
    )
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
     * "Search anywhere" entry point for query engine routing.
     * Returns referenceId (recID) -> score.
     */
    override fun searchAll(queryText: String, limit: Int): Map<Long, Float> {
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
        indexWriter.updateDocument(
            Term(ID_FIELD, referenceId.toString()),
            createDocument(referenceId, text, entity)
        )
        maybeCommitPeriodically()
    }

    private fun maybeCommitPeriodically() {
        if (updateCountSinceCommit.incrementAndGet().rem(COMMIT_EVERY) == 0L) {
            indexWriter.commit()
        }
    }

    private fun createDocument(referenceId: Long, text: String, entity: IManagedEntity): Document {
        val doc = Document()
        doc.add(StringField(ID_FIELD, referenceId.toString(), Field.Store.YES))
        if (text.isNotBlank()) {
            doc.add(TextField(CONTENT_FIELD, text, Field.Store.NO))
        }
        addAttributeFields(doc, entity)
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

        runCatching { parts.add(entity.identifier(ctx).castTo(String::class.java) as String) }
        runCatching { parts.add(entity.partitionId(ctx).castTo(String::class.java) as String) }

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
                val t = v.castTo(String::class.java) as String
                if (t.isNotBlank()) parts.add(t)
            }
        }

        return parts.joinToString(" ").trim()
    }

    private fun addAttributeFields(doc: Document, entity: IManagedEntity) {
        val added = HashSet<String>()

        entityDescriptor.attributes.keys.forEach { attributeName ->
            val value = runCatching<Any?> {
                entity.get(context = ctx, descriptor = entityDescriptor, name = attributeName)
            }.getOrNull()
            if (added.add(attributeName)) {
                addAttributeFieldValue(doc, attributeName, value)
            }
        }

        val identifierName = entityDescriptor.identifier?.name
        if (identifierName != null && added.add(identifierName)) {
            addAttributeFieldValue(doc, identifierName, entity.identifier(ctx))
        }

        val partitionName = entityDescriptor.partition?.name
        if (partitionName != null && added.add(partitionName)) {
            val value = runCatching<Any?> {
                entity.get(context = ctx, descriptor = entityDescriptor, name = partitionName)
            }.getOrNull()
            addAttributeFieldValue(doc, partitionName, value)
        }
    }

    private fun addAttributeFieldValue(doc: Document, fieldName: String, value: Any?) {
        when (value) {
            null -> return
            is Map<*, *> -> value.entries.forEach { entry ->
                addAttributeFieldValue(doc, fieldName, "${entry.key}:${entry.value}")
            }
            is Collection<*> -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            is Array<*> -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            is IntArray -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            is LongArray -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            is FloatArray -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            is DoubleArray -> value.forEach { addAttributeFieldValue(doc, fieldName, it) }
            else -> addAttributeFieldText(doc, fieldName, value.toString())
        }
    }

    private fun addAttributeFieldText(doc: Document, fieldName: String, rawValue: String) {
        doc.add(StringField(fieldName, rawValue, Field.Store.YES))
        doc.add(StringField(fieldName + ATTRIBUTE_LOWER_SUFFIX, rawValue.lowercase(Locale.US), Field.Store.YES))
    }

    companion object {
        private const val ID_FIELD = "record_id"
        private const val CONTENT_FIELD = "content"
        private const val ATTRIBUTE_LOWER_SUFFIX = "__lc"

        private const val COMMIT_EVERY = 5_000L

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

        private fun createDirectory(key: String): Directory = synchronized(luceneDirectories) {
            return luceneDirectories.computeIfAbsent(key) {
                val path = Path(key)
                Files.createDirectories(path)
                return@computeIfAbsent FSDirectory.open(path)
            }
        }
    }
}
