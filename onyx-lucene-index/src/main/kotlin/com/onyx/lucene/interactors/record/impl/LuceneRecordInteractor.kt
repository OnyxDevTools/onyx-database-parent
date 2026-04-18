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
import org.apache.lucene.index.TieredMergePolicy
import org.apache.lucene.queryparser.classic.ParseException
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.ControlledRealTimeReopenThread
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field as ReflectField
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

/**
 * Base Lucene "whole record" implementation that extends DefaultRecordInteractor.
 *
 * IMPORTANT:
 * Lucene document identity is the ENTITY PRIMARY KEY, not the DiskMap recID.
 *
 * Why:
 * - recID is a physical/storage reference and can change across updates/rebuilds
 * - primary key is the logical/stable record identity
 *
 * We still return recID from searchAll() because the query engine expects it,
 * but we resolve the CURRENT recID from the primary key at search time.
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

    private val indexKey: String = generateKey(entityDescriptor, context)

    init {
        val rebuild = checkAndRebuildIndexIfNeeded()
        hydrateStates()
        if (rebuild) {
            rebuildIndex()
        } else {
            ensureIndexVersionMarker()
        }
    }

    /**
     * Rebuild if:
     * 1. index directory is missing/empty while records exist
     * 2. the on-disk index format marker is missing or old
     *
     * This forces a one-time rebuild when switching from recID-keyed Lucene docs
     * to primary-key-keyed Lucene docs.
     */
    private fun checkAndRebuildIndexIfNeeded(): Boolean {
        val indexPath = Path(indexKey)
        val indexExists =
            Files.exists(indexPath) &&
                    Files.isDirectory(indexPath) &&
                    (indexPath.toFile().listFiles()?.isNotEmpty() ?: false)

        if (records.size <= 0L) {
            return false
        }

        if (!indexExists) {
            return true
        }
    
        val versionFile = indexPath.resolve(INDEX_VERSION_FILE)
        val versionMatches = runCatching {
            Files.exists(versionFile) &&
                    Files.readString(versionFile).trim() == INDEX_FORMAT_VERSION
        }.getOrDefault(false)

        return !versionMatches
    }

    /**
     * Rebuild the Lucene index from the current record store.
     * Since the document identity changed to primary key, we clear the old index first.
     */
    private fun rebuildIndex() {
        indexWriter.deleteAll()

        records.forEach { _, entity ->
            val pk = entity.identifier(ctx)
            if (pk != null) {
                updateDocument(pk, entity)
            }
        }

        indexWriter.commit()
        ensureIndexVersionMarker()
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(entity: IManagedEntity): PutResult {
        val result = super.save(entity)

        val pk = entity.identifier(ctx) ?: return result
        updateDocument(pk, entity)

        return result
    }

    /**
     * Do not manually delete from Lucene here.
     * DefaultRecordInteractor.delete(entity) already calls this.deleteWithId(...)
     * and dynamic dispatch lands in our Lucene-aware deleteWithId override.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(entity: IManagedEntity) {
        super.delete(entity)
    }

    @Synchronized
    override fun deleteWithId(primaryKey: Any): IManagedEntity? {
        val pkText = primaryKeyToText(primaryKey)
        val removed = super.deleteWithId(primaryKey)

        indexWriter.deleteDocuments(Term(PRIMARY_KEY_FIELD, pkText))
        IndexCommitScheduler.markDirty(indexKey)

        return removed
    }

    @Synchronized
    override fun clear() {
        super.clear()
        indexWriter.deleteAll()
        IndexCommitScheduler.markDirty(indexKey)
    }

    /**
     * "Search anywhere" entry point for query engine routing.
     * Returns CURRENT referenceId (recID) -> score.
     *
     * Lucene stores the primary key. We resolve recID fresh from DiskMap here,
     * so search results cannot drift because of old/stale recIDs in the Lucene doc.
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
                val doc = searcher.storedFields().document(sd.doc, setOf(PRIMARY_KEY_FIELD))
                val primaryKeyText = doc.get(PRIMARY_KEY_FIELD) ?: continue
                val primaryKey = primaryKeyFromText(primaryKeyText) ?: continue
                val referenceId = records.getRecID(primaryKey)

                if (referenceId > 0L) {
                    results[referenceId] = sd.score
                    if (results.size == limit) break
                }
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
        shutdownInstance(indexKey)
    }

    /* ─────────────────────────── internals ─────────────────────────── */

    private data class LuceneRecordState(
        val indexWriter: IndexWriter,
        val searcherManager: SearcherManager,
        val reopenThread: ControlledRealTimeReopenThread<IndexSearcher>,
        val directory: Directory
    )

    private fun hydrateStates() {
        val key = indexKey

        val state = luceneStates.computeIfAbsent(key) {
            directory = createDirectory(key)

            val writerConfig = IndexWriterConfig(analyzer).apply {
                openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
                ramBufferSizeMB = 48.0
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

            LuceneRecordState(writer, manager, thread, directory)
        }

        indexWriter = state.indexWriter
        searcherManager = state.searcherManager
        reopenThread = state.reopenThread
    }

    private fun updateDocument(primaryKey: Any, entity: IManagedEntity) {
        val pkText = primaryKeyToText(primaryKey)
        val text = entityToText(entity)

        indexWriter.updateDocument(
            Term(PRIMARY_KEY_FIELD, pkText),
            createDocument(primaryKey, text, entity)
        )

        IndexCommitScheduler.markDirty(indexKey)
    }

    private fun createDocument(primaryKey: Any, text: String, entity: IManagedEntity): Document {
        val doc = Document()
        val pkText = primaryKeyToText(primaryKey)

        doc.add(StringField(PRIMARY_KEY_FIELD, pkText, Field.Store.YES))

        // Optional/debug/backward visibility only.
        // Do NOT use this field as the authoritative identity.
        val currentReferenceId = records.getRecID(primaryKey)
        if (currentReferenceId > 0L) {
            doc.add(StringField(REFERENCE_FIELD, currentReferenceId.toString(), Field.Store.YES))
        }

        if (text.isNotBlank()) {
            doc.add(TextField(CONTENT_FIELD, text, Field.Store.NO))
        }

        addAttributeFields(doc, entity)
        return doc
    }

    /**
     * Creates a new [QueryParser] for each invocation to ensure thread safety.
     * [QueryParser] is not thread-safe and must not be shared across threads.
     */
    private fun parseQuery(queryText: String): org.apache.lucene.search.Query {
        val parser = QueryParser(CONTENT_FIELD, analyzer).apply {
            defaultOperator = QueryParser.Operator.OR
            allowLeadingWildcard = true
        }
        return try {
            parser.parse(queryText)
        } catch (_: ParseException) {
            parser.parse(QueryParser.escape(queryText))
        }
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

    private fun primaryKeyToText(primaryKey: Any): String {
        return runCatching {
            primaryKey.castTo(String::class.java) as String
        }.getOrElse {
            primaryKey.toString()
        }
    }

    private fun primaryKeyFromText(primaryKeyText: String): Any? {
        val identifierType = entityDescriptor.identifier?.type ?: return primaryKeyText

        return runCatching {
            primaryKeyText.castTo(identifierType)
        }.getOrElse {
            when (identifierType) {
                String::class.java -> primaryKeyText
                java.lang.Integer::class.java, Int::class.java -> primaryKeyText.toInt()
                java.lang.Long::class.java, Long::class.java -> primaryKeyText.toLong()
                java.lang.Short::class.java, Short::class.java -> primaryKeyText.toShort()
                java.lang.Byte::class.java, Byte::class.java -> primaryKeyText.toByte()
                java.lang.Boolean::class.java, Boolean::class.java -> primaryKeyText.toBoolean()
                java.lang.Float::class.java, Float::class.java -> primaryKeyText.toFloat()
                java.lang.Double::class.java, Double::class.java -> primaryKeyText.toDouble()
                else -> primaryKeyText
            }
        }
    }

    private fun ensureIndexVersionMarker() {
        runCatching {
            val indexPath = Path(indexKey)
            Files.createDirectories(indexPath)
            Files.writeString(indexPath.resolve(INDEX_VERSION_FILE), INDEX_FORMAT_VERSION)
        }
    }

    companion object {
        private const val PRIMARY_KEY_FIELD = "entity_primary_key"
        private const val REFERENCE_FIELD = "record_id"
        private const val CONTENT_FIELD = "content"
        private const val ATTRIBUTE_LOWER_SUFFIX = "__lc"

        /**
         * Bump this whenever the Lucene document identity/schema changes.
         * Missing/mismatched version forces a rebuild.
         */
        private const val INDEX_VERSION_FILE = ".onyx-lucene-record-index.version"
        private const val INDEX_FORMAT_VERSION = "entity-primary-key-v1"

        private val fieldCache = ConcurrentHashMap<Class<*>, List<ReflectField>>()

        private val luceneDirectories = ConcurrentHashMap<String, Directory>()
        private val luceneStates = ConcurrentHashMap<String, LuceneRecordState>()

        init {
            IndexCommitScheduler.start()

            Runtime.getRuntime().addShutdownHook(Thread {
                IndexCommitScheduler.stop()
                luceneStates.keys.forEach { key -> shutdownInstance(key) }
            })
        }

        private fun shutdownInstance(key: String) {
            val state = luceneStates.remove(key) ?: return

            // Prevent the background commit worker from trying to commit while this key is shutting down.
            IndexCommitScheduler.clearDirtyKey(key)

            runCatching {
                state.reopenThread.close()
            }

            var closeError: Exception? = null

            runCatching {
                state.searcherManager.close()
            }.onFailure {
                closeError = closeError ?: (it as? Exception ?: Exception(it))
            }

            runCatching {
                if (state.indexWriter.isOpen) {
                    state.indexWriter.commit()
                }
            }.onFailure {
                closeError = closeError ?: (it as? Exception ?: Exception(it))
            }

            runCatching {
                if (state.indexWriter.isOpen) {
                    state.indexWriter.close()
                }
            }.onFailure {
                closeError = closeError ?: (it as? Exception ?: Exception(it))
            }

            runCatching {
                state.directory.close()
            }.onFailure {
                closeError = closeError ?: (it as? Exception ?: Exception(it))
            }

            luceneDirectories.remove(key)

            if (closeError != null) {
                System.err.println("CRITICAL: Error closing Lucene Record Index '$key': ${closeError!!.message}")
                closeError!!.printStackTrace()
            }
        }

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
            luceneDirectories.computeIfAbsent(key) {
                val path = Path(key)
                Files.createDirectories(path)
                FSDirectory.open(path)
            }
        }

        /**
         * Efficient, thread-safe background commit scheduler.
         */
        private object IndexCommitScheduler {
            private val dirtyIndexKeys = ConcurrentHashMap.newKeySet<String>()
            @Volatile private var running = true

            private val worker = Thread {
                while (running) {
                    try {
                        Thread.sleep(5000)
                        if (dirtyIndexKeys.isEmpty()) continue

                        val iterator = dirtyIndexKeys.iterator()
                        while (iterator.hasNext()) {
                            val key = iterator.next()
                            iterator.remove()

                            val state = luceneStates[key]
                            if (state != null && state.indexWriter.isOpen) {
                                try {
                                    state.indexWriter.commit()
                                } catch (e: Exception) {
                                    dirtyIndexKeys.add(key)
                                    System.err.println("Error committing record index $key: ${e.message}")
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.apply {
                isDaemon = true
                name = "OnyxLuceneRecordCommitScheduler"
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

            fun clearDirtyKey(key: String) {
                dirtyIndexKeys.remove(key)
            }
        }
    }
}
