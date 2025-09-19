@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyx.cloud.impl

import com.google.gson.*
import com.onyx.cloud.api.DeleteOptions
import com.onyx.cloud.api.DocumentOptions
import com.onyx.cloud.api.FindOptions
import com.onyx.cloud.api.ICascadeBuilder
import com.onyx.cloud.api.IConditionBuilder
import com.onyx.cloud.api.IOnyxDatabase
import com.onyx.cloud.api.IQueryBuilder
import com.onyx.cloud.api.IQueryResults
import com.onyx.cloud.api.IStreamSubscription
import com.onyx.cloud.api.LogicalOperator
import com.onyx.cloud.api.OnyxDocument
import com.onyx.cloud.api.QueryCriteria
import com.onyx.cloud.api.SaveOptions
import com.onyx.cloud.api.Sort
import com.onyx.cloud.exceptions.NotFoundException
import com.onyx.cloud.extensions.fromJson
import com.onyx.cloud.extensions.fromJsonList
import com.onyx.cloud.extensions.gson
import com.onyx.cloud.extensions.toJson
import com.onyx.cloud.extensions.toQueryResults
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.xml.validation.Schema
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
) : IOnyxDatabase<Schema> {
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
     * Retrieves a document by ID.
     *
     * @param documentId Document identifier.
     * @param options Optional query parameters such as image sizing.
     * @return Raw document payload as a string.
     */
    override fun getDocument(documentId: String, options: DocumentOptions?): Any? {
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
    override fun deleteDocument(documentId: String): Boolean {
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        return makeRequest(HttpMethod.Delete, path).equals("true", ignoreCase = true)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    /**
     * Saves an entity or a list of entities.
     *
     * @param table Target entity type.
     * @param entityOrEntities Entity or list of entities.
     * @return Saved entity or original list.
     */
    @Suppress("UNCHECKED_CAST")
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
     * Finds an entity by primary key.
     *
     * @param T Result type.
     * @param type Entity class.
     * @param primaryKey Key value.
     * @param options Optional query options (partition, fetch).
     * @return Entity or null when not found.
     */
    override fun <T> findById(
        type: KClass<*>,
        primaryKey: Any,
        options: FindOptions?
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
     * Deletes an entity by key.
     *
     * @param table Table name.
     * @param primaryKey Key value.
     * @param options Optional delete options such as partition or cascade relationships.
     */
    override fun delete(
        table: String,
        primaryKey: String,
        options: DeleteOptions?
    ): Boolean {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/${encode(table)}/${encode(primaryKey)}"
        return makeRequest(HttpMethod.Delete, path, queryString = queryString).equals("true", ignoreCase = true)
    }

    override fun saveDocument(doc: OnyxDocument): OnyxDocument {
        val path = "/data/${encode(databaseId)}/document"
        return makeRequest(HttpMethod.Put, path, doc).fromJson<OnyxDocument>()
            ?: throw IllegalStateException("Failed to parse response for saveDocument")
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
     * Opens a JSONL stream and invokes [onLine] for each line of the response.
     *
     * The stream is processed on a dedicated daemon thread and the returned [StreamSubscription]
     * can be used to stop listening for events when desired.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param includeQueryResults Emit initial results first.
     * @param keepAlive Keep connection open for changes.
     * @param onLine Callback invoked for each raw JSON line from the stream.
     * @return An active [StreamSubscription] controlling the stream lifecycle.
     */
    fun stream(
        table: String,
        selectQuery: Any,
        includeQueryResults: Boolean = true,
        keepAlive: Boolean = false,
        onLine: (String) -> Unit,
    ): StreamSubscription {
        val params = "?includeQueryResults=$includeQueryResults&keepAlive=$keepAlive"
        val encodedDbId = encode(databaseId)
        val encodedTable = encode(table)
        val path = "/data/$encodedDbId/query/stream/$encodedTable$params"
        val urlStr = "$baseUrl$path"

        val isActive = AtomicBoolean(true)
        val connectionRef = AtomicReference<HttpURLConnection?>()
        val errorRef = AtomicReference<Throwable?>()

        val thread = Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URI(urlStr).toURL()
                conn = (url.openConnection() as HttpURLConnection).also { connectionRef.set(it) }

                val payload = selectQuery.toJson()
                val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)

                conn.requestMethod = "PUT"
                conn.instanceFollowRedirects = false // avoid silent body drop on 30x
                conn.connectTimeout = Timeouts.CONNECT_TIMEOUT
                conn.readTimeout = Timeouts.STREAM_READ_TIMEOUT
                conn.doInput = true
                conn.doOutput = true
                conn.useCaches = false

                applyHeaders(conn, defaultHeaders())
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/x-ndjson, application/json")

                // send exact length (safer than chunked for small JSON)
                conn.setFixedLengthStreamingMode(payloadBytes.size)

                conn.outputStream.use { os ->
                    os.write(payloadBytes)
                    os.flush()
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val errorBody = conn.bodyAsString()
                    throw RuntimeException("HTTP Error: $code ${conn.responseMessage}. Body: $errorBody")
                }

                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (isActive.get() && !Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        if (!isActive.get()) break
                        try {
                            onLine(line)
                        } catch (consumerError: Throwable) {
                            errorRef.compareAndSet(null, consumerError)
                            isActive.set(false)
                            break
                        }
                    }
                }
            } catch (ex: Throwable) {
                if (isActive.get()) errorRef.compareAndSet(null, ex)
            } finally {
                isActive.set(false)
                connectionRef.getAndSet(null)?.disconnect()
                conn?.disconnect()
            }
        }

        thread.isDaemon = true
        thread.start()

        return StreamSubscription(
            thread,
            cancelAction = {
                if (isActive.compareAndSet(true, false)) {
                    connectionRef.getAndSet(null)?.disconnect()
                }
            },
            errorRef = errorRef
        )
    }

    /**
     * Creates a [QueryBuilder] inferring the table from [T].
     *
     * @param T Entity type.
     */
    inline fun <reified T> from(): IQueryBuilder = QueryBuilder(this, T::class)

    /**
     * Creates a [QueryBuilder] with selected fields.
     *
     * @param fields Field names or aggregate expressions.
     */
    override fun select(vararg fields: String): IQueryBuilder {
        val qb = QueryBuilder(this)
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

    /**
     * Saves an entity or list of entities with additional query options (e.g., cascade relationships).
     *
     * @param table Table name.
     * @param entityOrEntities Single entity or list of entities to save.
     * @param options Map of query parameters (e.g., "relationships").
     * @return Saved entity or original list.
     */
    fun <T> save(table: KClass<*>, entityOrEntities: T, options: Map<String, Any?>): Any? {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/${encode(table.simpleName!!)}"
        // Perform save request and capture raw response
        val response = makeRequest(HttpMethod.Put, path, entityOrEntities, queryString = queryString)
        // Handle cascade updates or batch save: server may return an array or a single object
        if (entityOrEntities is List<*> || options.containsKey("relationships")) {
            // Determine element type from payload or default to Any
            return if (entityOrEntities is List<*>) {
                val elementType = entityOrEntities.firstOrNull()?.javaClass
                // Fallback to Any
                val actualType = elementType ?: Any::class.java
                // Try parsing response as array
                response.fromJsonList<Any>(actualType)?.let { return it }
            } else {
                val elementType = entityOrEntities!!.javaClass
                response.fromJson<Any>(elementType.kotlin).let { return it }
            }
            // Fallback to single-object response wrapped in list
            throw IllegalStateException("Failed to parse response for save list of entities")
        }
        // Handle saving a single entity: server may return an object or an array
        val entityType = entityOrEntities!!::class.java
        response.fromJson<Any>(entityType.kotlin).let { return it }
        response.fromJsonList<Any>(entityType)?.firstOrNull()?.let { return it }
        throw IllegalStateException("Failed to parse response for save single entity")
    }

    /**
     * Begins a cascading save or delete operation that includes specified relationships.
     *
     * @param relationships Relationship graph strings to cascade.
     * @return Builder to perform save or delete with cascade.
     */
    override fun cascade(vararg relationships: String): ICascadeBuilder =
        CascadeBuilderImpl(this, relationships.toList())

    @Suppress("UNCHECKED_CAST")
    override fun <T> save(
        table: KClass<*>,
        entityOrEntities: T,
        options: SaveOptions?
    ): T {
        return save(
            table, entityOrEntities, hashMapOf<String, List<String>?>(
                "relationships" to options?.relationships
            )
        ) as T
    }

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

    private fun buildQueryString(options: DeleteOptions?): String {
        if (options == null) return ""
        val params = mutableMapOf<String, Any?>()
        options.partition?.let { params["partition"] = it }
        options.relationships?.let { params["relationships"] = it }
        return buildQueryString(params)
    }

    private fun buildQueryString(options: DocumentOptions?): String {
        if (options == null) return ""
        val params = mutableMapOf<String, Any?>()
        options.height?.let { params["height"] = it }
        options.width?.let { params["width"] = it }
        return buildQueryString(params)
    }

    private fun buildQueryString(options: FindOptions?): String {
        if (options == null) return ""
        val params = mutableMapOf<String, Any?>()
        options.partition?.let { params["partition"] = it }
        options.resolvers?.let { params["resolvers"] = it }
        return buildQueryString(params)
    }

    private fun buildQueryString(options: Map<String, Any?>): String {
        val params = buildList {
            options.forEach { (key, value) ->
                when {
                    value == null -> { /* skip */
                    }

                    (key == "fetch" || key == "relationships") && value is List<*> -> {
                        val list = value.filterNotNull().joinToString(",")
                        if (list.isNotEmpty()) add("$key=${encode(list)}")
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
        var currentUrl = URI("$baseUrl$path$queryString").toURL()
        var methodToUse = method.value
        var redirects = 0

        // Prepare body bytes up-front (if any)
        var bodyBytes: ByteArray? = null
        if ((methodToUse == "POST" || methodToUse == "PUT") && body != null) {
            val payload = (body as? String) ?: body.toJson()
            bodyBytes = payload.toByteArray(StandardCharsets.UTF_8)
        }

        while (true) {
            val conn = (currentUrl.openConnection() as HttpURLConnection)
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = Timeouts.CONNECT_TIMEOUT
                conn.readTimeout = Timeouts.REQUEST_TIMEOUT
                conn.useCaches = false
                conn.doInput = true
                conn.doOutput = bodyBytes != null
                conn.requestMethod = methodToUse

                // Headers
                applyHeaders(conn, defaultHeaders(extraHeaders))
                if (conn.getRequestProperty("Accept").isNullOrEmpty()) {
                    conn.setRequestProperty("Accept", "application/json")
                }
                if (bodyBytes != null && conn.getRequestProperty("Content-Type").isNullOrEmpty()) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                // Fixed-length streaming (avoid chunked TE)
                if (bodyBytes != null) {
                    conn.setFixedLengthStreamingMode(bodyBytes.size)
                }

                conn.connect()

                if (bodyBytes != null) {
                    conn.outputStream.use { os ->
                        os.write(bodyBytes)
                        os.flush()
                    }
                }

                val code = conn.responseCode

                // Follow only 307/308 (preserve method + body)
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == 308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrEmpty() && redirects < 5) {
                        currentUrl = URI(location).toURL()
                        redirects++
                        continue
                    } else {
                        throw RuntimeException("Redirect ($code) without Location or too many redirects")
                    }
                }

                // Refuse 301/302/303 on methods with bodies (they would drop the body)
                if ((code == 301 || code == 303) && bodyBytes != null) {
                    val location = conn.getHeaderField("Location")
                    val txt = conn.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""
                    conn.disconnect()
                    throw RuntimeException("Refusing to follow $code redirect for $methodToUse with body to $location. Response: $txt")
                }

                val stream = if (code >= 400) (conn.errorStream ?: conn.inputStream) else conn.inputStream
                val text = stream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""

                if (code !in 200..299) {
                    val msg = "HTTP $code @ ${conn.url} → $text"
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
}

/**
 * Fluent builder for select, update and delete queries including streaming.
 *
 * @property client Backing [OnyxClient].
 * @property table Optional table name; must be set before execution.
 */
class QueryBuilder(
    private val client: OnyxClient,
    var type: KClass<*>? = null,
    var table: String? = type?.simpleName
) : IQueryBuilder {

    private var fields: List<String>? = null
    private var conditions: QueryCondition? = null
    private var sort: List<Sort>? = null
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
     * Selects fields.
     *
     * @param fields Field names or expressions.
     */
    override fun select(vararg fields: String): QueryBuilder {
        this.fields = fields.toList().ifEmpty { null }
        this.mode = Mode.SELECT
        return this
    }

    /**
     * Replaces existing conditions.
     *
     * @param condition Root condition.
     */
    override fun where(condition: IConditionBuilder): IQueryBuilder {
        this.conditions = condition.toCondition()
        return this
    }

    /**
     * Adds AND condition.
     *
     * @param condition Condition to add.
     */
    override fun and(condition: IConditionBuilder): IQueryBuilder {
        addCondition(condition, LogicalOperator.AND)
        return this
    }

    /**
     * Adds OR condition.
     *
     * @param condition Condition to add.
     */
    override fun or(condition: IConditionBuilder): QueryBuilder {
        addCondition(condition, LogicalOperator.OR)
        return this
    }

    private fun addCondition(builderToAdd: IConditionBuilder, logicalOperator: LogicalOperator) {
        val conditionToAdd = builderToAdd.toCondition() ?: return
        val currentCondition = this.conditions
        this.conditions = when {
            currentCondition == null -> conditionToAdd
            currentCondition is QueryCondition.CompoundCondition && currentCondition.operator == logicalOperator ->
                currentCondition.copy(conditions = currentCondition.conditions + conditionToAdd)

            else -> QueryCondition.CompoundCondition(
                operator = logicalOperator,
                conditions = listOfNotNull(currentCondition, conditionToAdd)
            )
        }
    }

    /**
     * Sets limit.
     *
     * @param limit Maximum rows.
     */
    override fun limit(limit: Int): QueryBuilder {
        limitValue = limit
        return this
    }

    /**
     * Enforces distinct results.
     */
    override fun distinct(): QueryBuilder {
        distinctValue = true
        return this
    }

    /**
     * Sets group-by.
     *
     * @param fields Fields to group by.
     */
    override fun groupBy(vararg fields: String): QueryBuilder {
        groupByValues = fields.toList().ifEmpty { null }
        return this
    }

    /**
     * Sets order-by.
     *
     * @param orders Sort orders.
     */
    override fun orderBy(vararg orders: Sort): IQueryBuilder {
        sort = orders.toList().ifEmpty { null }
        return this
    }

    /**
     * Scopes the query to a partition.
     *
     * @param partition Partition value.
     */
    override fun inPartition(partition: String): QueryBuilder {
        partitionValue = partition
        return this
    }

    /**
     * Requests relationship resolution.
     *
     * @param resolvers Relationships to resolve.
     */
    override fun resolve(vararg resolvers: String): QueryBuilder {
        this.resolvers = resolvers.toList()
        return this
    }

    /**
     * Switches to update mode with field values.
     *
     * @param updates Field/value pairs.
     */
    override fun setUpdates(vararg updates: Pair<String, Any?>): QueryBuilder {
        this.mode = Mode.UPDATE
        this.updates = updates.toMap()
        return this
    }

    /**
     * Sets page size.
     *
     * @param size Items per page.
     */
    override fun pageSize(size: Int): QueryBuilder {
        pageSizeValue = size
        return this
    }

    /**
     * Sets next page token.
     *
     * @param token Next page token.
     */
    override fun nextPage(token: String): QueryBuilder {
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
            "updates" to updates?.mapValues { it.value?.toString() },
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
     * @param type Class token for deserialization.
     */
    override fun <T : Any> list(): IQueryResults<T> {
        check(mode == Mode.SELECT) { "Cannot call list() when the builder is in ${mode.name} mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling list().")
        val queryPayload = buildSelectQueryPayload()
        val requestOptions = buildCommonOptions()
        val jsonResponse = client.executeQuery(targetTable, queryPayload, requestOptions)
        val results =
            jsonResponse.toQueryResults<T>(gson, if (fields?.isNotEmpty() == true) HashMap::class else this.type!!)
        results.query = this
        results.classType = if (fields?.isNotEmpty() == true) HashMap::class else this.type!!
        return results
    }

    override fun <T : Any> firstOrNull(): T? = this.list<T>().firstOrNull()
    override fun <T : Any> one(): T? = this.list<T>().firstOrNull()

    /**
     * Counts rows that match current conditions.
     */
    override fun count(): Int {
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling count().")
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
    override fun delete(): Int {
        check(mode == Mode.SELECT || mode == Mode.DELETE) { "delete() can only be called after setting conditions (where/and/or/partition), not in update mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling delete().")
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
    override fun update(): Int {
        check(mode == Mode.UPDATE) { "Must call setUpdates(...) before calling update()." }
        check(updates != null) { "No updates specified. Call setUpdates(...) first." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling update().")
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
    override fun <T : Any> onItemAdded(listener: (entity: T) -> Unit): QueryBuilder {
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
    override fun <T : Any> onItemDeleted(listener: (entity: T) -> Unit): QueryBuilder {
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
    override fun <T : Any> onItemUpdated(listener: (entity: T) -> Unit): QueryBuilder {
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
    override fun <T : Any> onItem(listener: (entity: T) -> Unit): IQueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Starts streaming with optional initial results.
     *
     * @param T Entity type.
     * @param type Class token for deserialization.
     * @param includeQueryResults Include initial results.
     * @param keepAlive Keep the stream open.
     */
    override fun <T> stream(
        includeQueryResults: Boolean,
        keepAlive: Boolean,
    ): IStreamSubscription {
        check(mode == Mode.SELECT) { "Streaming is only applicable in select mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling stream().")
        val queryPayload = buildSelectQueryPayload()

        return client.stream(
            targetTable,
            queryPayload,
            includeQueryResults,
            keepAlive,
        ) onLine@{ line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                return@onLine
            }

            val response = runCatching { trimmed.fromJson<StreamResponse>() }.getOrNull() ?: return@onLine
            val entityJson = response.entity?.toString() ?: return@onLine

            val entitySupplier = lazy {
                runCatching { entityJson.fromJson<T>(type!!) }.getOrNull()
            }

            fun deliver(listener: ((Any) -> Unit)?) {
                if (listener == null) return
                val entity = entitySupplier.value ?: return
                listener(entity as Any)
            }

            when (response.action?.uppercase(Locale.ROOT)) {
                "CREATE" -> deliver(onItemAddedListener)
                "UPDATE" -> deliver(onItemUpdatedListener)
                "DELETE" -> deliver(onItemDeletedListener)
                "QUERY_RESPONSE" -> deliver(onItemListener)
            }
        }
    }
}

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
 * Represents an active streaming subscription created via [OnyxClient.stream].
 *
 * The underlying stream executes on a daemon thread. Use [cancel], [join], or [cancelAndJoin]
 * to control its lifecycle. Invoking [close] behaves the same as [cancel].
 */
class StreamSubscription internal constructor(
    private val thread: Thread,
    private val cancelAction: () -> Unit,
    private val errorRef: AtomicReference<Throwable?>,
) : IStreamSubscription {
    private val cancelled = AtomicBoolean(false)

    /** Stops the stream without waiting for the background thread to finish. */
    override fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelAction()
            if (thread.isAlive) {
                thread.interrupt()
            }
        }
    }

    /** Waits for the background thread to finish processing. */
    override fun join() {
        try {
            thread.join()
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Convenience helper that cancels the stream and waits for it to finish. */
    override fun cancelAndJoin() {
        cancel()
        join()
    }

    /** Latest error observed while streaming, or `null` if none occurred. */
    override val error: Throwable?
        get() = errorRef.get()

    override fun close() {
        cancel()
    }
}

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
