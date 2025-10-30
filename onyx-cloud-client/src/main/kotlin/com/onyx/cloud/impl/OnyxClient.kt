@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyx.cloud.impl

import com.google.gson.JsonObject
import com.onyx.cloud.api.DeleteOptions
import com.onyx.cloud.api.DocumentOptions
import com.onyx.cloud.api.FetchImpl
import com.onyx.cloud.api.FetchInit
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.Volatile
import kotlin.reflect.KClass

/**
 * Minimal Onyx API client implemented using JDK networking only (no third-party HTTP client).
 *
 * This client focuses on correctness and predictable behavior in production:
 * - Uses fixed-length streaming for request bodies to avoid chunked‐transfer oddities.
 * - Explicitly refuses unsafe redirects that would drop request bodies (e.g., 301/303).
 * - Handles 307/308 redirects only (preserve method + body), with a sane redirect cap.
 * - Surfaces non-2xx responses with the response body for easier diagnostics.
 *
 * Typical usage:
 * ```
 * val db = OnyxClient(
 *   baseUrl = "https://api.onyx.dev",
 *   databaseId = "db_123",
 *   apiKey = "...",
 *   apiSecret = "..."
 * )
 *
 * val results = db
 *   .select("id", "name")
 *   .from<User>()
 *   .where("name".eq("Ada"))
 *   .list<User>()
 * ```
 *
 * @param baseUrl Base URL of the Onyx API (no trailing slash required).
 * @param databaseId Target database identifier.
 * @param apiKey API key header value.
 * @param apiSecret API secret header value.
 * @param authToken Optional Authorization header value.
 * @param fetch Optional custom HTTP implementation. When provided, all non-streaming
 * requests will delegate to this function instead of the default `HttpURLConnection`
 * transport.
 * @param defaultPartition Default partition applied to queries when none is specified
 * explicitly.
 * @param requestLoggingEnabled When `true`, the client logs outgoing requests (with
 * sensitive headers redacted).
 * @param responseLoggingEnabled When `true`, the client logs responses received from the
 * server.
 * @param ttl Optional time-to-live value (milliseconds) propagated via the `x-onyx-ttl`
 * header for credential caching.
 * @param requestTimeoutMsOverride Optional override for the non-streaming read timeout
 * in milliseconds. When `null`, a safe default is used.
 * @param connectTimeoutMsOverride Optional override for the socket connection timeout in
 * milliseconds. When `null`, a safe default is used.
 */
class OnyxClient(
    baseUrl: String = "https://api.onyx.dev",
    private val databaseId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val authToken: String? = null,
    private val fetch: FetchImpl? = null,
    internal val defaultPartition: String? = null,
    private val requestLoggingEnabled: Boolean = false,
    private val responseLoggingEnabled: Boolean = false,
    private val ttl: Long? = null,
    requestTimeoutMsOverride: Int? = null,
    connectTimeoutMsOverride: Int? = null
) : IOnyxDatabase<Any> {

    private val baseUrl: String = baseUrl.replace(Regex("/+$"), "")
    private val lifecycleLock = Any()
    private val activeStreams = ConcurrentHashMap.newKeySet<StreamSubscription>()

    private val requestTimeoutMs: Int = requestTimeoutMsOverride?.also {
        require(it > 0) { "requestTimeoutMs must be greater than zero but was $it" }
    } ?: Defaults.REQUEST_TIMEOUT_MS

    private val connectTimeoutMs: Int = connectTimeoutMsOverride?.also {
        require(it > 0) { "connectTimeoutMs must be greater than zero but was $it" }
    } ?: Defaults.CONNECT_TIMEOUT_MS

    private val streamReadTimeoutMs: Int = Defaults.STREAM_READ_TIMEOUT_MS

    @Volatile
    private var closed = false

    private class HttpMethod private constructor(val value: String) {
        companion object {
            val Get = HttpMethod("GET")
            val Put = HttpMethod("PUT")
            val Post = HttpMethod("POST")
            val Delete = HttpMethod("DELETE")
        }
    }

    private object Defaults {
        /** Max time to read non-stream requests (ms). 120 seconds keeps calls responsive without hanging indefinitely. */
        const val REQUEST_TIMEOUT_MS = 120_000
        /** Connect timeout (ms). */
        const val CONNECT_TIMEOUT_MS = 30_000
        /** Streaming read timeout (ms). 0 = infinite (socket-level). */
        const val STREAM_READ_TIMEOUT_MS = 0
    }

    // ---------------------------------------------------------------------
    // Documents
    // ---------------------------------------------------------------------

    /**
     * Retrieves a document by ID.
     *
     * @param documentId Document identifier.
     * @param options Optional rendering/size options.
     * @return Raw JSON or binary wrapper, depending on server response. May be `String` JSON.
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
     * @return `true` if deleted, `false` otherwise.
     */
    override fun deleteDocument(documentId: String): Boolean {
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        return makeRequest(HttpMethod.Delete, path).equals("true", ignoreCase = true)
    }

    /**
     * Saves or updates a document.
     *
     * @param doc The document to save.
     * @return The persisted document returned by the server.
     */
    override fun saveDocument(doc: OnyxDocument): OnyxDocument {
        val path = "/data/${encode(databaseId)}/document"
        return makeRequest(HttpMethod.Put, path, doc).fromJson<OnyxDocument>()
            ?: throw IllegalStateException("Failed to parse response for saveDocument")
    }

    // ---------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------

    /**
     * Saves an entity or a list of entities (no cascade options).
     *
     * @param T Entity type.
     * @param table The target entity class (table).
     * @param entityOrEntities Entity instance or list of entities to save.
     * @return The saved entity (or original list for batch saves).
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
     * Saves an entity or list of entities with querystring options (e.g., cascade relationships).
     *
     * @param table Table class.
     * @param entityOrEntities Entity instance or list.
     * @param options Querystring parameters (e.g., `"relationships"`).
     * @return Parsed server response when available, otherwise best-effort echo of input.
     */
    fun <T> save(table: KClass<*>, entityOrEntities: T, options: Map<String, Any?>): Any? {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/${encode(table.simpleName!!)}"
        val response = makeRequest(HttpMethod.Put, path, entityOrEntities, queryString = queryString)

        // Try to parse a sensible value back for common cases.
        return when (entityOrEntities) {
            is List<*> -> {
                val elementType = entityOrEntities.firstOrNull()?.javaClass
                if (elementType != null) {
                    response.fromJsonList<Any>(elementType) ?: entityOrEntities
                } else {
                    response // unknown element type—return raw response
                }
            }
            null -> response
            else -> {
                response.fromJson<Any>(entityOrEntities::class)
            }
        }
    }

    /**
     * Cascading save/delete entry point.
     *
     * @param relationships Relationship graph strings to cascade (e.g., `"orders:Order(userId,id)"`).
     * @return A builder that can perform `save` or `delete` with the cascade graph applied.
     */
    override fun cascade(vararg relationships: String): ICascadeBuilder =
        CascadeBuilderImpl(this, relationships.toList())

    /**
     * Saves an entity or list of entities with optional cascade relationships.
     *
     * @param table Table class.
     * @param entityOrEntities Entity instance or list.
     * @param options Optional save options (e.g., cascade relationships).
     * @return The saved entity or list.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> save(
        table: KClass<*>,
        entityOrEntities: T,
        options: SaveOptions?
    ): T {
        return save(
            table,
            entityOrEntities,
            hashMapOf<String, List<String>?>("relationships" to options?.relationships)
        ) as T
    }

    /**
     * Finds an entity by primary key.
     *
     * @param T Entity type.
     * @param type Entity class token.
     * @param primaryKey Primary key value.
     * @param options Optional find options (partition/resolvers).
     * @return The entity or `null` if not found.
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
     * Deletes an entity by primary key.
     *
     * @param table Table name.
     * @param primaryKey Primary key value.
     * @param options Optional delete options (partition/relationships).
     * @return `true` when the server reports success, otherwise `false`.
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

    // ---------------------------------------------------------------------
    // Query (non-streaming)
    // ---------------------------------------------------------------------

    /**
     * Executes a select query and returns the raw JSON response.
     *
     * @param table Table name.
     * @param selectQuery Query body (select clauses, conditions, etc.).
     * @param options Querystring options (e.g., pageSize, nextPage, partition).
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
     * Executes a count query (server counts rows that match the conditions).
     *
     * @param table Table name.
     * @param selectQuery Query body with conditions.
     * @param options Optional options (e.g., partition).
     * @return Count as an `Int` (0 if the server returns a non-numeric body).
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
     * Executes an update-by-query.
     *
     * @param table Table name.
     * @param updateQuery Update body (conditions + updates).
     * @param options Optional options (e.g., partition).
     * @return Number of updated rows.
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
     * @param options Optional options (e.g., partition).
     * @return Number of deleted rows.
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

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

    /**
     * Opens a NDJSON (JSON Lines) stream for a query and invokes [onLine] for each line.
     *
     * The stream runs on a dedicated daemon thread; use the returned [StreamSubscription] to
     * cancel or wait for completion. The client uses fixed-length streaming for the request body,
     * disables automatic redirect following, and validates 2xx status codes before reading.
     *
     * @param table Table name.
     * @param selectQuery Query body.
     * @param includeQueryResults Emit initial results first (if supported by the server).
     * @param keepAlive Keep connection open for change events after initial results.
     * @param onLine Callback invoked with each raw line from the stream (already UTF-8 decoded).
     * @return A [StreamSubscription] that controls the stream lifecycle and exposes the last error (if any).
     */
    fun stream(
        table: String,
        selectQuery: Any,
        includeQueryResults: Boolean = true,
        keepAlive: Boolean = false,
        onLine: (String) -> Unit,
    ): StreamSubscription {
        if (closed) {
            throw IllegalStateException("OnyxClient has been closed")
        }

        val params = "?includeQueryResults=$includeQueryResults&keepAlive=$keepAlive"
        val encodedDbId = encode(databaseId)
        val encodedTable = encode(table)
        val path = "/data/$encodedDbId/query/stream/$encodedTable$params"
        val urlStr = "$baseUrl$path"

        val isActive = AtomicBoolean(true)
        val connectionRef = AtomicReference<HttpURLConnection?>()
        val errorRef = AtomicReference<Throwable?>()

        lateinit var subscription: StreamSubscription
        val removed = AtomicBoolean(false)
        val removeFromRegistry = {
            if (removed.compareAndSet(false, true)) {
                synchronized(lifecycleLock) {
                    activeStreams.remove(subscription)
                }
            }
        }

        val thread = Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URI(urlStr).toURL()
                conn = (url.openConnection() as HttpURLConnection).also { connectionRef.set(it) }

                val payload = selectQuery.toJson()
                val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)

                conn.requestMethod = "PUT"
                conn.instanceFollowRedirects = false // avoid silent body drop on 30x
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = streamReadTimeoutMs
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
                removeFromRegistry()
            }
        }

        subscription = StreamSubscription(
            thread,
            cancelAction = {
                if (isActive.compareAndSet(true, false)) {
                    connectionRef.getAndSet(null)?.disconnect()
                }
            },
            errorRef = errorRef
        )

        synchronized(lifecycleLock) {
            if (closed) {
                removeFromRegistry()
                throw IllegalStateException("OnyxClient has been closed")
            }
            activeStreams.add(subscription)
            thread.isDaemon = true
            thread.start()
        }

        return subscription
    }

    // ---------------------------------------------------------------------
    // Builder entry points / helpers
    // ---------------------------------------------------------------------

    /**
     * Creates a [QueryBuilder] inferring the table from [T].
     *
     * @param T Entity type.
     */
    inline fun <reified T> from(): IQueryBuilder = QueryBuilder(this, T::class)

    /**
     * Creates a [QueryBuilder] inferring the table from table.
     *
     * @param table Entity type.
     */
    fun from(table: String): IQueryBuilder = QueryBuilder(this, table = table)

    /**
     * Starts a select query with the desired fields.
     *
     * @param fields Field names or aggregate expressions.
     * @return A [IQueryBuilder] for further fluency.
     */
    override fun select(vararg fields: String): IQueryBuilder {
        val qb = QueryBuilder(this)
        qb.select(*fields)
        return qb
    }

    /**
     * URL-encodes a string for safe inclusion in paths or querystrings.
     */
    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    /**
     * URL-encodes a class simple name (table).
     */
    fun encode(value: KClass<*>): String = encode(value.java.simpleName)

    /**
     * Cancels and waits for completion of all active stream subscriptions.
     *
     * Subsequent calls are idempotent. After closing, new streams cannot be created.
     */
    override fun close() {
        val subscriptionsToClose: List<StreamSubscription>
        synchronized(lifecycleLock) {
            if (closed) {
                return
            }
            closed = true
            subscriptionsToClose = activeStreams.toList()
        }

        var firstError: Throwable? = null
        subscriptionsToClose.forEach { subscription ->
            try {
                subscription.cancelAndJoin()
            } catch (ex: Throwable) {
                if (firstError == null) {
                    firstError = ex
                } else {
                    firstError.addSuppressed(ex)
                }
            } finally {
                synchronized(lifecycleLock) {
                    activeStreams.remove(subscription)
                }
            }
        }

        firstError?.let { throw it }
    }

    // ---------------------------------------------------------------------
    // Internal: HTTP + utilities
    // ---------------------------------------------------------------------

    private fun defaultHeaders(extra: Map<String, String> = emptyMap()): MutableMap<String, String> {
        val headers = if (apiKey.isNotEmpty()) {
            mutableMapOf(
                "x-onyx-key" to apiKey,
                "x-onyx-secret" to apiSecret,
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Connection" to "keep-alive"
            )
        } else {
            mutableMapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "Connection" to "keep-alive"
            )
        }
        authToken?.let { headers["Authorization"] = "Bearer $it" }
        ttl?.let { headers["x-onyx-ttl"] = it.toString() }
        headers.putAll(extra)
        return headers
    }

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

    @Suppress("DuplicatedCode")
    private fun buildQueryString(options: DeleteOptions?): String {
        if (options == null && defaultPartition == null) return ""
        val params = mutableMapOf<String, Any?>()
        val partition = options?.partition ?: defaultPartition
        partition?.let { params["partition"] = it }
        options?.relationships?.let { params["relationships"] = it }
        return buildQueryString(params)
    }

    private fun buildQueryString(options: DocumentOptions?): String {
        if (options == null && defaultPartition == null) return ""
        val params = mutableMapOf<String, Any?>()
        options?.height?.let { params["height"] = it }
        options?.width?.let { params["width"] = it }
        defaultPartition?.let { params["partition"] = it }
        return buildQueryString(params)
    }

    @Suppress("DuplicatedCode")
    private fun buildQueryString(options: FindOptions?): String {
        if (options == null && defaultPartition == null) return ""
        val params = mutableMapOf<String, Any?>()
        val partition = options?.partition ?: defaultPartition
        partition?.let { params["partition"] = it }
        options?.resolvers?.let { params["resolvers"] = it }
        return buildQueryString(params)
    }

    private fun buildQueryString(options: Map<String, Any?>): String {
        val params = buildList {
            options.forEach { (key, value) ->
                when {
                    value == null -> Unit
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

    /**
     * Performs an HTTP call with robust redirect and error handling.
     *
     * Behavior:
     * - Only 307/308 redirects are followed (at most 5 hops); other redirect codes are refused if a body is present.
     * - Returns the response body as UTF-8 string on 2xx.
     * - Throws [NotFoundException] on 404, or [RuntimeException] for other non-2xx codes with body included.
     */
    private fun makeRequest(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        queryString: String = ""
    ): String {
        val url = "$baseUrl$path$queryString"
        val methodValue = method.value
        val headers = defaultHeaders(extraHeaders)
        val payload = if ((methodValue == "POST" || methodValue == "PUT") && body != null) {
            (body as? String) ?: body.toJson()
        } else null

        if (headers["Accept"].isNullOrEmpty()) {
            headers["Accept"] = "application/json"
        }
        if (payload != null && headers["Content-Type"].isNullOrEmpty()) {
            headers["Content-Type"] = "application/json; charset=utf-8"
        }

        return if (fetch != null) {
            executeWithFetch(url, methodValue, headers, payload)
        } else {
            executeWithHttpUrlConnection(url, methodValue, headers, payload)
        }
    }

    private fun executeWithFetch(
        url: String,
        method: String,
        headers: MutableMap<String, String>,
        payload: String?
    ): String {
        if (requestLoggingEnabled) {
            logRequest(method, url, headers, payload)
        }
        val init = FetchInit(method = method, headers = headers.toMap(), body = payload)
        val response = fetch!!.invoke(url, init)
        val text = response.text()
        if (responseLoggingEnabled) {
            logResponse(response.status, url, text)
        }
        if (response.status !in 200..299) {
            val msg = "HTTP ${response.status} @ $url → $text"
            throw when (response.status) {
                404 -> NotFoundException(msg, RuntimeException("HTTP ${response.status}"))
                else -> RuntimeException(msg)
            }
        }
        return text
    }

    private fun executeWithHttpUrlConnection(
        url: String,
        method: String,
        headers: MutableMap<String, String>,
        payload: String?
    ): String {
        var currentUrl = URI(url).toURL()
        var methodToUse = method
        var redirects = 0
        val bodyBytes = payload?.toByteArray(StandardCharsets.UTF_8)

        while (true) {
            val conn = (currentUrl.openConnection() as HttpURLConnection)
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = requestTimeoutMs
                conn.useCaches = false
                conn.doInput = true
                conn.doOutput = bodyBytes != null
                conn.requestMethod = methodToUse

                applyHeaders(conn, headers)
                if (requestLoggingEnabled) {
                    logRequest(methodToUse, currentUrl.toString(), headers, payload)
                }

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

                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == 308) {
                    if (responseLoggingEnabled) {
                        logResponse(code, conn.url.toString(), "<redirect>")
                    }
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

                if ((code == 301 || code == 303) && bodyBytes != null) {
                    val location = conn.getHeaderField("Location")
                    val txt = conn.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""
                    if (responseLoggingEnabled) {
                        logResponse(code, conn.url.toString(), txt)
                    }
                    conn.disconnect()
                    throw RuntimeException("Refusing to follow $code redirect for $methodToUse with body to $location. Response: $txt")
                }

                val stream = if (code >= 400) (conn.errorStream ?: conn.inputStream) else conn.inputStream
                val text = stream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""
                if (responseLoggingEnabled) {
                    logResponse(code, conn.url.toString(), text)
                }

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

    private fun maskSensitiveHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (key, value) ->
            if (key.equals("x-onyx-secret", ignoreCase = true)) "****" else value
        }

    private fun logRequest(method: String, url: String, headers: Map<String, String>, body: String?) {
        val sanitizedHeaders = maskSensitiveHeaders(headers)
        val bodyPreview = body ?: "<empty>"
        println("OnyxClient Request: method=$method url=$url headers=$sanitizedHeaders body=$bodyPreview")
    }

    private fun logResponse(status: Int, url: String, body: String?) {
        val bodyPreview = body ?: "<empty>"
        println("OnyxClient Response: status=$status url=$url body=$bodyPreview")
    }
}

// =====================================================================
// Query builder
// =====================================================================

/**
 * Fluent builder for select/update/delete queries and event streaming.
 *
 * Construct via [OnyxClient.from] (type-inferred) or [OnyxClient.select] (fields-first).
 * Methods are chainable and validated so that illegal sequences throw early with clear messages.
 *
 * Example:
 * ```
 * val page = client.select("id","name")
 *   .from<User>()
 *   .where("age".gt(18))
 *   .orderBy(Sort("created", ascending = false))
 *   .pageSize(50)
 *   .list<User>()
 * ```
 *
 * @property client Underlying [OnyxClient] used to execute requests.
 * @property type Optional entity class (when inferred via [OnyxClient.from]).
 * @property table Optional table name; inferred from [type] when not set explicitly.
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
    private var partitionValue: String? = client.defaultPartition
    private var resolvers: List<String> = emptyList()
    private var updates: Map<String, Any?>? = null

    private enum class Mode { SELECT, UPDATE, DELETE }
    private var mode: Mode = Mode.SELECT

    private var pageSizeValue: Int? = null
    private var nextPageValue: String? = null

    // Streaming callbacks (typed at delivery time)
    private var onItemAddedListener: ((entity: Any) -> Unit)? = null
    private var onItemDeletedListener: ((entity: Any) -> Unit)? = null
    private var onItemUpdatedListener: ((entity: Any) -> Unit)? = null
    private var onItemListener: ((entity: Any) -> Unit)? = null

    /**
     * Sets the projection for SELECT queries.
     *
     * @param fields Field names or expressions/aggregates.
     */
    override fun select(vararg fields: String): QueryBuilder {
        this.fields = fields.toList().ifEmpty { null }
        this.mode = Mode.SELECT
        return this
    }

    /**
     * Replaces existing conditions with [condition].
     */
    override fun where(condition: IConditionBuilder): IQueryBuilder {
        this.conditions = condition.toCondition()
        return this
    }

    /**
     * Adds an `AND` condition to the current predicate.
     */
    override fun and(condition: IConditionBuilder): IQueryBuilder {
        addCondition(condition, LogicalOperator.AND)
        return this
    }

    /**
     * Adds an `OR` condition to the current predicate.
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
     * Limits the number of rows returned by the server.
     */
    override fun limit(limit: Int): QueryBuilder {
        limitValue = limit
        return this
    }

    /**
     * Requests distinct results for the projection.
     */
    override fun distinct(): QueryBuilder {
        distinctValue = true
        return this
    }

    /**
     * Groups results by the given fields.
     */
    override fun groupBy(vararg fields: String): QueryBuilder {
        groupByValues = fields.toList().ifEmpty { null }
        return this
    }

    /**
     * Applies sort ordering to the results.
     */
    override fun orderBy(vararg orders: Sort): IQueryBuilder {
        sort = orders.toList().ifEmpty { null }
        return this
    }

    /**
     * Scopes the query to a specific partition (shard/tenant).
     */
    override fun inPartition(partition: String): QueryBuilder {
        partitionValue = partition
        return this
    }

    /**
     * Requests relationship resolution for the given resolvers.
     */
    override fun resolve(vararg resolvers: String): QueryBuilder {
        this.resolvers = resolvers.toList()
        return this
    }

    /**
     * Switches to UPDATE mode and sets field/value updates.
     */
    override fun setUpdates(vararg updates: Pair<String, Any?>): QueryBuilder {
        this.mode = Mode.UPDATE
        this.updates = updates.toMap()
        return this
    }

    /**
     * Sets the desired page size when paginating results.
     */
    override fun pageSize(size: Int): QueryBuilder {
        pageSizeValue = size
        return this
    }

    /**
     * Sets the server-provided next page token for pagination.
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
     * Executes the (SELECT) query and returns the first page of results.
     *
     * Notes:
     * - When a projection is specified (`select("fieldA", ...)`) the result type defaults to `HashMap`
     *   so you can call `list<HashMap<String, Any>>()`. For full-entity selects, the inferred [type] is used.
     */
    override fun <T : Any> list(): IQueryResults<T> {
        check(mode == Mode.SELECT) { "Cannot call list() when the builder is in ${mode.name} mode." }
        val targetTable = table
            ?: throw IllegalStateException("Table name must be specified using from() before calling list().")
        val queryPayload = buildSelectQueryPayload()
        val requestOptions = buildCommonOptions()
        val jsonResponse = client.executeQuery(targetTable, queryPayload, requestOptions)

        val isProjection = fields?.isNotEmpty() == true
        val clazz = if (isProjection || (this.table != null && this.type == null)) HashMap::class else this.type
            ?: throw IllegalStateException("Result class cannot be determined. Use from<T>() or provide fields.")

        val results = jsonResponse.toQueryResults<T>(gson, clazz)
        results.query = this
        results.classType = clazz
        return results
    }

    /**
     * Executes the query and returns the first record or `null`.
     */
    override fun <T : Any> firstOrNull(): T? = this.list<T>().firstOrNull()

    /**
     * Alias for [firstOrNull] (kept for API parity).
     */
    override fun <T : Any> one(): T? = this.list<T>().firstOrNull()

    /**
     * Counts rows that match the current conditions.
     */
    override fun count(): Int {
        val targetTable = table
            ?: throw IllegalStateException("Table name must be specified using from() before calling count().")
        val countQueryPayload = mapOf(
            "type" to "SelectQuery",
            "conditions" to conditions,
            "partition" to partitionValue
        ).filterValues { it != null }
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeCountForQuery(targetTable, countQueryPayload, requestOptions)
    }

    /**
     * Deletes rows that match the current conditions.
     *
     * @return Number of deleted rows.
     */
    override fun delete(): Int {
        check(mode == Mode.SELECT || mode == Mode.DELETE) {
            "delete() can only be called after setting conditions (where/and/or/partition), not in update mode."
        }
        val targetTable = table
            ?: throw IllegalStateException("Table name must be specified using from() before calling delete().")
        mode = Mode.DELETE
        val queryPayload = buildDeleteQueryPayload()
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeDeleteQuery(targetTable, queryPayload, requestOptions)
    }

    /**
     * Applies updates to rows that match the current conditions.
     *
     * @return Number of updated rows.
     */
    override fun update(): Int {
        check(mode == Mode.UPDATE) { "Must call setUpdates(...) before calling update()." }
        check(updates != null) { "No updates specified. Call setUpdates(...) first." }
        val targetTable = table
            ?: throw IllegalStateException("Table name must be specified using from() before calling update().")
        val queryPayload = buildUpdateQueryPayload()
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }
        return client.executeUpdateQuery(targetTable, queryPayload, requestOptions)
    }

    // ---------------------- Streaming (builder) ----------------------

    /**
     * Registers a creation listener used when [stream] is invoked.
     */
    override fun <T : Any> onItemAdded(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemAddedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a deletion listener used when [stream] is invoked.
     */
    override fun <T : Any> onItemDeleted(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemDeletedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers an update listener used when [stream] is invoked.
     */
    override fun <T : Any> onItemUpdated(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemUpdatedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a listener for initial query results when [stream] is invoked with `includeQueryResults = true`.
     */
    override fun <T : Any> onItem(listener: (entity: T) -> Unit): IQueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Starts a change stream for the current SELECT query.
     *
     * @param includeQueryResults If `true`, delivers initial results via [onItem] callbacks prior to live events.
     * @param keepAlive If `true`, keeps the connection open for subsequent change events.
     * @return An [IStreamSubscription] to control the stream.
     */
    override fun <T> stream(
        includeQueryResults: Boolean,
        keepAlive: Boolean,
    ): IStreamSubscription {
        check(mode == Mode.SELECT) { "Streaming is only applicable in select mode." }
        val targetTable = table
            ?: throw IllegalStateException("Table name must be specified using from() before calling stream().")
        val queryPayload = buildSelectQueryPayload()

        return client.stream(
            targetTable,
            queryPayload,
            includeQueryResults,
            keepAlive,
        ) onLine@{ line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@onLine

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

// =====================================================================
// Query condition model
// =====================================================================

/**
 * Discriminated union for query conditions.
 */
sealed class QueryCondition {
    /**
     * Leaf condition consisting of a single [QueryCriteria].
     */
    data class SingleCondition(
        val criteria: QueryCriteria,
        val conditionType: String = "SingleCondition"
    ) : QueryCondition()

    /**
     * Composite condition with a logical operator applied to nested conditions.
     */
    data class CompoundCondition(
        val operator: LogicalOperator,
        val conditions: List<QueryCondition>,
        val conditionType: String = "CompoundCondition"
    ) : QueryCondition()
}

// =====================================================================
// Streaming subscription + envelope
// =====================================================================

/**
 * Represents an active streaming subscription created via [OnyxClient.stream].
 *
 * The underlying stream runs on a daemon thread. Use [cancel], [join], or [cancelAndJoin]
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
 * Stream response envelope (one line of NDJSON).
 *
 * @property action Event type (`CREATE`/`UPDATE`/`DELETE`/`QUERY_RESPONSE`).
 * @property entity Raw entity payload as JSON.
 */
internal data class StreamResponse(
    val action: String?,
    val entity: JsonObject?
)
