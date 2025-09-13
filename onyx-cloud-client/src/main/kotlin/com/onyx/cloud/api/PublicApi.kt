@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.onyx.cloud.api

import java.math.BigInteger

/**
 * Supported operators for building query criteria.
 */
enum class QueryCriteriaOperator {
    EQUAL,
    NOT_EQUAL,
    IN,
    NOT_IN,
    GREATER_THAN,
    GREATER_THAN_EQUAL,
    LESS_THAN,
    LESS_THAN_EQUAL,
    MATCHES,
    NOT_MATCHES,
    BETWEEN,
    LIKE,
    NOT_LIKE,
    CONTAINS,
    CONTAINS_IGNORE_CASE,
    NOT_CONTAINS,
    NOT_CONTAINS_IGNORE_CASE,
    STARTS_WITH,
    NOT_STARTS_WITH,
    IS_NULL,
    NOT_NULL
}

/**
 * Logical operator used to join conditions in a query.
 */
enum class LogicalOperator { AND, OR }

/**
 * Sort direction.
 */
enum class SortOrder { ASC, DESC }

/**
 * Sorting instruction for query results.
 *
 * @property field Field name to order by.
 * @property order Sort direction.
 */
data class Sort(
    val field: String,
    val order: SortOrder
)

/**
 * Actions emitted by real-time data streams.
 */
enum class StreamAction {
    CREATE,
    UPDATE,
    DELETE,
    QUERY_RESPONSE,
    KEEP_ALIVE
}

/**
 * Basic document representation used by the SDK.
 */
data class OnyxDocument(
    val documentId: String? = null,
    val path: String? = null,
    val created: java.util.Date? = null,
    val updated: java.util.Date? = null,
    val mimeType: String? = null,
    val content: String? = null
)

/**
 * Minimal fetch typing to avoid DOM lib dependency.
 */
interface FetchResponse {
    val ok: Boolean
    val status: Int
    val statusText: String
    fun header(name: String): String?
    fun text(): String
    val body: Any?
}

/**
 * Parameters accepted by [FetchImpl].
 */
data class FetchInit(
    val method: String? = null,
    val headers: Map<String, String>? = null,
    val body: String? = null
)

/**
 * Fetch implementation signature used by the SDK.
 */
typealias FetchImpl = (url: String, init: FetchInit?) -> FetchResponse

/**
 * Represents a single field comparison in a query.
 */
data class QueryCriteria(
    val field: String,
    val operator: QueryCriteriaOperator,
    val value: Any? = null
)

/**
 * Recursive condition structure used to express complex WHERE clauses.
 */
sealed interface QueryCondition

data class SingleCondition(val criteria: QueryCriteria) : QueryCondition

data class CompoundCondition(
    val operator: LogicalOperator,
    val conditions: List<QueryCondition>
) : QueryCondition

/**
 * Wire format for select queries sent to the server.
 */
data class SelectQuery(
    val type: String = "SelectQuery",
    val fields: List<String>? = null,
    val conditions: QueryCondition? = null,
    val sort: List<Sort>? = null,
    val limit: Int? = null,
    val distinct: Boolean? = null,
    val groupBy: List<String>? = null,
    val partition: String? = null,
    val resolvers: List<String>? = null
)

/**
 * Wire format for update queries sent to the server.
 */
data class UpdateQuery(
    val type: String = "UpdateQuery",
    val conditions: QueryCondition? = null,
    val updates: Map<String, Any?>,
    val sort: List<Sort>? = null,
    val limit: Int? = null,
    val partition: String? = null
)

/**
 * A single page of query results.
 */
data class QueryPage<T>(
    val records: List<T>,
    val nextPage: String? = null
)

/**
 * Array-like container for paginated query results.
 * Provides convenience methods for traversing and aggregating records.
 */
interface QueryResults<T : Any> : List<T> {
    /** Token for the next page of results or `null` if no more pages. */
    val nextPage: String?

    /** Returns the first record or throws if empty. */
    fun first(): T

    /** Returns the first record or `null` if the set is empty. */
    fun firstOrNull(): T?

    /** Iterates over each record on the current page only. */
    fun forEachOnPage(action: (T) -> Unit)

    /**
     * Iterates over every record across all pages sequentially.
     * Returning `false` stops iteration early.
     */
    fun forEachAll(action: (T) -> Boolean?)

    /**
     * Iterates page by page across the result set.
     * Returning `false` stops iteration early.
     */
    fun forEachPage(action: (List<T>) -> Boolean?)

    /** Collects all records from every page into a single list. */
    fun getAllRecords(): List<T>

    /** Filters all records using the provided predicate. */
    fun filterAll(predicate: (T) -> Boolean): List<T>

    /** Maps all records using the provided transform. */
    fun <R> mapAll(transform: (T) -> R): List<R>

    /** Extracts values for a field across all records. */
    fun values(field: String): List<Any?>

    /** Maximum value produced by the selector across all records. */
    fun maxOfDouble(selector: (T) -> Double): Double

    /** Minimum value produced by the selector across all records. */
    fun minOfDouble(selector: (T) -> Double): Double

    /** Sum of values produced by the selector across all records. */
    fun sumOfDouble(selector: (T) -> Double): Double

    /** Maximum float value from the selector. */
    fun maxOfFloat(selector: (T) -> Float): Float

    /** Minimum float value from the selector. */
    fun minOfFloat(selector: (T) -> Float): Float

    /** Sum of float values from the selector. */
    fun sumOfFloat(selector: (T) -> Float): Float

    /** Maximum integer value from the selector. */
    fun maxOfInt(selector: (T) -> Int): Int

    /** Minimum integer value from the selector. */
    fun minOfInt(selector: (T) -> Int): Int

    /** Sum of integer values from the selector. */
    fun sumOfInt(selector: (T) -> Int): Int

    /** Maximum long value from the selector. */
    fun maxOfLong(selector: (T) -> Long): Long

    /** Minimum long value from the selector. */
    fun minOfLong(selector: (T) -> Long): Long

    /** Sum of long values from the selector. */
    fun sumOfLong(selector: (T) -> Long): Long

    /** Sum of bigint values from the selector. */
    fun sumOfBigInt(selector: (T) -> BigInteger): BigInteger

    /** Executes an action for each page in parallel. */
    fun forEachPageParallel(action: (T) -> Unit)
}

/**
 * Builder used to compose query conditions.
 */
interface IConditionBuilder {
    /**
     * Combines this condition with another using `AND`.
     *
     * @param builder Additional condition builder.
     * @return This builder for chaining.
     */
    fun and(builder: IConditionBuilder): IConditionBuilder

    /**
     * Adds raw criteria combined with `AND`.
     *
     * @param criteria Additional criteria.
     * @return This builder for chaining.
     */
    fun and(criteria: QueryCriteria): IConditionBuilder

    /**
     * Combines this condition with another using `OR`.
     *
     * @param builder Additional condition builder.
     * @return This builder for chaining.
     */
    fun or(builder: IConditionBuilder): IConditionBuilder

    /**
     * Adds raw criteria combined with `OR`.
     *
     * @param criteria Additional criteria.
     * @return This builder for chaining.
     */
    fun or(criteria: QueryCriteria): IConditionBuilder

    /**
     * Materializes the composed condition.
     *
     * @return Built [QueryCondition].
     */
    fun toCondition(): QueryCondition
}

/**
 * Fluent query builder for constructing and executing operations.
 */
interface IQueryBuilder<T : Any> {
    /** Sets the table to query. */
    fun from(table: String): IQueryBuilder<T>

    /** Selects a subset of fields to return. */
    fun selectFields(vararg fields: String): IQueryBuilder<T>

    /** Resolves related values by name. */
    fun resolve(vararg values: String): IQueryBuilder<T>

    /** Adds a filter condition. */
    fun where(condition: QueryCriteria): IQueryBuilder<T>

    /** Adds a filter using a builder. */
    fun where(builder: IConditionBuilder): IQueryBuilder<T>

    /** Adds an additional `AND` criteria. */
    fun and(condition: QueryCriteria): IQueryBuilder<T>

    /** Adds an additional `AND` builder. */
    fun and(builder: IConditionBuilder): IQueryBuilder<T>

    /** Adds an additional `OR` criteria. */
    fun or(condition: QueryCriteria): IQueryBuilder<T>

    /** Adds an additional `OR` builder. */
    fun or(builder: IConditionBuilder): IQueryBuilder<T>

    /** Orders results by the provided fields. */
    fun orderBy(vararg sorts: Sort): IQueryBuilder<T>

    /** Groups results by the provided fields. */
    fun groupBy(vararg fields: String): IQueryBuilder<T>

    /** Ensures only distinct records are returned. */
    fun distinct(): IQueryBuilder<T>

    /** Limits the number of records returned. */
    fun limit(n: Int): IQueryBuilder<T>

    /** Restricts the query to a specific partition. */
    fun inPartition(partition: String): IQueryBuilder<T>

    /** Sets the page size for subsequent `list` or `page` calls. */
    fun pageSize(n: Int): IQueryBuilder<T>

    /** Continues a paged query using a next-page token. */
    fun nextPage(token: String): IQueryBuilder<T>

    /** Counts matching records. */
    fun count(): Long

    /** Lists records with optional pagination. */
    fun list(options: ListOptions? = null): QueryResults<T>

    /** Retrieves the first record or null. */
    fun firstOrNull(): T?

    /** Retrieves exactly one record or null. */
    fun one(): T?

    /** Retrieves a single page of records with optional next token. */
    fun page(options: ListOptions? = null): QueryPage<T>

    /** Sets field updates for an update query. */
    fun setUpdates(updates: Map<String, Any?>): IQueryBuilder<T>

    /** Executes an update operation. */
    fun update(): Any?

    /** Executes a delete operation. */
    fun delete(): Any?

    /** Registers a listener for added items on a stream. */
    fun onItemAdded(listener: (T) -> Unit): IQueryBuilder<T>

    /** Registers a listener for updated items on a stream. */
    fun onItemUpdated(listener: (T) -> Unit): IQueryBuilder<T>

    /** Registers a listener for deleted items on a stream. */
    fun onItemDeleted(listener: (T) -> Unit): IQueryBuilder<T>

    /** Registers a listener for any stream item with its action. */
    fun onItem(listener: (T?, StreamAction) -> Unit): IQueryBuilder<T>

    /** Starts a stream including query results. */
    fun stream(includeQueryResults: Boolean = false, keepAlive: Boolean = false): StreamHandle

    /** Starts a stream emitting only events. */
    fun streamEventsOnly(keepAlive: Boolean = false): StreamHandle

    /** Starts a stream that returns events alongside query results. */
    fun streamWithQueryResults(keepAlive: Boolean = false): StreamHandle
}

/**
 * Options for paged queries.
 *
 * @property pageSize Number of records per page.
 * @property nextPage Token for the next page.
 */
data class ListOptions(val pageSize: Int? = null, val nextPage: String? = null)

/**
 * Handle returned by streaming operations.
 *
 * @property cancel Cancels the active stream.
 */
data class StreamHandle(val cancel: () -> Unit)

/** Builder for save operations. */
interface ISaveBuilder<T : Any> {
    /** Cascades specified relationships when saving. */
    fun cascade(vararg relationships: String): ISaveBuilder<T>

    /** Persists a single entity. */
    fun one(entity: Map<String, Any?>): Any?

    /** Persists multiple entities. */
    fun many(entities: List<Map<String, Any?>>): Any?
}

/** Builder for cascading save/delete operations across multiple tables. */
interface ICascadeBuilder<Schema : Any> {
    /** Specifies relationships to cascade through. */
    fun cascade(vararg relationships: String): ICascadeBuilder<Schema>

    /** Saves one or many entities for a given table. */
    fun save(table: String, entityOrEntities: Any): Any?

    /** Deletes an entity by primary key. */
    fun delete(table: String, primaryKey: String): Any?
}

/** Builder for describing cascade relationship metadata. */
interface ICascadeRelationshipBuilder {
    /** Names the relationship graph. */
    fun graph(name: String): ICascadeRelationshipBuilder

    /** Sets the graph type. */
    fun graphType(type: String): ICascadeRelationshipBuilder

    /** Field on the target entity. */
    fun targetField(field: String): ICascadeRelationshipBuilder

    /** Field on the source entity. */
    fun sourceField(field: String): String
}

/**
 * Configuration options for initializing the client.
 *
 * @property baseUrl Base API URL.
 * @property databaseId Target database ID.
 * @property apiKey API key.
 * @property apiSecret API secret.
 * @property fetch Custom fetch implementation.
 * @property partition Default partition for queries.
 * @property requestLoggingEnabled Log HTTP requests.
 * @property responseLoggingEnabled Log HTTP responses.
 * @property ttl Milliseconds to cache resolved credentials.
 */
data class OnyxConfig(
    val baseUrl: String? = null,
    val databaseId: String? = null,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val fetch: FetchImpl? = null,
    val partition: String? = null,
    val requestLoggingEnabled: Boolean? = null,
    val responseLoggingEnabled: Boolean? = null,
    val ttl: Long? = null
)

/** Database client interface. */
interface IOnyxDatabase<Schema : Any> {
    /** Begin a query against a table. */
    fun from(table: String): IQueryBuilder<Any>

    /** Select specific fields for a query. */
    fun select(vararg fields: String): IQueryBuilder<Map<String, Any?>>

    /** Include related records in the next save or delete. */
    fun cascade(vararg relationships: String): ICascadeBuilder<Schema>

    /** Build cascade relationship strings programmatically. */
    fun cascadeBuilder(): ICascadeRelationshipBuilder

    /** Start a save builder for inserting or updating entities. */
    fun save(table: String): ISaveBuilder<Any>

    /** Save one or many entities immediately. */
    fun save(
        table: String,
        entityOrEntities: Any,
        options: SaveOptions? = null
    ): Any?

    /** Save many entities in configurable batches. */
    fun batchSave(
        table: String,
        entities: List<Any>,
        batchSize: Int = 1000,
        options: SaveOptions? = null
    )

    /** Retrieve an entity by its primary key. */
    fun findById(
        table: String,
        primaryKey: String,
        options: FindOptions? = null
    ): Any?

    /** Delete an entity by primary key. */
    fun delete(
        table: String,
        primaryKey: String,
        options: DeleteOptions? = null
    ): Any?

    /** Store a document (file blob) for later retrieval. */
    fun saveDocument(doc: OnyxDocument): Any?

    /** Fetch a previously saved document. */
    fun getDocument(documentId: String, options: DocumentOptions? = null): Any?

    /** Remove a stored document permanently. */
    fun deleteDocument(documentId: String): Any?

    /** Cancels active streams; safe to call multiple times. */
    fun close()
}

/**
 * Options for save operations.
 *
 * @property relationships Cascade relationships to include.
 */
data class SaveOptions(val relationships: List<String>? = null)

/**
 * Options for findById operations.
 *
 * @property partition Partition to search.
 * @property resolvers Related resolvers to include.
 */
data class FindOptions(val partition: String? = null, val resolvers: List<String>? = null)

/**
 * Options for delete operations.
 *
 * @property partition Partition containing the entity.
 * @property relationships Cascade relationships to include.
 */
data class DeleteOptions(val partition: String? = null, val relationships: List<String>? = null)

/**
 * Options for document retrieval.
 *
 * @property width Image width.
 * @property height Image height.
 */
data class DocumentOptions(val width: Int? = null, val height: Int? = null)

/** Facade for constructing database clients. */
interface OnyxFacade {
    /**
     * Initialize a database client.
     *
     * @param config Connection settings and optional custom fetch.
     */
    fun <Schema : Any> init(config: OnyxConfig? = null): IOnyxDatabase<Schema>

    /**
     * Clear cached configuration so the next [init] call re-resolves credentials.
     */
    fun clearCacheConfig()
}

/**
 * Creates ascending sort instruction.
 *
 * @param field Field name to order by.
 */
fun asc(field: String): Sort = Sort(field, SortOrder.ASC)

/**
 * Creates descending sort instruction.
 *
 * @param field Field name to order by.
 */
fun desc(field: String): Sort = Sort(field, SortOrder.DESC)

/**
 * Equality condition.
 *
 * @param field Field to compare.
 * @param value Value to match.
 */
fun eq(field: String, value: Any?): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.EQUAL, value))

/**
 * Inequality condition.
 *
 * @param field Field to compare.
 * @param value Value that should not match.
 */
fun neq(field: String, value: Any?): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_EQUAL, value))

/**
 * In condition.
 *
 * @param field Field to compare.
 * @param values Allowed values or comma-delimited string.
 */
fun inOp(field: String, values: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.IN, values))

/**
 * Not in condition.
 *
 * @param field Field to compare.
 * @param values Disallowed values.
 */
fun notIn(field: String, values: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_IN, values))

/**
 * Between condition.
 *
 * @param field Field to compare.
 * @param lower Lower bound.
 * @param upper Upper bound.
 */
fun between(field: String, lower: Any, upper: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.BETWEEN, listOf(lower, upper)))

/**
 * Greater than condition.
 *
 * @param field Field to compare.
 * @param value Value that must be exceeded.
 */
fun gt(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.GREATER_THAN, value))

/**
 * Greater than or equal condition.
 */
fun gte(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.GREATER_THAN_EQUAL, value))

/**
 * Less than condition.
 */
fun lt(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.LESS_THAN, value))

/**
 * Less than or equal condition.
 */
fun lte(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.LESS_THAN_EQUAL, value))

/**
 * Regex match condition.
 */
fun matches(field: String, regex: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.MATCHES, regex))

/**
 * Negative regex match condition.
 */
fun notMatches(field: String, regex: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_MATCHES, regex))

/**
 * Like condition.
 */
fun like(field: String, pattern: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.LIKE, pattern))

/**
 * Not like condition.
 */
fun notLike(field: String, pattern: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_LIKE, pattern))

/**
 * Contains condition.
 */
fun contains(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.CONTAINS, value))

/**
 * Case-insensitive contains condition.
 */
fun containsIgnoreCase(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value))

/**
 * Not contains condition.
 */
fun notContains(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_CONTAINS, value))

/**
 * Case-insensitive not contains condition.
 */
fun notContainsIgnoreCase(field: String, value: Any): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value))

/**
 * Starts with condition.
 */
fun startsWith(field: String, prefix: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.STARTS_WITH, prefix))

/**
 * Not starts with condition.
 */
fun notStartsWith(field: String, prefix: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_STARTS_WITH, prefix))

/**
 * Is null condition.
 */
fun isNull(field: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.IS_NULL, null))

/**
 * Not null condition.
 */
fun notNull(field: String): ConditionBuilderImpl =
    ConditionBuilderImpl(QueryCriteria(field, QueryCriteriaOperator.NOT_NULL, null))

/**
 * Average aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun avg(attribute: String): String = "avg($attribute)"

/**
 * Sum aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun sum(attribute: String): String = "sum($attribute)"

/**
 * Count aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun count(attribute: String): String = "count($attribute)"

/**
 * Minimum aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun min(attribute: String): String = "min($attribute)"

/**
 * Maximum aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun max(attribute: String): String = "max($attribute)"

/**
 * Standard deviation aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun std(attribute: String): String = "std($attribute)"

/**
 * Variance aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun variance(attribute: String): String = "variance($attribute)"

/**
 * Median aggregation helper.
 *
 * @param attribute Attribute name.
 */
fun median(attribute: String): String = "median($attribute)"

/**
 * Uppercase transform helper.
 *
 * @param attribute Attribute name.
 */
fun upper(attribute: String): String = "upper($attribute)"

/**
 * Lowercase transform helper.
 *
 * @param attribute Attribute name.
 */
fun lower(attribute: String): String = "lower($attribute)"

/**
 * Substring transform helper.
 *
 * @param attribute Attribute name.
 * @param from Starting index.
 * @param length Number of characters.
 */
fun substring(attribute: String, from: Int, length: Int): String =
    "substring($attribute,$from,$length)"

/**
 * Replace transform helper.
 *
 * @param attribute Attribute name.
 * @param pattern Pattern to match.
 * @param repl Replacement value.
 */
fun replace(attribute: String, pattern: String, repl: String): String =
    "replace($attribute,$pattern,$repl)"

/**
 * Percentile aggregation helper.
 *
 * @param attribute Attribute name.
 * @param p Percentile value.
 */
fun percentile(attribute: String, p: Number): String = "percentile($attribute,$p)"

/** SDK name. */
const val sdkName: String = "@onyx.dev/onyx-database"

/** SDK version. */
const val sdkVersion: String = "0.1.0"

