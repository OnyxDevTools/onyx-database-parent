package com.onyx.lucene.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.exception.OnyxException
import com.onyx.interactors.index.impl.DefaultIndexInteractor
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
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.RAMDirectory
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Lucene-backed implementation of [IndexInteractor] that supports full-text search for indexed fields.
 *
 * The interactor mirrors the lifecycle of [DefaultIndexInteractor] to maintain the on-disk index metadata
 * while delegating fuzzy matching to a Lucene index stored alongside the entity data files.
 */
class LuceneIndexInteractor @Throws(OnyxException::class) constructor(
    private val entityDescriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : DefaultIndexInteractor(entityDescriptor, indexDescriptor, context) {

    private val contextRef = WeakReference(context)
    private val context: SchemaContext
        get() = contextRef.get() ?: throw IllegalStateException("SchemaContext has been garbage collected.")

    private val analyzer: Analyzer = StandardAnalyzer()
    private val directory: Directory
    private val indexWriter: IndexWriter
    private val searcherManager: SearcherManager
    private val queryParser: QueryParser
    private val lock = ReentrantReadWriteLock()

    init {
        directory = createDirectory()

        val config = IndexWriterConfig(analyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }
        indexWriter = IndexWriter(directory, config)
        searcherManager = SearcherManager(indexWriter, true, true, null)
        queryParser = QueryParser(CONTENT_FIELD, analyzer).apply {
            defaultOperator = QueryParser.Operator.AND
        }

        rebuildLuceneIndex()

        Runtime.getRuntime().addShutdownHook(Thread {
            lock.write {
                runCatching { searcherManager.close() }
                runCatching { indexWriter.close() }
                runCatching { directory.close() }
            }
        })
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        super.save(indexValue, oldReferenceId, newReferenceId)
        val valueForIndex = indexValue ?: extractIndexFieldValue(loadEntity(newReferenceId))
        updateDocument(newReferenceId, valueForIndex)
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        super.delete(reference)
        lock.write {
            indexWriter.deleteDocuments(Term(ID_FIELD, reference.toString()))
            indexWriter.commit()
            searcherManager.maybeRefreshBlocking()
        }
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun rebuild() {
        super.rebuild()
        rebuildLuceneIndex()
    }

    @Synchronized
    override fun clear() {
        super.clear()
        lock.write {
            indexWriter.deleteAll()
            indexWriter.commit()
            searcherManager.maybeRefreshBlocking()
        }
    }

    override fun matchAll(indexValue: Any?, limit: Int, maxCandidates: Int): Map<Long, Any?> {
        val queryText = indexValue?.toString()?.trim().orEmpty()
        if (queryText.isEmpty()) {
            return emptyMap()
        }

        val parsedQuery = parseQuery(queryText)
        val maxResults = maxCandidates.coerceAtLeast(limit).coerceAtLeast(1)

        return lock.read {
            searcherManager.maybeRefreshBlocking()
            val searcher = searcherManager.acquire()
            try {
                val topDocs = searcher.search(parsedQuery, maxResults)
                val results = LinkedHashMap<Long, Any?>(topDocs.scoreDocs.size)
                for (scoreDoc in topDocs.scoreDocs) {
                    val doc = searcher.doc(scoreDoc.doc)
                    val id = doc.get(ID_FIELD)?.toLongOrNull() ?: continue
                    results[id] = scoreDoc.score
                    if (results.size == limit) break
                }
                results
            } finally {
                searcherManager.release(searcher)
            }
        }
    }

    private fun rebuildLuceneIndex() {
        lock.write {
            indexWriter.deleteAll()
            val records = context.getDataFile(entityDescriptor)
                .getHashMap<DiskMap<Any, IManagedEntity>>(
                    entityDescriptor.identifier!!.type,
                    entityDescriptor.entityClass.name
                )
            for (entry in records.entries) {
                val recordId = records.getRecID(entry.key)
                if (recordId <= 0) continue
                val value = extractIndexFieldValue(entry.value)
                if (value != null) {
                    indexWriter.addDocument(createDocument(recordId, value))
                }
            }
            indexWriter.commit()
            searcherManager.maybeRefreshBlocking()
        }
    }

    private fun updateDocument(recordId: Long, value: Any?) {
        lock.write {
            if (value == null) {
                indexWriter.deleteDocuments(Term(ID_FIELD, recordId.toString()))
            } else {
                indexWriter.updateDocument(Term(ID_FIELD, recordId.toString()), createDocument(recordId, value))
            }
            indexWriter.commit()
            searcherManager.maybeRefreshBlocking()
        }
    }

    private fun createDocument(recordId: Long, value: Any?): Document {
        val document = Document()
        document.add(StringField(ID_FIELD, recordId.toString(), Field.Store.YES))
        val text = valueToText(value)
        if (text.isNotBlank()) {
            document.add(TextField(CONTENT_FIELD, text, Field.Store.NO))
        }
        return document
    }

    private fun parseQuery(queryText: String) = try {
        queryParser.parse(queryText)
    } catch (_: ParseException) {
        queryParser.parse(QueryParser.escape(queryText))
    }

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

    private fun createDirectory(): Directory {
        val safeFolderName = buildString {
            append(entityDescriptor.entityClass.name.replace('.', '_'))
            append('_')
            append(indexDescriptor.name)
            append("_lucene")
        }

        val basePath = try {
            Paths.get(context.location)
        } catch (_: InvalidPathException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }

        return if (basePath != null) {
            val directoryPath = basePath.resolve(safeFolderName)
            Files.createDirectories(directoryPath)
            FSDirectory.open(directoryPath)
        } else {
            RAMDirectory()
        }
    }

    companion object {
        private const val ID_FIELD = "record_id"
        private const val CONTENT_FIELD = "content"
    }
}
