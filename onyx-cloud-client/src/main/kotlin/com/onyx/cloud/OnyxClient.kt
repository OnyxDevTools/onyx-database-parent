@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.onyx.cloud

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import com.onyx.cloud.exceptions.NotFoundException
import com.onyx.cloud.extensions.fromJson
import com.onyx.cloud.extensions.fromJsonList
import com.onyx.cloud.extensions.toJson
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.KClass

/**
 * Client for interacting with the Onyx API.
 * Provides methods for managing documents and data entities, executing queries, and streaming results.
 *
 * @property databaseId The ID of the Onyx database to interact with.
 * @property apiKey The API key for authentication.
 * @property apiSecret The API secret for authentication.
 * @param baseUrl The base URL of the Onyx API. Defaults to "https://api.onyx.dev".
 */
class OnyxClient(
    baseUrl: String = "https://api.onyx.dev",
    private val databaseId: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    // Remove trailing slashes from the base URL
    private val baseUrl: String = baseUrl.replace(Regex("/+$"), "")

    /**
     * Ktor HttpClient configured for standard API requests.
     * Includes long timeouts and connection pooling settings.
     */
    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000_000 // 20 minutes
            connectTimeoutMillis = 12_000_000 // 20 minutes
            socketTimeoutMillis = 12_000_000 // 20 minutes
        }
        engine {
            pipelining = false
        }
    }

    /**
     * Ktor HttpClient configured specifically for streaming API requests.
     * Uses shorter connect timeouts and enables pipelining, includes authentication headers by default.
     */
    private val streamClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
        install (HttpTimeout) {
            connectTimeoutMillis = 30_000 // 30 seconds
            socketTimeoutMillis = 30_000_000_000
            requestTimeoutMillis = 30_000_000_000
        }
        // Default headers for streaming requests, including authentication
        headers {
            append("Connection", "keep-alive") // Important for streaming
            appendAll(buildHeaders()) // Add standard auth headers
        }
        engine {
            // Pipelining can be beneficial for scenarios where multiple requests are sent
            // without waiting for each response, though its effectiveness depends on the server.
            this.pipelining = true
        }
    }

    /**
     * Builds the necessary HTTP headers for authenticating requests with the Onyx API.
     *
     * @param extraHeaders Additional headers to include in the request.
     * @return A Ktor [Headers] object containing default and extra headers.
     */
    private fun buildHeaders(extraHeaders: Map<String, String> = emptyMap()): Headers {
        val defaultHeaders = mapOf(
            "x-onyx-key" to apiKey,
            "x-onyx-secret" to apiSecret,
            "Content-Type" to "application/json"
        )
        // Merge default headers with any provided extra headers
        return Headers.build {
            (defaultHeaders + extraHeaders).forEach { (key, value) ->
                append(key, value)
            }
        }
    }

    /**
     * Constructs a query string from a map of options.
     * Keys with null values are ignored. List values for the 'fetch' key are joined by commas.
     *
     * @param options A map of query parameter names to their values.
     * @return A URL-encoded query string (e.g., "?key1=value1&key2=value2") or an empty string if no options are provided.
     */
    private fun buildQueryString(options: Map<String, Any?>): String {
        val params = buildList {
            options.forEach { (key, value) ->
                when {
                    value == null -> {} // Skip null values
                    key == "fetch" && value is List<*> -> { // Special handling for 'fetch' parameter
                        val fetchList = value.filterNotNull().joinToString(",")
                        if (fetchList.isNotEmpty()) {
                            add("$key=${encode(fetchList)}")
                        }
                    }

                    else -> add("$key=${encode(value.toString())}")
                }
            }
        }.joinToString("&")

        return if (params.isNotEmpty()) "?$params" else ""
    }


    /**
     * Internal helper function to execute HTTP requests to the Onyx API.
     * Handles request setup, execution, response validation, and error handling.
     *
     * @param method The HTTP method ([HttpMethod]) to use (e.g., GET, PUT, POST, DELETE).
     * @param path The API endpoint path (e.g., "/data/{databaseId}/document").
     * @param body Optional request body data. If not a String, it will be serialized to JSON.
     * @param extraHeaders Optional additional headers for the request.
     * @param queryString Optional pre-built query string to append to the URL.
     * @return The response body as a String.
     * @throws NotFoundException if the API returns a 404 Not Found status.
     * @throws RuntimeException for other non-successful HTTP status codes.
     */
    private fun makeRequest(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        queryString: String = ""
    ): String {
        val url = "$baseUrl$path$queryString"

        return runBlocking {
            val statement: HttpStatement = httpClient.prepareRequest {
                url(url)
                this.method = method
                headers { appendAll(buildHeaders(extraHeaders)) }
                if (body != null) {
                    val payload = if (body is String) body else body.toJson()
                    setBody(payload)
                }
            }

            val response: HttpResponse = statement.execute()
            val responseBody = response.bodyAsText()

            if (!response.status.isSuccess()) {
                val msg = "HTTP ${response.status.value} @ $url → $responseBody"
                throw when (response.status) {
                    HttpStatusCode.NotFound ->
                        NotFoundException(msg, RuntimeException("HTTP ${response.status.value}"))
                    else ->
                        RuntimeException(msg)
                }
            }

            responseBody
        }
    }

    // --------------------------------------------------------------------------------------------
    // Document Endpoints
    // --------------------------------------------------------------------------------------------

    /**
     * Creates or updates a document in the Onyx database.
     *
     * @param document The [Document] object to save.
     * @return The saved [Document] object, potentially updated by the server (e.g., with timestamps).
     */
    fun saveDocument(document: Document): Document {
        val path = "/data/${encode(databaseId)}/document"
        return makeRequest(HttpMethod.Put, path, document).fromJson<Document>()
            ?: throw IllegalStateException("Failed to parse response for saveDocument")
    }

    /**
     * Retrieves a document by its ID.
     *
     * @param documentId The unique ID of the document to retrieve.
     * @param options Optional parameters, such as 'width' and 'height' for image documents.
     * @return The document content as a String (often JSON or binary data depending on the mime type).
     * @throws NotFoundException if the document with the specified ID does not exist.
     */
    fun getDocument(documentId: String, options: Map<String, Any?> = emptyMap()): String {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        return makeRequest(HttpMethod.Get, path, queryString = queryString)
    }

    /**
     * Deletes a document by its ID.
     *
     * @param documentId The unique ID of the document to delete.
     * @return `true` if the deletion was successful, `false` otherwise (though typically throws on failure).
     * @throws NotFoundException if the document with the specified ID does not exist.
     */
    fun deleteDocument(documentId: String): Boolean {
        val path = "/data/${encode(databaseId)}/document/${encode(documentId)}"
        // API likely returns a confirmation or status, adapt parsing if needed.
        // Assuming a simple boolean "true" or similar for success based on original code.
        return makeRequest(HttpMethod.Delete, path).equals("true", ignoreCase = true)
    }

    // --------------------------------------------------------------------------------------------
    // Data Endpoints
    // --------------------------------------------------------------------------------------------

    /**
     * Saves (creates or updates) one or more entities in a specified table.
     * If `entityOrEntities` is a single object, it saves that object.
     * If it's a list, it attempts to save all entities in the list in a single batch request.
     *
     * @param T The type of the entity/entities being saved.
     * @param table The [KClass] representing the table (entity type).
     * @param entityOrEntities A single entity object or a list of entity objects to save.
     * @return If saving a single entity, returns the saved entity (potentially updated by the server).
     * If saving a list, returns the original list.
     */
    fun <T : Any> save(table: KClass<*>, entityOrEntities: T): T {
        val path = "/data/${encode(databaseId)}/${encode(table)}"
        return if (entityOrEntities is List<*>) {
            // Batch save: Send the list directly
            makeRequest(HttpMethod.Put, path, entityOrEntities)
            // Return the original list as the API might not return the full list back
            entityOrEntities
        } else {
            // Single entity save: Send the object and parse the response
            makeRequest(HttpMethod.Put, path, entityOrEntities).fromJson(table)
                ?: throw IllegalStateException("Failed to parse response for save single entity")
        }
    }

    /**
     * Saves (creates or updates) a single entity. Infers the table name from the entity's class.
     *
     * @param T The type of the entity being saved.
     * @param entity The entity object to save.
     * @return The saved entity, potentially updated by the server.
     */
    inline fun <reified T : Any> save(entity: T): T = this.save(T::class, entity)

    /**
     * Saves a list of entities in batches. The table name is inferred from the list's generic type.
     * This method chunks the input list into smaller batches (e.g., 1000 entities per request)
     * and calls the `save` method for each chunk.
     *
     * @param T The type of entities in the list.
     * @param entities The list of entities to save.
     * @param batchSize The number of entities to include in each batch request. Defaults to 1000.
     */
    inline fun <reified T : Any> batchSave(entities: List<T>, batchSize: Int = 1000) {
        entities.chunked(batchSize).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                this.save(T::class, chunk) // Use the batch save capability of the main save method
            }
        }
    }

    /**
     * Retrieves a single entity by its primary key from a specified table.
     *
     * @param T The expected type of the entity.
     * @param type The [KClass] representing the table (entity type).
     * @param primaryKey The primary key value of the entity to retrieve.
     * @param options Optional parameters, such as 'partition' to specify a partition key or 'fetch'
     * to load related entities (provide as a list of relationship names).
     * @return The found entity of type [T], or `null` if no entity matches the primary key.
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
        } catch (e: NotFoundException) {
            // Return null if the API explicitly returns 404 Not Found
            null
        }
    }

    /**
     * Retrieves a single entity by its primary key. Infers the table name from the generic type [T].
     *
     * @param T The expected type of the entity.
     * @param id The primary key value of the entity to retrieve.
     * @return The found entity of type [T], or `null` if not found.
     */
    inline fun <reified T : Any> findById(id: Any): T? = findById(T::class, id)

    /**
     * Retrieves an entity by primary key within a specific partition.
     *
     * @param T The expected type of the entity.
     * @param table The [KClass] representing the table (entity type).
     * @param primaryKey The primary key value.
     * @param partition The partition key value.
     * @return The found entity of type [T], or `null` if not found.
     */
    fun <T : Any> findByIdInPartition(table: KClass<*>, primaryKey: String, partition: String): T? {
        return findById(table, primaryKey, mapOf("partition" to partition))
    }

    /**
     * Retrieves an entity by primary key within a specific partition. Infers table name from [T].
     *
     * @param T The expected type of the entity.
     * @param primaryKey The primary key value.
     * @param partition The partition key value.
     * @return The found entity of type [T], or `null` if not found.
     */
    inline fun <reified T : Any> findByIdInPartition(primaryKey: String, partition: String): T? {
        return findByIdInPartition(T::class, primaryKey, partition)
    }

    /**
     * Retrieves an entity by primary key, fetching specified related entities.
     *
     * @param T The expected type of the entity.
     * @param table The [KClass] representing the table (entity type).
     * @param primaryKey The primary key value.
     * @param fetchRelationships A list of relationship names to fetch eagerly.
     * @return The found entity of type [T] with specified relationships loaded, or `null` if not found.
     */
    fun <T : Any> findByIdWithFetch(table: KClass<*>, primaryKey: String, fetchRelationships: List<String>): T? {
        return findById(table, primaryKey, mapOf("fetch" to fetchRelationships))
    }

    /**
     * Retrieves an entity by primary key, fetching specified related entities. Infers table name from [T].
     *
     * @param T The expected type of the entity.
     * @param primaryKey The primary key value.
     * @param fetchRelationships A list of relationship names to fetch eagerly.
     * @return The found entity of type [T] with specified relationships loaded, or `null` if not found.
     */
    inline fun <reified T : Any> findByIdWithFetch(primaryKey: String, fetchRelationships: List<String>): T? {
        return findByIdWithFetch(T::class, primaryKey, fetchRelationships)
    }

    /**
     * Retrieves an entity by primary key within a specific partition, fetching specified related entities.
     *
     * @param T The expected type of the entity.
     * @param table The [KClass] representing the table (entity type).
     * @param primaryKey The primary key value.
     * @param partition The partition key value.
     * @param fetchRelationships A list of relationship names to fetch eagerly.
     * @return The found entity of type [T] in the partition with relationships loaded, or `null` if not found.
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
     * Deletes an entity by its primary key from the specified table.
     *
     * @param table The name of the table from which to delete the entity.
     * @param primaryKey The primary key value of the entity fto delete.
     * @param options Optional parameters, such as 'partition' to specify a partition key.
     * @return `true` if the deletion was successful, `false` otherwise.
     * @throws NotFoundException if the entity with the specified ID does not exist.
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
     * Deletes an entity by primary key within a specific partition.
     *
     * @param table The name of the table.
     * @param primaryKey The primary key value.
     * @param partition The partition key value.
     * @return `true` if the deletion was successful, `false` otherwise.
     */
    fun deleteInPartition(table: String, primaryKey: String, partition: String): Boolean {
        return delete(table, primaryKey, mapOf("partition" to partition))
    }

    /**
     * Executes a select query against a specified table.
     * The query definition (`selectQuery`) should be an object representing the query structure
     * (e.g., a map or a custom data class serializable to the expected JSON format).
     *
     * @param table The name of the table to query.
     * @param selectQuery An object representing the select query criteria, fields, sorting, etc.
     * @param options Optional parameters like 'pageSize', 'nextPage' for pagination, or 'partition'.
     * @return The raw JSON string response containing the query results. Parsing is left to the caller.
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
     * Executes a query to count the number of entities matching the criteria in a specified table.
     *
     * @param table The name of the table to query.
     * @param selectQuery An object representing the query criteria (conditions).
     * @param options Optional parameters, primarily 'partition'. Paging options are usually ignored for count queries.
     * @return The total number of matching entities as an [Int]. Returns 0 if the response cannot be parsed to an Int.
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
     * Executes a select query with explicit pagination parameters.
     *
     * @param table The name of the table.
     * @param selectQuery The query object.
     * @param pageSize The maximum number of results per page.
     * @param nextPage A token indicating the next page to retrieve (obtained from a previous response).
     * @return The raw JSON string response for the requested page.
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
     * Executes a select query within a specific partition.
     *
     * @param table The name of the table.
     * @param selectQuery The query object.
     * @param partition The partition key value.
     * @return The raw JSON string response containing results from the specified partition.
     */
    fun executeQueryInPartition(
        table: String,
        selectQuery: Any,
        partition: String
    ): String {
        return executeQuery(table, selectQuery, mapOf("partition" to partition))
    }

    /**
     * Executes a select query within a specific partition with pagination.
     *
     * @param table The name of the table.
     * @param selectQuery The query object.
     * @param partition The partition key value.
     * @param pageSize The maximum number of results per page.
     * @param nextPage The token for the next page.
     * @return The raw JSON string response for the requested page within the partition.
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
     * Executes an update query against entities matching the criteria in a specified table.
     *
     * @param table The name of the table to update entities in.
     * @param updateQuery An object representing the update operation, including conditions and fields to update.
     * @param options Optional parameters, primarily 'partition'.
     * @return The number of entities updated as an [Int].
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
     * Executes an update query within a specific partition.
     *
     * @param table The name of the table.
     * @param updateQuery The update query object.
     * @param partition The partition key value.
     * @return The number of entities updated within the partition.
     */
    fun executeUpdateQueryInPartition(
        table: String,
        updateQuery: Any,
        partition: String
    ): Int {
        return executeUpdateQuery(table, updateQuery, mapOf("partition" to partition))
    }

    /**
     * Executes a delete query against entities matching the criteria in a specified table.
     *
     * @param table The name of the table from which to delete entities.
     * @param selectQuery An object representing the criteria (conditions) for selecting entities to delete.
     * @param options Optional parameters, primarily 'partition'.
     * @return The number of entities deleted as an [Int].
     */
    fun executeDeleteQuery(
        table: String,
        selectQuery: Any, // Usually contains just the 'conditions' part
        options: Map<String, Any?> = emptyMap()
    ): Int {
        val queryString = buildQueryString(options)
        val path = "/data/${encode(databaseId)}/query/delete/${encode(table)}"
        return makeRequest(HttpMethod.Put, path, selectQuery, queryString = queryString).toIntOrNull() ?: 0
    }

    /**
     * Executes a delete query within a specific partition.
     *
     * @param table The name of the table.
     * @param selectQuery The criteria for deletion.
     * @param partition The partition key value.
     * @return The number of entities deleted within the partition.
     */
    fun executeDeleteQueryInPartition(
        table: String,
        selectQuery: Any,
        partition: String
    ): Int {
        return executeDeleteQuery(table, selectQuery, mapOf("partition" to partition))
    }

    /**
     * Streams query results as a Kotlin [Flow] of JSON strings (JSON Lines format).
     * This allows processing large result sets without loading everything into memory.
     *
     * @param table The name of the table to query.
     * @param selectQuery The query object defining the criteria, fields, etc.
     * @param includeQueryResults If `true`, the initial matching results are included in the stream. Defaults to `true`.
     * @param keepAlive If `true`, attempts to keep the connection open for real-time updates (server support required). Defaults to `false`.
     * @return A [Flow] emitting each JSON line (representing an entity or event) as a String.
     */
    fun stream(
        table: String,
        selectQuery: Any,
        includeQueryResults: Boolean = true,
        keepAlive: Boolean = false,
    ): Flow<String> {
        // Build the request details
        val params = "?includeQueryResults=$includeQueryResults&keepAlive=$keepAlive"

        // Ensure proper URL encoding for path segments
        val encodedDbId = encode(databaseId) // Replace with your actual encoding if needed
        val encodedTable = encode(table)     // Replace with your actual encoding if needed
        val path = "/data/$encodedDbId/query/stream/$encodedTable$params"
        val url = "$baseUrl$path" // Ensure baseUrl doesn't have a trailing slash if path starts with one

        // The flow builder constructs the Flow asynchronously
        return flow {
            // Prepare and execute the streaming request within the flow builder
            streamClient.prepareRequest(url) {
                method = HttpMethod.Put
                headers {
                    appendAll(buildHeaders())
                    // Ensure Content-Type is set if the server expects it
                    contentType(ContentType.Application.Json)
                }
                setBody(selectQuery.toJson())
            }.execute { response -> // `execute` manages the response lifecycle
                // Check for HTTP errors first
                if (!response.status.isSuccess()) {
                    // Consider reading the error body for more details
                    val errorBody = try {
                        response.bodyAsText()
                    } catch (e: Exception) {
                        "(Could not read error body: ${e.message})"
                    }
                    throw RuntimeException("HTTP Error: ${response.status.value} ${response.status.description}. Body: $errorBody")
                }

                // Get the response body as a channel for reading bytes
                val channel: ByteReadChannel = response.bodyAsChannel()

                // Read lines efficiently using Ktor's built-in function
                try {
                    while (true) {
                        // readUTF8Line reads until \n or \r\n, handles buffering internally.
                        // Returns null when the channel is closed (end of stream).
                        val line = channel.readUTF8Line() ?: break // Exit loop at end of stream
                        emit(line) // Emit the complete line downstream
                    }
                } catch (e: Exception) {
                    // Handle potential exceptions during reading (e.g., connection closed unexpectedly)
                    // Log the error or rethrow a more specific exception
                    println("Error reading stream: ${e.message}") // Replace with proper logging
                    throw e // Rethrow or handle as appropriate for your application
                }
                // No need for explicit finally/close on channel here,
                // Ktor's `execute` block handles consuming/closing the response body
                // when this lambda finishes (normally or exceptionally).
            }
        }
    }

    /**
     * Convenience function to stream query results *without* including the initial query results,
     * often used for listening only to real-time changes (create, update, delete events).
     * Requires `keepAlive=true` to be effective for continuous listening.
     *
     * @param table The name of the table.
     * @param selectQuery The query object defining the filter criteria.
     * @param keepAlive Whether to keep the connection open for continuous updates. Defaults to `true` for this use case.
     * @return A [Flow] emitting JSON lines for events (CREATE, UPDATE, DELETE).
     */
    fun streamEventsOnly(table: String, selectQuery: Any, keepAlive: Boolean = true): Flow<String> {
        return stream(table, selectQuery, includeQueryResults = false, keepAlive = keepAlive)
    }

    /**
     * Convenience function to stream query results *including* the initial matching entities,
     * followed by any real-time changes if `keepAlive` is enabled.
     *
     * @param table The name of the table.
     * @param selectQuery The query object.
     * @param keepAlive Whether to keep the connection open for continuous updates after initial results. Defaults to `false`.
     * @return A [Flow] emitting JSON lines for initial results and subsequent events.
     */
    fun streamWithQueryResults(table: String, selectQuery: Any, keepAlive: Boolean = false): Flow<String> {
        return stream(table, selectQuery, includeQueryResults = true, keepAlive = keepAlive)
    }


    /**
     * Creates a [QueryBuilder] instance bound to a specific table name.
     * This is the entry point for building fluent queries.
     *
     * @param tableName The name of the table to query.
     * @return A [QueryBuilder] instance for the specified table.
     */
    fun from(tableName: String): QueryBuilder {
        return QueryBuilder(this, tableName)
    }

    /**
     * Creates a [QueryBuilder] instance bound to a table name inferred from the generic type [T].
     *
     * @param T The entity type, used to determine the table name.
     * @return A [QueryBuilder] instance for the inferred table.
     */
    inline fun <reified T> from(): QueryBuilder = this.from(T::class.java.simpleName)

    /**
     * Creates a [QueryBuilder] instance starting with specific fields to select.
     * The table must be specified later using the [QueryBuilder.from] method.
     *
     * @param fields The names of the fields to select.
     * @return A [QueryBuilder] instance with pre-selected fields, awaiting a table specification.
     */
    fun select(vararg fields: String): QueryBuilder {
        // Start with null table, requires calling .from() later
        val qb = QueryBuilder(this, null)
        // Use the QueryBuilder's select method
        qb.select(*fields)
        return qb
    }

    /**
     * URL-encodes a string value using UTF-8 encoding.
     *
     * @param value The string to encode.
     * @return The URL-encoded string.
     */
    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    /**
     * URL-encodes the simple name of a Kotlin class.
     *
     * @param value The [KClass] whose simple name should be encoded.
     * @return The URL-encoded simple name of the class.
     */
    fun encode(value: KClass<*>): String = encode(value.java.simpleName)
}

//region Condition Builder DSL

/**
 * Builds query conditions using a fluent API. Allows combining criteria with AND/OR operators.
 * Use the associated infix helper functions (e.g., `eq`, `neq`, `gt`) to create initial instances.
 *
 * @param criteria The initial [QueryCriteria] for this builder, if any.
 */
class ConditionBuilder internal constructor(criteria: QueryCriteria? = null) {
    private var condition: QueryCondition? = criteria?.let { QueryCondition.SingleCondition(it) }

    /**
     * Combines the current condition with another condition using the AND operator.
     *
     * @param other The [ConditionBuilder] containing the condition to combine with.
     * @return This [ConditionBuilder] instance for chaining.
     */
    fun and(other: ConditionBuilder): ConditionBuilder {
        combine(LogicalOperator.AND, other.toCondition())
        return this
    }

    /**
     * Combines the current condition with another condition using the OR operator.
     *
     * @param other The [ConditionBuilder] containing the condition to combine with.
     * @return This [ConditionBuilder] instance for chaining.
     */
    fun or(other: ConditionBuilder): ConditionBuilder {
        combine(LogicalOperator.OR, other.toCondition())
        return this
    }

    /**
     * Internal helper to combine the existing condition with a new one.
     */
    private fun combine(operator: LogicalOperator, newCondition: QueryCondition?) {
        val currentCondition = condition // Capture current state

        if (newCondition == null) return // Nothing to combine

        condition = when (currentCondition) {
            null -> newCondition // First condition
            // If current is single or compound, create/update a compound condition
            is QueryCondition.SingleCondition, is QueryCondition.CompoundCondition -> {
                // If current is already a compound condition with the *same* operator, append to it
                if (currentCondition is QueryCondition.CompoundCondition && currentCondition.operator == operator) {
                    currentCondition.copy(conditions = currentCondition.conditions + newCondition)
                } else {
                    // Otherwise, create a new compound condition grouping the current and new ones
                    QueryCondition.CompoundCondition(
                        operator = operator,
                        conditions = listOfNotNull(currentCondition, newCondition)
                    )
                }
            }
        }
    }

    /**
     * Converts the internal state of the builder into a [QueryCondition] object
     * suitable for use in a [QueryBuilder].
     *
     * @return The resulting [QueryCondition], or `null` if no conditions were added.
     */
    internal fun toCondition(): QueryCondition? {
        return condition
    }
}

/** Creates an "equals" condition. */
infix fun String.eq(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.EQUAL, value))

/** Creates a "not equals" condition. */
infix fun String.neq(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value))

/** Creates an "in" condition (field value must be one of the specified values). */
infix fun String.inOp(values: List<Any?>): ConditionBuilder =
    ConditionBuilder(
        QueryCriteria(
            this,
            QueryCriteriaOperator.IN,
            values.joinToString(",") { it.toString() })
    ) // Send list directly

/** Creates a "not in" condition (field value must not be one of the specified values). */
infix fun String.notIn(values: List<Any?>): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values)) // Send list directly

/** Creates a "greater than" condition. */
infix fun String.gt(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value))

/** Creates a "greater than or equal to" condition. */
infix fun String.gte(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value))

/** Creates a "less than" condition. */
infix fun String.lt(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value))

/** Creates a "less than or equal to" condition. */
infix fun String.lte(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value))

/** Creates a "matches regex" condition. */
infix fun String.matches(regex: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.MATCHES, regex))

/** Creates a "does not match regex" condition. */
infix fun String.notMatches(regex: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, regex))

/** Creates a "between" condition (inclusive). */
fun String.between(lower: Any?, upper: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(lower, upper)))

/** Creates a "like" condition (SQL-style pattern matching). */
infix fun String.like(pattern: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.LIKE, pattern))

/** Creates a "not like" condition (SQL-style pattern matching). */
infix fun String.notLike(pattern: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, pattern))

/** Creates a "contains" condition (e.g., for checking if an array/list contains a value, case-sensitive). */
infix fun String.contains(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value))

/** Creates a "contains ignore case" condition (e.g., for checking substrings in text, case-insensitive). */
infix fun String.containsIgnoreCase(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value))

/** Creates a "does not contain ignore case" condition. */
infix fun String.notContainsIgnoreCase(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value))

/** Creates a "does not contain" condition (case-sensitive). */
infix fun String.notContains(value: Any?): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value))

/** Creates a "starts with" condition (case-sensitive). */
infix fun String.startsWith(prefix: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, prefix))

/** Creates a "does not start with" condition (case-sensitive). */
infix fun String.notStartsWith(prefix: String): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, prefix))

/** Creates an "is null" condition. */
fun String.isNull(): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.IS_NULL))

/** Creates a "is not null" condition. */
fun String.notNull(): ConditionBuilder =
    ConditionBuilder(QueryCriteria(this, QueryCriteriaOperator.NOT_NULL))

//endregion Condition Builder DSL

//region Query Structure Data Classes

/**
 * Enumeration of operators available for defining query criteria.
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
 * Logical operators used to combine multiple query conditions.
 */
enum class LogicalOperator {
    AND, OR
}

/**
 * Represents a query condition, which can be either a single criterion
 * or a combination of multiple conditions using a logical operator (AND/OR).
 * This is used internally by [ConditionBuilder] and [QueryBuilder].
 */
sealed class QueryCondition {
    /** Represents a single query criterion (e.g., field = value). */
    data class SingleCondition(
        val criteria: QueryCriteria,
        // Field included for potential serialization distinction, matches JS structure hint
        val conditionType: String = "SingleCondition"
    ) : QueryCondition()

    /** Represents multiple conditions combined with a logical operator. */
    data class CompoundCondition(
        val operator: LogicalOperator,
        val conditions: List<QueryCondition>,
        // Field included for potential serialization distinction
        val conditionType: String = "CompoundCondition"
    ) : QueryCondition()
}

/**
 * Represents a single piece of criteria within a query condition.
 *
 * @property field The name of the entity field to apply the criterion to.
 * @property operator The [QueryCriteriaOperator] defining the comparison logic.
 * @property value The value to compare against. Can be null for operators like IS_NULL/NOT_NULL,
 * a single value, or a list for operators like IN, NOT_IN, BETWEEN.
 */
data class QueryCriteria(
    val field: String,
    val operator: QueryCriteriaOperator,
    val value: Any? = null
)

/**
 * Helper function to manually create a [QueryCondition.CompoundCondition].
 * Primarily used internally by [QueryBuilder] when chaining `and`/`or` calls.
 *
 * @param operator The logical operator ("AND" or "OR", case-insensitive).
 * @param conditions A list of conditions to combine. Can contain [QueryCondition] objects or nulls (which are ignored).
 * @return A [QueryCondition.CompoundCondition] representing the combined conditions.
 */
internal fun buildCompoundCondition(operator: String, conditions: List<Any?>): QueryCondition.CompoundCondition {
    val logicalOp = LogicalOperator.valueOf(operator.uppercase())

    // Filter out nulls and ensure all elements are valid QueryCondition objects
    val parsedConds = conditions.mapNotNull { cond ->
        when (cond) {
            is QueryCondition -> cond
            is ConditionBuilder -> cond.toCondition() // Allow ConditionBuilder instances too
            else -> null // Ignore other types or nulls
        }
    }

    return QueryCondition.CompoundCondition(
        operator = logicalOp,
        conditions = parsedConds
    )
}

/**
 * Represents an Onyx Document object.
 *
 * @property documentId The unique identifier for the document. Usually assigned by Onyx on creation if left empty.
 * @property path An optional path or classification for the document.
 * @property created The date the document was created. Handled by the server.
 * @property updated The date the document was last updated. Handled by the server.
 * @property mimeType The MIME type of the document content (e.g., "application/json", "image/png").
 * @property content The actual content of the document, often Base64 encoded for binary data or a JSON string.
 */
data class Document(
    val documentId: String = "",
    val path: String = "",
    val created: Date = Date(), // Should ideally be nullable or use server-provided value
    val updated: Date = Date(), // Should ideally be nullable or use server-provided value
    val mimeType: String = "",
    val content: String = "" // Could be Base64 encoded string for binary data
)

/**
 * Example data class representing a Contact entity.
 * Use this structure or define your own based on your Onyx schema.
 *
 * @property id The primary key for the Contact.
 * @property contactType A categorization of the contact.
 * @property email The contact's email address.
 * @property name The contact's name.
 * @property message The message content associated with the contact.
 * @property billingIssue Flag indicating a billing-related issue.
 * @property salesInquiry Flag indicating a sales inquiry.
 * @property subjects Comma-separated list or JSON array string of subjects.
 * @property timestamp Timestamp of the contact event, often an ISO 8601 string.
 */
data class Contact(
    val id: Int = 0, // Assuming Int ID, adjust if String, Long, etc.
    val contactType: String = "",
    val email: String = "",
    val name: String = "",
    val message: String = "",
    val billingIssue: Boolean? = null,
    val salesInquiry: Boolean? = null,
    val subjects: String = "", // Consider List<String> if parsed
    val timestamp: String = "" // Consider Instant or ZonedDateTime
)

//endregion Query Structure Data Classes

//region Aggregate and Function Helpers

/** Creates an average aggregation function string. Example: `avg("age")` */
fun avg(attribute: String): String = "avg($attribute)"

/** Creates a sum aggregation function string. Example: `sum("price")` */
fun sum(attribute: String): String = "sum($attribute)"

/** Creates a count aggregation function string. Example: `count("id")` or `count("*")` */
fun count(attribute: String): String = "count($attribute)"

/** Creates a minimum aggregation function string. Example: `min("score")` */
fun min(attribute: String): String = "min($attribute)"

/** Creates a maximum aggregation function string. Example: `max("score")` */
fun max(attribute: String): String = "max($attribute)"

/** Creates a median aggregation function string. Example: `median("value")` */
fun median(attribute: String): String = "median($attribute)"

/** Creates a standard deviation aggregation function string. Example: `std("value")` */
fun std(attribute: String): String = "std($attribute)"

/** Creates an uppercase function string. Example: `upper("name")` */
fun upper(attribute: String): String = "upper($attribute)"

/** Creates a lowercase function string. Example: `lower("name")` */
fun lower(attribute: String): String = "lower($attribute)"

/** Creates a substring function string. Example: `substring("description", 0, 10)` */
fun substring(attribute: String, from: Int, length: Int): String = "substring($attribute,$from,$length)"

/** Creates a replace function string. Example: `replace("text", " ", "_")` */
fun replace(attribute: String, pattern: String, repl: String): String =
    "replace($attribute, '${pattern.replace("'", "''")}', '${repl.replace("'", "''")}')" // Basic SQL-like escaping

//endregion Aggregate and Function Helpers

//region Query Builder

/**
 * Provides a fluent API for constructing and executing queries (select, update, delete)
 * and streaming results from the Onyx API.
 *
 * @property client The [OnyxClient] instance used to execute the built queries.
 * @property table The name of the target table for the query. Can be set initially or via the `from()` method.
 */
class QueryBuilder internal constructor(
    private val client: OnyxClient,
    private var table: String?
) {
    // Query components
    private var fields: List<String>? = null
    private var conditions: QueryCondition? = null
    private var sort: List<SortOrder>? = null
    private var limitValue: Int? = null
    private var distinctValue: Boolean = false
    private var groupByValues: List<String>? = null
    private var partitionValue: String? = null
    private var resolvers: List<String> = emptyList()

    // Update specific components
    private var updates: Map<String, Any?>? = null // Use Any? to allow setting nulls

    // Query mode
    private enum class Mode { SELECT, UPDATE, DELETE }

    private var mode: Mode = Mode.SELECT

    // Paging components
    private var pageSizeValue: Int? = null
    private var nextPageValue: String? = null

    // Streaming callback components
    private var onItemAddedListener: ((entity: Any) -> Unit)? = null
    private var onItemDeletedListener: ((entity: Any) -> Unit)? = null
    private var onItemUpdatedListener: ((entity: Any) -> Unit)? = null
    private var onItemListener: ((entity: Any) -> Unit)? = null // Generic listener for query results in stream

    /**
     * Sets the target table for this query.
     *
     * @param table The name of the table.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun from(table: String): QueryBuilder {
        this.table = table
        return this
    }

    /**
     * Sets the target table for this query, inferring the name from the generic type [T].
     *
     * @param T The entity type whose class name will be used as the table name.
     * @return This [QueryBuilder] instance for chaining.
     */
    inline fun <reified T> from(): QueryBuilder = this.from(T::class.java.simpleName)

    /**
     * Specifies the fields to retrieve in a select query. Replaces any previously selected fields.
     * Can include simple field names or function calls (e.g., `count("*")`, `avg("price")`).
     *
     * @param fields Vararg list of field names or function strings to select.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun select(vararg fields: String): QueryBuilder {
        this.fields = fields.toList().ifEmpty { null } // Store as null if empty
        this.mode = Mode.SELECT // Ensure mode is select
        return this
    }

    /**
     * Specifies the fields to retrieve in a select query. Replaces any previously selected fields.
     *
     * @param fields List of field names or function strings to select.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun selectFields(fields: List<String>): QueryBuilder {
        this.fields = fields.ifEmpty { null } // Store as null if empty
        this.mode = Mode.SELECT // Ensure mode is select
        return this
    }

    /**
     * Sets the primary condition for the query using a [ConditionBuilder].
     * Replaces any existing conditions.
     *
     * @param condition The [ConditionBuilder] defining the WHERE clause.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun where(condition: ConditionBuilder): QueryBuilder {
        this.conditions = condition.toCondition()
        return this
    }

    /**
     * Adds an additional condition combined with the existing conditions using AND.
     * If no conditions exist yet, this behaves like `where()`.
     *
     * @param condition The [ConditionBuilder] defining the condition to add.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun and(condition: ConditionBuilder): QueryBuilder {
        addCondition(condition, LogicalOperator.AND)
        return this
    }

    /**
     * Adds an additional condition combined with the existing conditions using OR.
     * If no conditions exist yet, this behaves like `where()`.
     *
     * @param condition The [ConditionBuilder] defining the condition to add.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun or(condition: ConditionBuilder): QueryBuilder {
        addCondition(condition, LogicalOperator.OR)
        return this
    }

    /**
     * Internal helper to add or combine conditions.
     */
    private fun addCondition(builderToAdd: ConditionBuilder, logicalOperator: LogicalOperator) {
        val conditionToAdd = builderToAdd.toCondition() ?: return // Ignore empty conditions

        val currentCondition = this.conditions
        if (currentCondition == null) {
            // If no existing condition, just set the new one
            this.conditions = conditionToAdd
        } else {
            // Combine existing and new conditions
            // If current is already a compound condition with the same operator, append to it
            if (currentCondition is QueryCondition.CompoundCondition && currentCondition.operator == logicalOperator) {
                this.conditions = currentCondition.copy(conditions = currentCondition.conditions + conditionToAdd)
            } else {
                // Otherwise, create a new compound condition grouping the current and new ones
                this.conditions = QueryCondition.CompoundCondition(
                    operator = logicalOperator,
                    conditions = listOf(currentCondition, conditionToAdd)
                )
            }
        }
    }

    /**
     * Sets the maximum number of records to return.
     *
     * @param limit The maximum number of records.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun limit(limit: Int): QueryBuilder {
        limitValue = limit
        return this
    }

    /**
     * Specifies that the query should return only distinct results.
     *
     * @return This [QueryBuilder] instance for chaining.
     */
    fun distinct(): QueryBuilder {
        distinctValue = true
        return this
    }

    /**
     * Specifies fields to group the results by (for aggregate queries).
     *
     * @param fields Vararg list of field names to group by.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun groupBy(vararg fields: String): QueryBuilder {
        groupByValues = fields.toList().ifEmpty { null }
        return this
    }

    /**
     * Specifies the sorting order for the results.
     * Use `asc("fieldName")` or `desc("fieldName")` helpers to create [SortOrder] objects.
     *
     * @param orders Vararg list of [SortOrder] objects defining the sorting.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun orderBy(vararg orders: SortOrder): QueryBuilder {
        sort = orders.toList().ifEmpty { null }
        return this
    }

    /**
     * Specifies the partition key value for the query.
     * Limits the query scope to a specific partition.
     *
     * @param partition The partition key value.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun inPartition(partition: String): QueryBuilder {
        partitionValue = partition
        return this
    }

    /**
     * Specifies related entities to resolve (fetch) along with the main query results.
     *
     * @param resolvers Vararg list of relationship names to resolve.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun resolve(vararg resolvers: String): QueryBuilder {
        this.resolvers = resolvers.toList()
        return this
    }

    /**
     * Specifies the field updates for an update query.
     * Calling this method switches the builder to "update" mode.
     *
     * @param updates Vararg list of [Pair] objects where the first element is the field name (String)
     * and the second is the value to set. Use `null` to set a field to null.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun setUpdates(vararg updates: Pair<String, Any?>): QueryBuilder {
        this.mode = Mode.UPDATE
        this.updates = updates.toMap()
        return this
    }

    /**
     * Sets the page size for paginated queries.
     * Used in conjunction with `list()` execution.
     *
     * @param size The number of records per page.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun pageSize(size: Int): QueryBuilder {
        pageSizeValue = size
        return this
    }

    /**
     * Sets the token for retrieving the next page of results.
     * Used in conjunction with `list()` execution. The token is typically obtained
     * from the `nextPage` property of a previous [QueryResults] response.
     *
     * @param token The next page token.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun nextPage(token: String): QueryBuilder {
        nextPageValue = token
        return this
    }

    // --- Private Helper Functions for Query Execution ---

    /**
     * Builds the common options map used for client requests, including partition, pageSize, and nextPage.
     */
    private fun buildCommonOptions(): Map<String, Any?> {
        return mapOf(
            "partition" to partitionValue,
            "pageSize" to pageSizeValue,
            "nextPage" to nextPageValue
        ).filterValues { it != null } // Remove null values before sending
    }

    /**
     * Builds the payload Map for a Select query based on the current builder state.
     */
    private fun buildSelectQueryPayload(): Map<String, Any?> {
        return mapOf(
            "type" to "SelectQuery", // Standard type identifier for select queries
            "fields" to fields,
            "conditions" to conditions,
            "sort" to sort,
            "limit" to limitValue,
            "distinct" to distinctValue,
            "groupBy" to groupByValues,
            "resolvers" to resolvers.ifEmpty { null }, // Send null if empty list
            // Partition is often handled as a query param, but include if API expects it in body too
            "partition" to partitionValue
        ).filterValues { it != null } // Remove null values
    }

    /**
     * Builds the payload Map for an Update query based on the current builder state.
     */
    private fun buildUpdateQueryPayload(): Map<String, Any?> {
        return mapOf(
            "type" to "UpdateQuery", // Standard type identifier for update queries
            "conditions" to conditions,
            "updates" to updates,
            "partition" to partitionValue
        ).filterValues { it != null && !(it is List<*> && it.isEmpty()) } // Ensure updates is not empty map potentially
    }

    /**
     * Builds the payload Map for a Delete query (which is often a Select query structure).
     */
    private fun buildDeleteQueryPayload(): Map<String, Any?> {
        // Delete often uses the same structure as Select for conditions, but ignores fields/sort/etc.
        return mapOf(
            "type" to "SelectQuery", // API might reuse SelectQuery structure for delete conditions
            "conditions" to conditions,
            "partition" to partitionValue // Include partition if needed for delete scoping
            // Other select-specific fields are typically omitted or ignored by the delete endpoint
        ).filterValues { it != null }
    }


    // --- Execution Methods ---

    /**
     * Executes the built select query and returns the results deserialized into a list of the specified type [T].
     * Handles pagination automatically if `pageSize` was set.
     *
     * @param T The type to deserialize the result records into. Must be a data class suitable for Gson deserialization.
     * @return A [QueryResults] object containing the first page of results and pagination info.
     * @throws IllegalStateException if the query builder is not in select mode or if the table name is missing.
     */
    inline fun <reified T : Any> list(): QueryResults<T> = list(T::class)

    /**
     * Executes the built select query and returns the results deserialized into a list of the specified type.
     * Handles pagination automatically if `pageSize` was set.
     *
     * @param T The type to deserialize the result records into.
     * @param type The [KClass] of the type [T].
     * @return A [QueryResults] object containing the first page of results and pagination info.
     * @throws IllegalStateException if the query builder is not in select mode or if the table name is missing.
     */
    fun <T : Any> list(type: KClass<*>): QueryResults<T> {
        check(mode == Mode.SELECT) { "Cannot call list() when the builder is in ${mode.name} mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling list().")

        val queryPayload = buildSelectQueryPayload()
        val requestOptions = buildCommonOptions() // Includes pageSize, nextPage, partition

        val jsonResponse = client.executeQuery(targetTable, queryPayload, requestOptions)

        // Deserialize the top-level response which contains records and pagination info
        val results = (jsonResponse.fromJson(QueryResults::class) as? QueryResults<T>)
            ?: QueryResults() // Provide a default empty result if parsing fails

        // Store context needed for fetching subsequent pages
        results.query = this
        results.classType = type

        return results
    }


    /**
     * Executes a count query based on the current conditions.
     * Ignores select fields, sort order, limit, and pagination settings.
     *
     * @return The total number of records matching the conditions.
     * @throws IllegalStateException if the table name is missing.
     */
    fun count(): Int {
        // Count typically only needs conditions and partition
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling count().")

        // Build a payload suitable for count (usually just conditions)
        val countQueryPayload = mapOf(
            "type" to "SelectQuery", // API likely reuses SelectQuery for structure
            "conditions" to conditions,
            "partition" to partitionValue // Include partition if relevant for count
        ).filterValues { it != null }

        // Build options (mainly partition)
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }

        return client.executeCountForQuery(targetTable, countQueryPayload, requestOptions)
    }

    /**
     * Executes a delete operation based on the current conditions.
     * Deletes all records matching the specified `where`, `and`, `or`, and `inPartition` clauses.
     * Ignores select fields, sort, limit, etc.
     *
     * @return The number of records deleted.
     * @throws IllegalStateException if the table name is missing or if called in update mode.
     */
    fun delete(): Int {
        check(mode == Mode.SELECT || mode == Mode.DELETE) { "delete() can only be called after setting conditions (where/and/or/partition), not in update mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling delete().")
        // It's safer to explicitly set the mode, even though it might be implied
        this.mode = Mode.DELETE

        val queryPayload = buildDeleteQueryPayload()
        val requestOptions =
            mapOf("partition" to partitionValue).filterValues { it != null } // Only partition needed typically

        return client.executeDeleteQuery(targetTable, queryPayload, requestOptions)
    }

    /**
     * Executes an update operation based on the current conditions and specified updates.
     * Updates all records matching the `where`, `and`, `or`, and `inPartition` clauses using the values provided via `setUpdates()`.
     *
     * @return The number of records updated.
     * @throws IllegalStateException if `setUpdates()` was not called first, or if the table name is missing.
     */
    fun update(): Int {
        check(mode == Mode.UPDATE) { "Must call setUpdates(...) before calling update()." }
        check(updates != null) { "No updates specified. Call setUpdates(...) first." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling update().")

        val queryPayload = buildUpdateQueryPayload()
        val requestOptions = mapOf("partition" to partitionValue).filterValues { it != null }

        return client.executeUpdateQuery(targetTable, queryPayload, requestOptions)
    }

    // --- Streaming Methods ---

    /**
     * Registers a listener to be called when a new item matching the query criteria is created.
     * Used with the `stream()` method.
     *
     * @param T The type of the entity.
     * @param listener A lambda function that accepts the created entity of type [T].
     * @return This [QueryBuilder] instance for chaining.
     */
    fun <T : Any> onItemAdded(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemAddedListener = listener as (Any) -> Unit // Store type-erased listener
        return this
    }

    /**
     * Registers a listener to be called when an item matching the query criteria is deleted.
     * Used with the `stream()` method.
     *
     * @param T The type of the entity.
     * @param listener A lambda function that accepts the deleted entity of type [T].
     * @return This [QueryBuilder] instance for chaining.
     */
    fun <T : Any> onItemDeleted(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemDeletedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a listener to be called when an item matching the query criteria is updated.
     * Used with the `stream()` method.
     *
     * @param T The type of the entity.
     * @param listener A lambda function that accepts the updated entity of type [T].
     * @return This [QueryBuilder] instance for chaining.
     */
    fun <T : Any> onItemUpdated(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemUpdatedListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Registers a listener to be called for initial query results when streaming with `includeQueryResults=true`.
     * Used with the `stream()` method.
     *
     * @param T The type of the entity.
     * @param listener A lambda function that accepts an entity of type [T] from the initial result set.
     * @return This [QueryBuilder] instance for chaining.
     */
    fun <T : Any> onItem(listener: (entity: T) -> Unit): QueryBuilder {
        @Suppress("UNCHECKED_CAST")
        onItemListener = listener as (Any) -> Unit
        return this
    }

    /**
     * Initiates a streaming query based on the current builder state.
     * Returns a [Job] representing the background coroutine handling the stream.
     * The stream continuously listens for changes (if `keepAlive=true`) and invokes
     * the registered listeners (`onItemAdded`, `onItemUpdated`, `onItemDeleted`, `onItem`).
     *
     * Call `job.cancel()` to stop listening to the stream.
     *
     * @param T The expected type of the entities in the stream.
     * @param includeQueryResults If `true`, includes initial matching results before listening for changes.
     * @param keepAlive If `true`, keeps the connection open to receive real-time updates.
     * @return A [Job] controlling the streaming coroutine. Cancel this job to stop the stream.
     * @throws IllegalStateException if the builder is not in select mode or if the table name is missing.
     */
    inline fun <reified T : Any> stream(includeQueryResults: Boolean = true, keepAlive: Boolean = false): Job =
        stream<T>(T::class, includeQueryResults, keepAlive)

    /**
     * Initiates a streaming query based on the current builder state (non-reified version).
     *
     * @param T The expected type of the entities (unused directly here, used in reified version).
     * @param type The [KClass] of the entity type, used for deserialization.
     * @param includeQueryResults If `true`, includes initial matching results.
     * @param keepAlive If `true`, keeps the connection open for real-time updates.
     * @return A [Job] controlling the streaming coroutine.
     * @throws IllegalStateException if the builder is not in select mode or if the table name is missing.
     */
    fun <T> stream(type: KClass<*>, includeQueryResults: Boolean = true, keepAlive: Boolean = false): Job {
        check(mode == Mode.SELECT) { "Streaming is only applicable in select mode." }
        val targetTable =
            table ?: throw IllegalStateException("Table name must be specified using from() before calling stream().")

        val queryPayload = buildSelectQueryPayload()

        // We'll transform the raw line Flow into a Flow<StreamResponse> by parsing JSON
        return CoroutineScope(Dispatchers.IO).launch {
            client.stream(targetTable, queryPayload, includeQueryResults, keepAlive)
                .transform { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            // Attempt to parse
                            val obj = trimmed.fromJson<StreamResponse>()!!
                            emit(obj)
                        } catch (e: Exception) {
                            // If parse fails, skip or log an error
                        }
                    }
                }
                .onEach { response ->
                    when (response.action) {
                        "CREATE" -> onItemAddedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "UPDATE" -> onItemUpdatedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "DELETE" -> onItemDeletedListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        "QUERY_RESPONSE" -> onItemListener?.invoke(response.entity.toString().fromJson<T>(type) as Any)
                        else -> {}
                    }
                }.collect()
        }
    }

    // --- QueryResults Inner Class ---

    /**
     * Represents the results of a select query, including the records for the current page
     * and information needed for pagination. Also provides convenience methods for iteration.
     *
     * @param T The type of the records.
     * @property recordText The raw JSON array of records as returned by the API. Use [records] for deserialized access.
     * @property nextPage A token representing the next page of results, or `null` if this is the last page.
     * @property totalResults The total number of results matching the query across all pages (may not always be provided by the API).
     */
    data class QueryResults<T : Any>(
        @SerializedName("records")
        internal val recordText: JsonArray = JsonArray(), // Internal storage as JsonArray
        val nextPage: String? = null,
        val totalResults: Int = 0,
    ) {
        // Context needed for fetching next pages
        @Transient
        internal var query: QueryBuilder? = null

        @Transient
        internal var classType: KClass<*>? = null

        // Lazy deserialization of records for the current page
        /** The deserialized list of records for the current page. */
        val records: List<T> by lazy {
            try {
                val javaType = classType?.java ?: error("Class type not set for deserialization")
                recordText.toString().fromJsonList(javaType) ?: emptyList()
            } catch (e: Exception) {
                // Log error or return empty list on deserialization failure
                // println("Error deserializing records: ${e.message}")
                emptyList()
            }
        }

        /** Returns the first record on the current page, or throws [NoSuchElementException] if the page is empty. */
        fun first(): T = records.first()

        /** Returns the first record on the current page, or `null` if the page is empty. */
        fun firstOrNull(): T? = records.firstOrNull()

        /** Returns `true` if the current page contains no records. */
        fun isEmpty(): Boolean = records.isEmpty()

        /** Returns the number of records on the current page. */
        fun size(): Int = records.size

        /**
         * Iterates over each record on the *current page* only.
         * To iterate over all pages, use [forEachPage] or [getAllRecords].
         *
         * @param action The action to perform on each record.
         */
        fun forEachOnPage(action: (T) -> Unit) {
            records.forEach(action)
        }

        /**
         * Fetches and processes all pages of the query results sequentially
         * and iterate through each record in all pages
         *
         * @param action The action to perform on each record for all pages
         */
        fun forEach(action: (action: T) -> Boolean) {
            this.forEachPage { page ->
                page.forEach {
                    if (!action(it))
                        return@forEachPage false
                }
                true
            }
        }

        /**
         * Fetches and processes all pages of the query results sequentially.
         * This function fetches subsequent pages one by one as needed.
         *
         * **Caution**: This can lead to many API calls for large result sets.
         * Consider if processing page by page is more appropriate.
         *
         * @param action The action to perform on each list of records (one list per page).
         */
        fun forEachPage(action: (pageRecords: List<T>) -> Boolean) {
            var currentPage: QueryResults<T>? = this
            val currentQuery = query ?: error("Query context is missing for pagination.")
            val currentClassType = classType ?: error("Class type is missing for pagination.")

            while (currentPage != null) {
                val recordsOnPage = currentPage.records
                if (recordsOnPage.isNotEmpty()) {
                    if (!action(recordsOnPage)) {
                        return
                    }
                }

                // Check if there's a next page and fetch it
                val nextPageToken = currentPage.nextPage
                currentPage = if (!nextPageToken.isNullOrBlank()) {
                    // Update the query builder with the next page token and execute
                    currentQuery.nextPage(nextPageToken).list(currentClassType)
                } else {
                    null // No more pages
                }
            }
        }

        /**
         * Fetches all records from all pages and returns them as a single list.
         *
         * **Caution**: This loads the entire result set into memory. Use with care for large queries.
         * Prefer [forEachPage] for processing large datasets without excessive memory usage.
         *
         * @return A single list containing all records from all pages.
         */
        fun getAllRecords(): List<T> {
            val allRecords = mutableListOf<T>()
            forEachPage { pageRecords ->
                allRecords.addAll(pageRecords)
            }
            return allRecords
        }

        // --- Convenience methods operating on ALL records (use with caution) ---

        /** Filters all records across all pages based on the predicate. Loads all records into memory. */
        fun filter(predicate: (T) -> Boolean): List<T> = getAllRecords().filter(predicate)

        /** Maps all records across all pages using the transform function. Loads all records into memory. */
        fun <R> map(transform: (T) -> R): List<R> = getAllRecords().map(transform)

        /** Finds the maximum Double value across all records using the selector. Loads all records into memory. */
        fun maxOfDouble(selector: (T) -> Double): Double = getAllRecords().maxOfOrNull(selector) ?: Double.NaN

        /** Finds the minimum Double value across all records using the selector. Loads all records into memory. */
        fun minOfDouble(selector: (T) -> Double): Double = getAllRecords().minOfOrNull(selector) ?: Double.NaN

        /** Calculates the sum of Double values across all records using the selector. Loads all records into memory. */
        fun sumOfDouble(selector: (T) -> Double): Double = getAllRecords().sumOf(selector)

        /** Finds the maximum Float value across all records using the selector. Loads all records into memory. */
        fun maxOfFloat(selector: (T) -> Float): Float = getAllRecords().maxOfOrNull(selector) ?: Float.NaN

        /** Finds the minimum Float value across all records using the selector. Loads all records into memory. */
        fun minOfFloat(selector: (T) -> Float): Float = getAllRecords().minOfOrNull(selector) ?: Float.NaN

        /** Finds the maximum Int value across all records using the selector. Loads all records into memory. */
        fun maxOfInt(selector: (T) -> Int): Int = getAllRecords().maxOfOrNull(selector) ?: Int.MIN_VALUE

        /** Finds the minimum Int value across all records using the selector. Loads all records into memory. */
        fun minOfInt(selector: (T) -> Int): Int = getAllRecords().minOfOrNull(selector) ?: Int.MAX_VALUE

        /** Calculates the sum of Int values across all records using the selector. Loads all records into memory. */
        fun sumOfInt(selector: (T) -> Int): Int = getAllRecords().sumOf(selector)

        /** Finds the maximum Long value across all records using the selector. Loads all records into memory. */
        fun maxOfLong(selector: (T) -> Long): Long = getAllRecords().maxOfOrNull(selector) ?: Long.MIN_VALUE

        /** Finds the minimum Long value across all records using the selector. Loads all records into memory. */
        fun minOfLong(selector: (T) -> Long): Long = getAllRecords().minOfOrNull(selector) ?: Long.MAX_VALUE

        /** Calculates the sum of Long values across all records using the selector. Loads all records into memory. */
        fun sumOfLong(selector: (T) -> Long): Long = getAllRecords().sumOf(selector)

        /** Calculates the sum of BigDecimal values across all records using the selector. Loads all records into memory. */
        fun sumOfBigDecimal(selector: (T) -> BigDecimal): BigDecimal =
            getAllRecords().map(selector).fold(BigDecimal.ZERO, BigDecimal::add)

        // --- Parallel Processing (Example - operates page by page) ---
        // Note: True parallelism across pages requires more complex fetching logic.

        /**
         * Iterates through each page and processes the records on that page in parallel using Java Streams.
         * Fetches pages sequentially but processes items within a page in parallel.
         *
         * @param action The action to perform in parallel on each record within a page.
         */
        fun forEachPageParallel(action: (T) -> Unit) {
            // Use a dedicated executor for fetching next pages to avoid blocking parallel processing
            val fetchExecutor = Executors.newSingleThreadExecutor()
            var currentPage: QueryResults<T>? = this
            val currentQuery = query ?: error("Query context is missing for pagination.")
            val currentClassType = classType ?: error("Class type is missing for pagination.")

            try {
                while (currentPage != null) {
                    val recordsOnPage = currentPage.records
                    val nextPageToken = currentPage.nextPage // Capture next token before parallel processing

                    // Process current page in parallel
                    if (recordsOnPage.isNotEmpty()) {
                        // Use Java's parallel stream for processing within the page
                        recordsOnPage.parallelStream().forEach(action)
                    }

                    // Fetch next page asynchronously while current page might still be processing (if action is long)
                    val future = if (!nextPageToken.isNullOrBlank()) {
                        fetchExecutor.submit<QueryResults<T>?> {
                            try {
                                currentQuery.nextPage(nextPageToken).list(currentClassType)
                            } catch (e: Exception) {
                                // Log or handle fetch error for the next page
                                // println("Error fetching next page: ${e.message}")
                                null
                            }
                        }
                    } else {
                        null // No more pages to fetch
                    }

                    // Wait for the next page fetch to complete
                    currentPage = future?.get()
                }
            } finally {
                fetchExecutor.shutdown() // Clean up the fetch executor
            }
        }
    }
}

//endregion Query Builder

//region Helper Data Classes and Functions

/**
 * Represents the sort order for a specific field in a query.
 * Use the [asc] and [desc] helper functions to create instances.
 *
 * @property field The name of the field to sort by.
 * @property order The sort direction, either "ASC" (ascending) or "DESC" (descending). Defaults to "ASC".
 */
data class SortOrder(val field: String, val order: String = "ASC") {
    init {
        // Validate order direction
        require(order.uppercase() == "ASC" || order.uppercase() == "DESC") {
            "Order must be 'ASC' or 'DESC', but was '$order'"
        }
    }
}

/** Creates an ascending [SortOrder] for the specified attribute. */
fun asc(attribute: String) = SortOrder(attribute, order = "ASC")

/** Creates a descending [SortOrder] for the specified attribute. */
fun desc(attribute: String) = SortOrder(attribute, order = "DESC")

/**
 * Represents a single event or record received over a streaming connection.
 * Used internally by the [QueryBuilder.stream] method to parse JSON lines.
 *
 * @property action Indicates the type of event (e.g., "CREATE", "UPDATE", "DELETE", "QUERY_RESPONSE").
 * @property entity The actual data associated with the event, represented as a [JsonObject].
 * Needs further deserialization into the specific entity type.
 */
internal data class StreamResponse(
    val action: String?, // Make nullable to handle potential missing field
    val entity: JsonObject? // Make nullable for safety
)

//endregion Helper Data Classes and Functions