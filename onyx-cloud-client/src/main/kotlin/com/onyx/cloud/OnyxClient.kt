@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyx.cloud

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.onyx.cloud.exceptions.NotFoundException
import com.onyx.cloud.extensions.fromJson
import com.onyx.cloud.extensions.fromJsonList
import com.onyx.cloud.extensions.toJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.KClass

/**
 * Onyx API client implemented with JDK networking only.
 *
 * @param baseUrl Base URL of the Onyx API without trailing slashes.
 * @param databaseId Target database identifier.
 * @param apiKey API key header value.
 * @param apiSecret API secret header value.
 */
class OnyxClient(
    baseUrl: String = "https://api.onyx.dev",
    private val databaseId: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val baseUrl: String = baseUrl.replace(Regex("/+$"), "")

    private class HttpMethod private constructor(val value: String) {
        companion object {
            val Get = HttpMethod("GET")
            val Put = HttpMethod("PUT")
            val Post = HttpMethod("POST")
            val Delete = HttpMethod("DELETE")
        }
    }

    private object Timeouts {
        const val REQUEST_TIMEOUT = 12_000_000
        const val CONNECT_TIMEOUT = 30_000
        const val STREAM_READ_TIMEOUT = 0
    }

    /**
     * Saves or updates a [Document].
     *
     * @param document Document to persist.
     * @return Saved document returned by the server.
     */
    fun saveDocument(document: Document): Document {
        val path = "/data/${encode(databaseId)}/document"
        return makeRequest(HttpMethod.Put, path, document).fromJson<Document>()
            ?: throw IllegalStateException("Failed to parse response for saveDocument")
    }

    /**
     * Retrieves a document by ID.
     *
     * @param documentId Document identifier.
     * @param options Optional query parameters such as image sizing.
     * @return Raw document payload as a string.
     */
    fun getDocument(documentId: String, options: Map<String, Any?> = emptyMap()): String {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        return makeRequest(HttpMethod.Get, path, queryString = queryString)
    }

    /**
     * Deletes a document by ID.
     *
     * @param documentId Document identifier.
     * @return True if deletion succeeded.
     */
    fun deleteDocument(documentId: String): Boolean {
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        return makeRequest(HttpMethod.Delete, path).equals("true", ignoreCase = true)
    }

    /**
     * Saves an entity or a list of entities.
     *
     * @param table Target entity type.
     * @param entityOrEntities Entity or list of entities.
     * @return Saved entity or original list.
     */
    fun <T : Any> save(table: KClass<*>, entityOrEntities: T): T {
        val path = "/data/${encode(databaseId)}/${encode(table)}"
        return if (entityOrEntities is List<*>) {
            makeRequest(HttpMethod.Put, path, entityOrEntities)
            entityOrEntities
        } else {
            makeRequest(HttpMethod.Put, path, entityOrEntities).fromJson(table)
                ?: throw IllegalStateException("Failed to parse response for save single entity")
        }
    }

    /**
     * Saves a single entity inferring the table.
     *
     * @param T Entity type.
     * @param entity Entity to save.
     * @return Saved entity.
     */
    inline fun <reified T : Any> save(entity: T): T = this.save(T::class, entity)

    /**
     * Saves entities in batches.
     *
     * @param T Entity type.
     * @param entities Entities to save.
     * @param batchSize Number per request.
     */
    inline fun <reified T : Any> batchSave(entities: List<T>, batchSize: Int = 1000) {
        entities.chunked(batchSize).forEach { chunk ->
            if (chunk.isNotEmpty()) this.save(T::class, chunk)
        }
    }

    /**
     * Finds an entity by primary key.
     *
     * @param T Result type.
     * @param type Entity class.
     * @param primaryKey Key value.
     * @param options Optional query options (partition, fetch).
     * @return Entity or null when not found.
     */
    fun <T : Any> findById(
        type: KClass<*>,
        primaryKey: Any,
        options: Map<String, Any?> = emptyMap()
    ): T? {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/${encode(type.java.simpleName)}/${encode(primaryKey.toString())}"
        return try {
            makeRequest(HttpMethod.Get, path, queryString = queryString).fromJson(type)
        } catch (_: NotFoundException) {
            null
        }
    }

    /**
     * Finds an entity by primary key inferring the table.
     *
     * @param T Result type.
     * @param id Key value.
     */
    inline fun <reified T : Any> findById(id: Any): T? = findById(T::class, id)

    /**
     * Finds an entity within a partition.
     *
     * @param T Result type.
     * @param table Entity class.
     * @param primaryKey Key value.
     * @param partition Partition value.
     */
    fun <T : Any> findByIdInPartition(table: KClass<*>, primaryKey: String, partition: String): T? {
        return findById(table, primaryKey, mapOf("partition" to partition))
    }

    /**
     * Finds an entity within a partition inferring the table.
     *
     * @param T Result type.
     * @param primaryKey Key value.
     * @param partition Partition value.
     */
    inline fun <reified T : Any> findByIdInPartition(primaryKey: String, partition: String): T? {
        return findByIdInPartition(T::class, primaryKey, partition)
    }

    /**
     * Finds an entity and fetches relationships.
     *
     * @param T Result type.
     * @param table Entity class.
     * @param primaryKey Key value.
     * @param fetchRelationships Relationships to fetch.
     */
    fun <T : Any> findByIdWithFetch(table: KClass<*>, primaryKey: String, fetchRelationships: List<String>): T? {
        return findById(table, primaryKey, mapOf("fetch" to fetchRelationships))
    }

    /**
     * Finds an entity and fetches relationships inferring the table.
     *
     * @param T Result type.
     * @param primaryKey Key value.
     * @param fetchRelationships Relationships to fetch.
     */
    inline fun <reified T : Any> findByIdWithFetch(primaryKey: String, fetchRelationships: List<String>): T? {
        return findByIdWithFetch(T::class, primaryKey, fetchRelationships)
    }

    /**
     * Finds an entity by id in a partition and fetches relationships.
     *
     * @param T Result type.
     * @param table Entity class.
     * @param primaryKey Key value.
     * @param partition Partition value.
     * @param fetchRelationships Relationships to fetch.
     */
    fun <T : Any> findByIdInPartitionWithFetch(
        table: KClass<*>,
        primaryKey: String,
        partition: String,
        fetchRelationships: List<String>
    ): T? {
        return findById(table, primaryKey, mapOf("partition" to partition, "fetch" to fetchRelationships))
    }

    /**
     * Deletes an entity by key.
     *
     * @param table Table name.
     * @param primaryKey Key value.
     * @param options Optional query options (partition).
     */
    fun delete(
        table: String,
        primaryKey: String,
        options: Map<String, Any?> = emptyMap()
    ): Boolean {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/${encode(table)}/${encode(primaryKey)}"
        return makeRequest(HttpMethod.Delete, path, queryString = queryString).equals("true", ignoreCase = true)
    }

    /**
     * Deletes an entity in a partition.
     *
     * @param table Table name.
     * @param primaryKey Key value.
     * @param partition Partition value.
     */
    fun deleteInPartition(table: String, primaryKey: String, partition: String): Boolean {
        return delete(table, primaryKey, mapOf("partition" to partition))
    }

    /**
     * Executes a select query.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param options Optional querystring options (pageSize, nextPage, partition).
     * @return Raw JSON response.
     */
    fun executeQuery(
        table: String,
        selectQuery: Any,
        options: Map<String, Any?> = emptyMap()
    ): String {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/query/${encode(table)}"
        return makeRequest(HttpMethod.Put, path, selectQuery, queryString = queryString)
    }

    /**
     * Executes a count query.
     *
     * @param table Table name.
     * @param selectQuery Query body with conditions.
     * @param options Optional options (partition).
     * @return Count as integer.
     */
    fun executeCountForQuery(
        table: String,
        selectQuery: Any,
        options: Map<String, Any?> = emptyMap()
    ): Int {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/query/count/${encode(table)}"
        return makeRequest(HttpMethod.Put, path, selectQuery, queryString = queryString).toIntOrNull() ?: 0
    }

    /**
     * Executes a select query with paging.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param pageSize Page size.
     * @param nextPage Next page token.
     */
    fun executeQueryWithPaging(
        table: String,
        selectQuery: Any,
        pageSize: Int,
        nextPage: String
    ): String {
        return executeQuery(table, selectQuery, mapOf("pageSize" to pageSize, "nextPage" to nextPage))
    }

    /**
     * Executes a select query scoped to a partition.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param partition Partition value.
     */
    fun executeQueryInPartition(
        table: String,
        selectQuery: Any,
        partition: String
    ): String {
        return executeQuery(table, selectQuery, mapOf("partition" to partition))
    }

    /**
     * Executes a select query in a partition with paging.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param partition Partition value.
     * @param pageSize Page size.
     * @param nextPage Next page token.
     */
    fun executeQueryInPartitionWithPaging(
        table: String,
        selectQuery: Any,
        partition: String,
        pageSize: Int,
        nextPage: String
    ): String {
        return executeQuery(
            table,
            selectQuery,
            mapOf("partition" to partition, "pageSize" to pageSize, "nextPage" to nextPage)
        )
    }

    /**
     * Executes an update query.
     *
     * @param table Table name.
     * @param updateQuery Update body.
     * @param options Optional options (partition).
     * @return Number updated.
     */
    fun executeUpdateQuery(
        table: String,
        updateQuery: Any,
        options: Map<String, Any?> = emptyMap()
    ): Int {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/query/update/${encode(table)}"
        return makeRequest(HttpMethod.Put, path, updateQuery, queryString = queryString).toIntOrNull() ?: 0
    }

    /**
     * Executes an update in a partition.
     *
     * @param table Table name.
     * @param updateQuery Update body.
     * @param partition Partition value.
     */
    fun executeUpdateQueryInPartition(
        table: String,
        updateQuery: Any,
        partition: String
    ): Int {
        return executeUpdateQuery(table, updateQuery, mapOf("partition" to partition))
    }

    /**
     * Executes a delete-by-query.
     *
     * @param table Table name.
     * @param selectQuery Conditions body.
     * @param options Optional options (partition).
     * @return Number deleted.
     */
    fun executeDeleteQuery(
        table: String,
        selectQuery: Any,
        options: Map<String, Any?> = emptyMap()
    ): Int {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/query/delete/${encode(table)}"
        return makeRequest(HttpMethod.Put, path, selectQuery, queryString = queryString).toIntOrNull() ?: 0
    }

    /**
     * Executes a delete in a partition.
     *
     * @param table Table name.
     * @param selectQuery Conditions body.
     * @param partition Partition value.
     */
    fun executeDeleteQueryInPartition(
        table: String,
        selectQuery: Any,
        partition: String
    ): Int {
        return executeDeleteQuery(table, selectQuery, mapOf("partition" to partition))
    }

    /**
     * Opens a JSONL stream as a cold [Flow].
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param includeQueryResults Emit initial results first.
     * @param keepAlive Keep connection open for changes.
     */
    fun stream(
        table: String,
        selectQuery: Any,
        includeQueryResults: Boolean = true,
        keepAlive: Boolean = false,
    ): Flow<String> = flow {
        val params = "?includeQueryResults=$includeQueryResults&keepAlive=$keepAlive"
        val encodedDbId = encode(databaseId)
        val encodedTable = encode(table)
        val path = "/data/$encodedDbId/query/stream/$encodedTable$params"
        val urlStr = "$baseUrl$path"

        withContext(Dispatchers.IO) {
            val url = URI(urlStr).toURL()
            val conn = (url.openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = "PUT"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = Timeouts.CONNECT_TIMEOUT
                conn.readTimeout = Timeouts.STREAM_READ_TIMEOUT
                conn.doInput = true
                conn.doOutput = true
                conn.useCaches = false
                conn.setChunkedStreamingMode(8 * 1024)
                applyHeaders(conn, defaultHeaders())

                val payload = selectQuery.toJson()
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(payload)
                    it.flush()
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val errorBody = conn.bodyAsString()
                    throw RuntimeException("HTTP Error: $code ${conn.responseMessage}. Body: $errorBody")
                }

                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String?
                    while (true) {
                        line = reader.readLine() ?: break
                        emit(line)
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Opens a stream that only emits change events.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param keepAlive Keep connection open for changes.
     */
    fun streamEventsOnly(table: String, selectQuery: Any, keepAlive: Boolean = true): Flow<String> {
        return stream(table, selectQuery, includeQueryResults = false, keepAlive = keepAlive)
    }

    /**
     * Opens a stream that emits initial results then changes.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param keepAlive Keep connection open for changes.
     */
    fun streamWithQueryResults(table: String, selectQuery: Any, keepAlive: Boolean = false): Flow<String> {
        return stream(table, selectQuery, includeQueryResults = true, keepAlive = keepAlive)
    }

    /**
     * Creates a [QueryBuilder] for a table name.
     *
     * @param tableName Table name.
     */
    fun from(tableName: String): QueryBuilder = QueryBuilder(this, tableName)

    /**
     * Creates a [QueryBuilder] inferring the table from [T].
     *
     * @param T Entity type.
     */
    inline fun <reified T> from(): QueryBuilder = this.from(T::class.java.simpleName)

    /**
     * Creates a [QueryBuilder] with selected fields.
     *
     * @param fields Field names or aggregate expressions.
     */
    fun select(vararg fields: String): QueryBuilder {
        val qb = QueryBuilder(this, null)
        qb.select(*fields)
        return qb
    }

    /**
     * URL-encodes a string.
     *
     * @param value Raw value.
     */
    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    /**
     * URL-encodes a KClass simple name.
     *
     * @param value Class to encode.
     */
    fun encode(value: KClass<*>): String = encode(value.java.simpleName)

    private fun defaultHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "x-onyx-key" to apiKey,
            "x-onyx-secret" to apiSecret,
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive"
        ) + extra

    private fun applyHeaders(conn: HttpURLConnection, headers: Map<String, String>) {
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
    }

    private fun HttpURLConnection.bodyAsString(): String {
        fun readAll(input: InputStream): String =
            input.bufferedReader(StandardCharsets.UTF_8).use(BufferedReader::readText)
        return try {
            readAll(inputStream)
        } catch (_: Exception) {
            val err = errorStream
            if (err != null) readAll(err) else ""
        }
    }

    private fun buildQueryString(options: Map<String, Any?>): String {
        val params = buildList {
            options.forEach { (key, value) ->
                when {
                    value == null -> {}
                    key == "fetch" && value is List<*> -> {
                        val fetchList = value.filterNotNull().joinToString(",")
                        if (fetchList.isNotEmpty()) add("$key=${encode(fetchList)}")
                    }
                    else -> add("$key=${encode(value.toString())}")
                }
            }
        }.joinToString("&")
        return if (params.isNotEmpty()) "?$params" else ""
    }

    private fun makeRequest(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        queryString: String = ""
    ): String {
        val urlStr = "$baseUrl$path$queryString"
        val url = URI(urlStr).toURL()
        val conn = (url.openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method.value
            conn.instanceFollowRedirects = true
            conn.connectTimeout = Timeouts.CONNECT_TIMEOUT
            conn.readTimeout = Timeouts.REQUEST_TIMEOUT
            conn.doInput = true
            val willSendBody = (method.value == "POST" || method.value == "PUT") && body != null
            conn.doOutput = willSendBody
            conn.useCaches = false
            applyHeaders(conn, defaultHeaders(extraHeaders))

            if (willSendBody) {
                conn.setChunkedStreamingMode(8 * 1024)
                val payload = body as? String ?: body.toJson()
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(payload)
                    it.flush()
                }
            }

            val code = conn.responseCode
            val text = conn.bodyAsString()
            if (code !in 200..299) {
                val msg = "HTTP $code @ $urlStr → $text"
                throw when (code) {
                    404 -> NotFoundException(msg, RuntimeException("HTTP $code"))
                    else -> RuntimeException(msg)
                }
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Fluent builder for select, update and delete queries including streaming.
 *
 * @property client Backing [OnyxClient].
 * @property table Optional table name; must be set before execution.
 */
class QueryBuilder internal constructor(
    private val client: OnyxClient,
    private var table: String?
) {
    private var fields: List<String>? = null
    private var conditions: QueryCondition? = null
    private var sort: List<SortOrder>? = null
    private var limitValue: Int? = null
    private var distinctValue: Boolean = false
    private var groupByValues: List<String>? = null
    private var partitionValue: String? = null
    private var resolvers: List<String> = emptyList()
    private var updates: Map<String, Any?>? = null

    private enum class Mode { SELECT, UPDATE, DELETE }
    private var mode: Mode = Mode.SELECT

    private var pageSizeValue: Int? = null
    private var nextPageValue: String? = null

    private var onItemAddedListener: ((entity: Any) -> Unit)? = null
    private var onItemDeletedListener: ((entity: Any) -> Unit)? = null
    private var onItemUpdatedListener: ((entity: Any) -> Unit)? = null
    private var onItemListener: ((entity: Any) -> Unit)? = null

    /**
     * Sets the table.
     *
     * @param table Table name.
     */
    fun from(table: String): QueryBuilder {
        this.table = table
        return this
    }

    /**
     * Sets the table inferred from [T].
     *
     * @param T Entity type.
     */
    inline fun <reified T> from(): QueryBuilder = this.from(T::class.java.simpleName)

    /**
     * Selects fields.
     *
     * @param fields Field names or expressions.
     */
    fun select(vararg fields: String): QueryBuilder {
        this.fields = fields.toList().ifEmpty { null }
        this.mode = Mode.SELECT
        return this
    }

    /**
     * Selects fields with a list.
     *
     * @param fields Field names or expressions.
     */
    fun selectFields(fields: List<String>): QueryBuilder {
        this.fields = fields.ifEmpty { null }
        this.mode = Mode.SELECT
        return this
    }

    /**
     * Replaces existing conditions.
     *
     * @param condition Root condition.
     */
    fun where(condition: ConditionBuilder): QueryBuilder {
        this.conditions = condition.toCondition()
        return this
    }

    /**
     * Adds AND condition.
     *
     * @param condition Condition to add.
     */
    fun and(condition: ConditionBuilder): QueryBuilder {
        addCondition(condition, LogicalOperator.AND)
        return this
    }

    /**
     * Adds OR condition.
     *
     * @param condition Condition to add.
     */
    fun or(condition: ConditionBuilder): QueryBuilder {
        addCondition(condition, LogicalOperator.OR)
        return this
    }

    private fun addCondition(builderToAdd: ConditionBuilder, logicalOperator: LogicalOperator) {
        val conditionToAdd = builderToAdd.toCondition() ?: return
        val currentCondition = this.conditions
        this.conditions = when {
            currentCondition == null -> conditionToAdd
            currentCondition is QueryCondition.CompoundCondition && currentCondition.operator == logicalOperator ->
                currentCondition.copy(conditions = currentCondition.conditions + conditionToAdd)
            else -> QueryCondition.CompoundCondition(
                operator = logicalOperator,
                conditions = listOf(currentCondition, conditionToAdd)
            )
        }
    }

    /**
     * Sets limit.
     *
     * @param limit Maximum rows.
     */
    fun limit(limit: Int): QueryBuilder {
        limitValue = limit
        return this
    }

    /**
     * Enforces distinct results.
     */
    fun distinct(): QueryBuilder {
        distinctValue = true
        return this
    }

    /**
     * Sets group-by.
     *
     * @param fields Fields to group by.
     */
    fun groupBy(vararg fields: String): QueryBuilder {
        groupByValues = fields.toList().ifEmpty { null }
        return this
    }

    /**
     * Sets order-by.
     *
     * @param orders Sort orders.
     */
    fun orderBy(vararg orders: SortOrder): QueryBuilder {
        sort = orders.toList().ifEmpty { null }
        return this
    }

    /**
     * Scopes the query to a partition.
     *
     * @param partition Partition value.
     */
    fun inPartition(partition: String): QueryBuilder {
        partitionValue = partition
        return this
    }

    /**
     * Requests relationship resolution.
     *
     * @param resolvers Relationships to resolve.
     */
    fun resolve(vararg resolvers: String): QueryBuilder {
        this.resolvers = resolvers.toList()
        return this
    }

    /**
     * Switches to update mode with field values.
     *
     * @param updates Field/value pairs.
     */
    fun setUpdates(vararg updates: Pair<String, Any?>): QueryBuilder {
        this.mode = Mode.UPDATE
        this.updates = updates.toMap()
        return this
    }

    /**
     * Sets page size.
     *
     * @param size Items per page.
     */
    fun pageSize(size: Int): QueryBuilder {
        pageSizeValue = size
        return this
    }

    /**
     * Sets next page token.
     *
     * @param token Next page token.
     */
    fun nextPage(token: String): QueryBuilder {
        nextPageValue = token
        return this
    }

    private fun buildCommonOptions(): Map<String, Any?> =
        mapOf(
            "partition" to partitionValue,
            "pageSize" to pageSizeValue,
            "nextPage" to nextPageValue
        ).filterValues { it != null }

    private fun buildSelectQueryPayload(): Map<String, Any?> =
        mapOf(
            "type" to "SelectQuery",
            "fields" to fields,
            "conditions" to conditions,
            "sort" to sort,
            "limit" to limitValue,
            "distinct" to distinctValue,
            "groupBy" to groupByValues,
            "resolvers" to resolvers.ifEmpty { null },
            "partition" to partitionValue
        ).filterValues { it != null }

    private fun buildUpdateQueryPayload(): Map<String, Any?> =
        mapOf(
            "type" to "UpdateQuery",
            "conditions" to conditions,
            "updates" to updates,
            "partition" to partitionValue
        ).filterValues { it != null && !(it is List<*> && it.isEmpty()) }

    private fun buildDeleteQueryPayload(): Map<String, Any?> =
        mapOf(
            "type" to "SelectQuery",
            "conditions" to conditions,
            "partition" to partitionValue
        ).filterValues { it != null }

    /**
     * Executes the query and returns the first page of results.
     *
     * @param T Result type.
     */
    inline fun <reified T : Any> list(): QueryResults<T> = list(T::class)

    /**
     * Executes the query and returns the first page of results.
     *
     * @param T Result type.
     * @param type Class token for deserialization.
     */
    fun <T : Any> list(type: KClass<*>): QueryResults<T> {
        check(mode == Mode.SELECT) { "Cannot call list() when the builder is in ${mode.name} mode." }
        val targetTable = table ?: throw IllegalStateException("Table name must be specified using from() before calling list().")
        val queryPayload = buildSelectQueryPayload()
        val requestOptions = buildCommonOptions()
        val jsonResponse = client.executeQuery(targetTable, queryPayload, requestOptions)
        val results = (jsonResponse.fromJson(QueryResults::class) as? QueryResults<T>) ?: QueryResults()
        results.query = this
        results.classType = type
        return results
    }

    /**
     * Counts rows that match current conditions.
     */
    fun count(): Int {
        val targetTable = table ?: throw IllegalStateException("Table name must be specified using from() before calling count().")
        val countQueryPayload = mapOf(
            "type" to "SelectQuery",
            "conditions" to conditions,
            "partition" to partitionValue
        ).filterValues { it != null }
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeCountForQuery(targetTable, countQueryPayload, requestOptions)
    }

    /**
     * Deletes rows that match current conditions.
     *
     * @return Rows deleted.
     */
    fun delete(): Int {
        check(mode == Mode.SELECT || mode == Mode.DELETE) { "delete() can only be called after setting conditions (where/and/or/partition), not in update mode." }
        val targetTable = table ?: throw IllegalStateException("Table name must be specified using from() before calling delete().")
        mode = Mode.DELETE
        val queryPayload = buildDeleteQueryPayload()
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeDeleteQuery(targetTable, queryPayload, requestOptions)
    }

    /**
     * Applies updates to rows that match current conditions.
     *
     * @return Rows updated.
     */
    fun update(): Int {
        check(mode == Mode.UPDATE) { "Must call setUpdates(...) before calling update()." }
        check(updates != null) { "No updates specified. Call setUpdates(...) first." }
        val targetTable = table ?: throw IllegalStateException("Table name must be specified using from() before calling update().")
        val queryPayload = buildUpdateQueryPayload()
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeUpdateQuery(targetTable, queryPayload, requestOptions)
    }

    /**
     * Registers a creation listener for streaming.
     *
     * @param T Entity type.
     * @param listener Callback for created items.
     */
    fun <T : Any> onItemAdded(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemAddedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a deletion listener for streaming.
     *
     * @param T Entity type.
     * @param listener Callback for deleted items.
     */
    fun <T : Any> onItemDeleted(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemDeletedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers an update listener for streaming.
     *
     * @param T Entity type.
     * @param listener Callback for updated items.
     */
    fun <T : Any> onItemUpdated(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemUpdatedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a listener for initial query results on streaming.
     *
     * @param T Entity type.
     * @param listener Callback for initial results.
     */
    fun <T : Any> onItem(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Starts streaming with optional initial results.
     *
     * @param T Entity type.
     * @param includeQueryResults Include initial results.
     * @param keepAlive Keep the stream open.
     */
    inline fun <reified T : Any> stream(includeQueryResults: Boolean = true, keepAlive: Boolean = false): Job =
        stream<T>(T::class, includeQueryResults, keepAlive)

    /**
     * Starts streaming with optional initial results.
     *
     * @param T Entity type.
     * @param type Class token for deserialization.
     * @param includeQueryResults Include initial results.
     * @param keepAlive Keep the stream open.
     */
    fun <T> stream(type: KClass<*>, includeQueryResults: Boolean = true, keepAlive: Boolean = false): Job {
        check(mode == Mode.SELECT) { "Streaming is only applicable in select mode." }
        val targetTable = table ?: throw IllegalStateException("Table name must be specified using from() before calling stream().")
        val queryPayload = buildSelectQueryPayload()

        return CoroutineScope(Dispatchers.IO).launch {
            client.stream(targetTable, queryPayload, includeQueryResults, keepAlive)
                .transform { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val obj = trimmed.fromJson<StreamResponse>()!!
                            emit(obj)
                        } catch (_: Exception) { }
                    }
                }
                .onEach { response ->
                    when (response.action) {
                        "CREATE" -> onItemAddedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "UPDATE" -> onItemUpdatedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "DELETE" -> onItemDeletedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "QUERY_RESPONSE" -> onItemListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                    }
                }
                .collect()
        }
    }

    /**
     * Page of results with helpers for iteration and aggregation.
     *
     * @param T Record type.
     * @property recordText Raw JSON array of records.
     * @property nextPage Next page token.
     * @property totalResults Total matches if provided.
     */
    data class QueryResults<T : Any>(
        @SerializedName("records")
        internal val recordText: JsonArray = JsonArray(),
        val nextPage: String? = null,
        val totalResults: Int = 0,
    ) {
        @Transient internal var query: QueryBuilder? = null
        @Transient internal var classType: KClass<*>? = null

        /** Current page records. */
        val records: List<T> by lazy {
            try {
                val javaType = classType?.java ?: error("Class type not set for deserialization")
                recordText.toString().fromJsonList(javaType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** First record on the current page. */
        fun first(): T = records.first()

        /** First record or null when empty. */
        fun firstOrNull(): T? = records.firstOrNull()

        /** True when current page has no records. */
        fun isEmpty(): Boolean = records.isEmpty()

        /** Count of records on the current page. */
        fun size(): Int = records.size

        /**
         * Iterates over current page records.
         *
         * @param action Action to invoke for each record.
         */
        fun forEachOnPage(action: (T) -> Unit) {
            records.forEach(action)
        }

        /**
         * Iterates across all pages and records until the action returns false.
         *
         * @param action Return false to stop iteration.
         */
        fun forEach(action: (T) -> Boolean) {
            var continueAll = true
            forEachPage { page ->
                for (item in page) {
                    if (!action(item)) {
                        continueAll = false
                        break
                    }
                }
                continueAll
            }
        }

        /**
         * Iterates page-by-page until the action returns false.
         *
         * @param action Return false to stop paging.
         */
        fun forEachPage(action: (pageRecords: List<T>) -> Boolean) {
            var currentPage: QueryResults<T>? = this
            val currentQuery = query ?: error("Query context is missing for pagination.")
            val currentClassType = classType ?: error("Class type is missing for pagination.")
            var continuePaging = true

            while (currentPage != null) {
                val recordsOnPage = currentPage.records
                if (recordsOnPage.isNotEmpty()) {
                    continuePaging = action(recordsOnPage)
                }
                val nextPageToken = currentPage.nextPage
                currentPage = if (continuePaging && !nextPageToken.isNullOrBlank()) {
                    currentQuery.nextPage(nextPageToken).list(currentClassType)
                } else {
                    null
                }
            }
        }

        /**
         * Loads all pages and returns all records as a list.
         */
        fun getAllRecords(): List<T> {
            val all = mutableListOf<T>()
            forEachPage { page ->
                all.addAll(page)
                true
            }
            return all
        }

        /** Filters all records (loads into memory). */
        fun filter(predicate: (T) -> Boolean): List<T> = getAllRecords().filter(predicate)

        /** Maps all records (loads into memory). */
        fun <R> map(transform: (T) -> R): List<R> = getAllRecords().map(transform)

        /** Max of a Double selector across all records. */
        fun maxOfDouble(selector: (T) -> Double): Double = getAllRecords().maxOfOrNull(selector) ?: Double.NaN

        /** Min of a Double selector across all records. */
        fun minOfDouble(selector: (T) -> Double): Double = getAllRecords().minOfOrNull(selector) ?: Double.NaN

        /** Sum of a Double selector across all records. */
        fun sumOfDouble(selector: (T) -> Double): Double = getAllRecords().sumOf(selector)

        /** Max of a Float selector across all records. */
        fun maxOfFloat(selector: (T) -> Float): Float = getAllRecords().maxOfOrNull(selector) ?: Float.NaN

        /** Min of a Float selector across all records. */
        fun minOfFloat(selector: (T) -> Float): Float = getAllRecords().minOfOrNull(selector) ?: Float.NaN

        /** Max of an Int selector across all records. */
        fun maxOfInt(selector: (T) -> Int): Int = getAllRecords().maxOfOrNull(selector) ?: Int.MIN_VALUE

        /** Min of an Int selector across all records. */
        fun minOfInt(selector: (T) -> Int): Int = getAllRecords().minOfOrNull(selector) ?: Int.MAX_VALUE

        /** Sum of an Int selector across all records. */
        fun sumOfInt(selector: (T) -> Int): Int = getAllRecords().sumOf(selector)

        /** Max of a Long selector across all records. */
        fun maxOfLong(selector: (T) -> Long): Long = getAllRecords().maxOfOrNull(selector) ?: Long.MIN_VALUE

        /** Min of a Long selector across all records. */
        fun minOfLong(selector: (T) -> Long): Long = getAllRecords().minOfOrNull(selector) ?: Long.MAX_VALUE

        /** Sum of a Long selector across all records. */
        fun sumOfLong(selector: (T) -> Long): Long = getAllRecords().sumOf(selector)

        /** Sum of BigDecimals across all records. */
        fun sumOfBigDecimal(selector: (T) -> BigDecimal): BigDecimal =
            getAllRecords().map(selector).fold(BigDecimal.ZERO, BigDecimal::add)

        /**
         * Processes each page sequentially and items within a page in parallel.
         *
         * @param action Action executed in parallel per item.
         */
        fun forEachPageParallel(action: (T) -> Unit) {
            val fetchExecutor = Executors.newSingleThreadExecutor()
            var currentPage: QueryResults<T>? = this
            val currentQuery = query ?: error("Query context is missing for pagination.")
            val currentClassType = classType ?: error("Class type is missing for pagination.")

            try {
                while (currentPage != null) {
                    val recordsOnPage = currentPage.records
                    val nextPageToken = currentPage.nextPage
                    if (recordsOnPage.isNotEmpty()) {
                        recordsOnPage.parallelStream().forEach(action)
                    }
                    val future = if (!nextPageToken.isNullOrBlank()) {
                        fetchExecutor.submit<QueryResults<T>?> {
                            try {
                                currentQuery.nextPage(nextPageToken).list(currentClassType)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    } else null
                    currentPage = future?.get()
                }
            } finally {
                fetchExecutor.shutdown()
            }
        }
    }
}

/**
 * Sort order for a field.
 *
 * @param field Field name.
 * @param order "ASC" or "DESC".
 */
data class SortOrder(val field: String, val order: String = "ASC") {
    init {
        require(order.uppercase() == "ASC" || order.uppercase() == "DESC") {
            "Order must be 'ASC' or 'DESC', but was '$order'"
        }
    }
}

/**
 * Ascending sort helper.
 *
 * @param attribute Field name.
 */
fun asc(attribute: String) = SortOrder(attribute, order = "ASC")

/**
 * Descending sort helper.
 *
 * @param attribute Field name.
 */
fun desc(attribute: String) = SortOrder(attribute, order = "DESC")

/**
 * Logical operator for compound conditions.
 */
enum class LogicalOperator { AND, OR }

/**
 * Query criteria operators.
 */
enum class QueryCriteriaOperator {
    EQUAL, NOT_EQUAL,
    IN, NOT_IN,
    GREATER_THAN, GREATER_THAN_EQUAL,
    LESS_THAN, LESS_THAN_EQUAL,
    MATCHES, NOT_MATCHES,
    BETWEEN,
    LIKE, NOT_LIKE,
    CONTAINS, CONTAINS_IGNORE_CASE,
    NOT_CONTAINS, NOT_CONTAINS_IGNORE_CASE,
    STARTS_WITH, NOT_STARTS_WITH,
    IS_NULL, NOT_NULL
}

/**
 * Single query criterion.
 *
 * @param field Field name.
 * @param operator Operator type.
 * @param value Value or list value depending on operator.
 */
data class QueryCriteria(
    val field: String,
    val operator: QueryCriteriaOperator,
    val value: Any? = null
)

/**
 * Discriminated union for query conditions.
 */
sealed class QueryCondition {
    /**
     * Leaf condition.
     */
    data class SingleCondition(
        val criteria: QueryCriteria,
        val conditionType: String = "SingleCondition"
    ) : QueryCondition()

    /**
     * Composite condition with a logical operator.
     */
    data class CompoundCondition(
        val operator: LogicalOperator,
        val conditions: List<QueryCondition>,
        val conditionType: String = "CompoundCondition"
    ) : QueryCondition()
}

/**
 * Builds a compound condition from parts.
 *
 * @param operator "AND" or "OR".
 * @param conditions Items to combine.
 */
internal fun buildCompoundCondition(operator: String, conditions: List<Any?>): QueryCondition.CompoundCondition {
    val logicalOp = LogicalOperator.valueOf(operator.uppercase())
    val parsedConds = conditions.mapNotNull { cond ->
        when (cond) {
            is QueryCondition -> cond
            is ConditionBuilder -> cond.toCondition()
            else -> null
        }
    }
    return QueryCondition.CompoundCondition(operator = logicalOp, conditions = parsedConds)
}

/**
 * Document model for the document endpoints.
 */
data class Document(
    val documentId: String = "",
    val path: String = "",
    val created: Date = Date(),
    val updated: Date = Date(),
    val mimeType: String = "",
    val content: String = ""
)

/**
 * Example Contact model.
 */
data class Contact(
    val id: Int = 0,
    val contactType: String = "",
    val email: String = "",
    val name: String = "",
    val message: String = "",
    val billingIssue: Boolean? = null,
    val salesInquiry: Boolean? = null,
    val subjects: String = "",
    val timestamp: String = ""
)

/**
 * Builder for creating complex where-clauses.
 */
class ConditionBuilder internal constructor(criteria: QueryCriteria? = null) {
    private var condition: QueryCondition? = criteria?.let { QueryCondition.SingleCondition(it) }

    /**
     * AND combine with another builder.
     */
    fun and(other: ConditionBuilder): ConditionBuilder {
        combine(LogicalOperator.AND, other.toCondition()); return this
    }

    /**
     * OR combine with another builder.
     */
    fun or(other: ConditionBuilder): ConditionBuilder {
        combine(LogicalOperator.OR, other.toCondition()); return this
    }

    private fun combine(operator: LogicalOperator, newCondition: QueryCondition?) {
        val currentCondition = condition
        if (newCondition == null) return
        condition = when (currentCondition) {
            null -> newCondition
            is QueryCondition.SingleCondition, is QueryCondition.CompoundCondition -> {
                if (currentCondition is QueryCondition.CompoundCondition && currentCondition.operator == operator) {
                    currentCondition.copy(conditions = currentCondition.conditions + newCondition)
                } else {
                    QueryCondition.CompoundCondition(
                        operator = operator,
                        conditions = listOfNotNull(currentCondition, newCondition)
                    )
                }
            }
        }
    }

    internal fun toCondition(): QueryCondition? = condition
}

/**
 * Equals criterion.
 */
infix fun String.eq(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.EQUAL, value))

/**
 * Not-equals criterion.
 */
infix fun String.neq(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value))

/**
 * In-list criterion.
 */
infix fun String.inOp(values: List<Any?>): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.IN, values.joinToString(",") { it.toString() }))

/**
 * Not-in-list criterion.
 */
infix fun String.notIn(values: List<Any?>): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values))

/**
 * Greater-than criterion.
 */
infix fun String.gt(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value))

/**
 * Greater-than-or-equal criterion.
 */
infix fun String.gte(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value))

/**
 * Less-than criterion.
 */
infix fun String.lt(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value))

/**
 * Less-than-or-equal criterion.
 */
infix fun String.lte(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value))

/**
 * Regex match criterion.
 */
infix fun String.matches(regex: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.MATCHES, regex))

/**
 * Regex not-match criterion.
 */
infix fun String.notMatches(regex: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, regex))

/**
 * Between inclusive criterion.
 */
fun String.between(lower: Any?, upper: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(lower, upper)))

/**
 * SQL-like pattern match criterion.
 */
infix fun String.like(pattern: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LIKE, pattern))

/**
 * SQL-like negative pattern match criterion.
 */
infix fun String.notLike(pattern: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, pattern))

/**
 * Contains criterion.
 */
infix fun String.contains(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value))

/**
 * Contains ignore-case criterion.
 */
infix fun String.containsIgnoreCase(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value))

/**
 * Not contains ignore-case criterion.
 */
infix fun String.notContainsIgnoreCase(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value))

/**
 * Not contains criterion.
 */
infix fun String.notContains(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value))

/**
 * Starts-with criterion.
 */
infix fun String.startsWith(prefix: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, prefix))

/**
 * Not-starts-with criterion.
 */
infix fun String.notStartsWith(prefix: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, prefix))

/**
 * Is-null criterion.
 */
fun String.isNull(): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.IS_NULL))

/**
 * Not-null criterion.
 */
fun String.notNull(): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_NULL))

/**
 * Average aggregate expression string.
 */
fun avg(attribute: String): String = "avg($attribute)"

/**
 * Sum aggregate expression string.
 */
fun sum(attribute: String): String = "sum($attribute)"

/**
 * Count aggregate expression string.
 */
fun count(attribute: String): String = "count($attribute)"

/**
 * Min aggregate expression string.
 */
fun min(attribute: String): String = "min($attribute)"

/**
 * Max aggregate expression string.
 */
fun max(attribute: String): String = "max($attribute)"

/**
 * Median aggregate expression string.
 */
fun median(attribute: String): String = "median($attribute)"

/**
 * Standard deviation aggregate expression string.
 */
fun std(attribute: String): String = "std($attribute)"

/**
 * Uppercase function expression string.
 */
fun upper(attribute: String): String = "upper($attribute)"

/**
 * Lowercase function expression string.
 */
fun lower(attribute: String): String = "lower($attribute)"

/**
 * Substring function expression string.
 */
fun substring(attribute: String, from: Int, length: Int): String = "substring($attribute,$from,$length)"

/**
 * Replace function expression string with basic quote escaping.
 */
fun replace(attribute: String, pattern: String, repl: String): String =
    "replace($attribute, '${pattern.replace("'", "''")}', '${repl.replace("'", "''")}')"

/**
 * Stream response envelope.
 *
 * @property action Event type.
 * @property entity Raw entity object.
 */
internal data class StreamResponse(
    val action: String?,
    val entity: JsonObject?
)
