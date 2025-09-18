@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.onyx.cloud.api

import com.onyx.cloud.QueryBuilder
import com.onyx.cloud.QueryCondition
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
 * Array-like container for paginated query results.
 * Provides convenience methods for traversing and aggregating records.
 */
interface IQueryResults<T : Any> : List<T> {

    /** Token for the next page of results or `null` if no more pages. */
    val nextPage: String?

    /** Returns the first record or throws if empty. */
    fun first(): T

    /** Returns the first record or `null` if the set is empty. */
    fun firstOrNull(): T?

    /** Iterates over each record on the current page only. */
    fun forEachOnPage(action: (T) -> Unit)

    /**
     *
     * Iterates over every record across all pages sequentially.
     * Returning `false` stops iteration early.
     */
    fun forEachAll(action: (T) -> Boolean)

    /**
     * Iterates page by page across the result set.
     * Returning `false` stops iteration early.
     */
    fun forEachPage(action: (List<T>) -> Boolean)

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
    fun toCondition(): QueryCondition?
}

/**
 * Fluent query builder for constructing and executing operations.
 */
interface IQueryBuilder {

    /** Selects a subset of fields to return. */
    fun select(vararg fields: String): IQueryBuilder

    /** Resolves related values by name. */
    fun resolve(vararg values: String): IQueryBuilder

    /** Adds a filter using a builder. */
    fun where(builder: IConditionBuilder): IQueryBuilder

    /** Adds an additional `AND` builder. */
    fun and(builder: IConditionBuilder): IQueryBuilder

    /** Adds an additional `OR` builder. */
    fun or(builder: IConditionBuilder): IQueryBuilder

    /** Orders results by the provided fields. */
    fun orderBy(vararg sorts: Sort): IQueryBuilder

    /** Groups results by the provided fields. */
    fun groupBy(vararg fields: String): IQueryBuilder

    /** Ensures only distinct records are returned. */
    fun distinct(): IQueryBuilder

    /** Limits the number of records returned. */
    fun limit(n: Int): IQueryBuilder

    /** Restricts the query to a specific partition. */
    fun inPartition(partition: String): IQueryBuilder

    /** Sets the page size for subsequent `list` or `page` calls. */
    fun pageSize(n: Int): IQueryBuilder

    /** Continues a paged query using a next-page token. */
    fun nextPage(token: String): IQueryBuilder

    /** Counts matching records. */
    fun count(): Int

    /** Lists records with optional pagination. */
    fun <T : Any> list(): IQueryResults<T>

    /** Retrieves the first record or null. */
    fun <T : Any> firstOrNull(): T?

    /** Retrieves exactly one record or null. */
    fun <T : Any> one(): T?

    /** Sets field updates for an update query. */
    fun setUpdates(vararg updates: Pair<String, Any?>): IQueryBuilder

    /** Executes an update operation. */
    fun update(): Int

    /** Executes a delete operation. */
    fun delete(): Int

    /** Registers a listener for added items on a stream. */
    fun <T : Any> onItemAdded(listener: (T) -> Unit): IQueryBuilder

    /** Registers a listener for updated items on a stream. */
    fun <T : Any> onItemUpdated(listener: (T) -> Unit): IQueryBuilder

    /** Registers a listener for deleted items on a stream. */
    fun <T : Any> onItemDeleted(listener: (T) -> Unit): IQueryBuilder

    /** Registers a listener for any stream item with its action. */
    fun <T : Any> onItem(listener: (T) -> Unit): IQueryBuilder

    /** Starts a stream including query results. */
    fun <T> stream(includeQueryResults: Boolean = false, keepAlive: Boolean = false): IStreamSubscription
}

/** Sets the table to query. */
inline fun <reified T> IQueryBuilder.from(): IQueryBuilder {
    (this as? QueryBuilder)?.apply {
        this.table = T::class.simpleName!!
        this.type = T::class
    }
    return this
}

interface IStreamSubscription : AutoCloseable {
    /** Stops the stream without waiting for the background thread to finish. */
    fun cancel()

    /** Waits for the background thread to finish processing. */
    fun join()

    /** Convenience helper that cancels the stream and waits for it to finish. */
    fun cancelAndJoin()

    /** Latest error observed while streaming, or `null` if none occurred. */
    val error: Throwable?
}

/**
 * Options for paged queries.
 *
 * @property pageSize Number of records per page.
 * @property nextPage Token for the next page.
 */
data class ListOptions(val pageSize: Int? = null, val nextPage: String? = null)

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
interface ICascadeBuilder {
    /** Specifies relationships to cascade through. */
    fun cascade(vararg relationships: String): ICascadeBuilder

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

    /** Select specific fields for a query. */
    fun select(vararg fields: String): IQueryBuilder

    /**
     * Include related entities for cascading when saving or deleting.
     *
     * @param relationships Relationship strings to cascade.
     * @return Builder for cascading save or delete operations.
     */
    fun cascade(vararg relationships: String): ICascadeBuilder =
        CascadeBuilderImpl(this, relationships.toList())

    /** Save one or many entities immediately. */
    fun <T> save(
        table: String,
        entityOrEntities: T,
        options: SaveOptions? = null
    ): T

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
 * @receiver Field to compare.
 * @param value Value to match.
 */
infix fun String.eq(value: Any?): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.EQUAL, value))

/**
 * Inequality condition.
 *
 * @receiver Field to compare.
 * @param value Value that should not match.
 */
infix fun String.neq(value: Any?): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value))

/**
 * In condition.
 *
 * @receiver Field to compare.
 * @param values Allowed values or comma-delimited string.
 */
infix fun String.inOp(values: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.IN, values))

/**
 * Not in condition.
 *
 * @receiver Field to compare.
 * @param values Disallowed values.
 */
infix fun String.notIn(values: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values))

/**
 * Between condition.
 *
 * @receiver Field to compare.
 * @param bounds Lower and upper bounds as a pair.
 *
 * Usage: `"age" between (18 to 30)`
 */
infix fun <T : Any> String.between(bounds: Pair<T, T>): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(bounds.first, bounds.second)))

/**
 * Between condition.
 *
 * @receiver Field to compare.
 * @param from Lower bound
 * @param to Upper bound
 *
 * Usage: `"age" between (18 to 30)`
 */
fun <T : Any> String.between(from: T, to: T): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(from, to)))

/**
 * Greater than condition.
 *
 * @receiver Field to compare.
 * @param value Value that must be exceeded.
 */
infix fun String.gt(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value))

/**
 * Greater than or equal condition.
 *
 * @receiver Field to compare.
 * @param value Value that must be met or exceeded.
 */
infix fun String.gte(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value))

/**
 * Less than condition.
 *
 * @receiver Field to compare.
 * @param value Upper bound (exclusive).
 */
infix fun String.lt(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value))

/**
 * Less than or equal condition.
 *
 * @receiver Field to compare.
 * @param value Upper bound (inclusive).
 */
infix fun String.lte(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value))

/**
 * Regex match condition.
 *
 * @receiver Field to compare.
 * @param regex Regular expression to match.
 */
infix fun String.matches(regex: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.MATCHES, regex))

/**
 * Negative regex match condition.
 *
 * @receiver Field to compare.
 * @param regex Regular expression that must not match.
 */
infix fun String.notMatches(regex: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, regex))

/**
 * Like condition.
 *
 * @receiver Field to compare.
 * @param pattern Pattern for LIKE comparison.
 */
infix fun String.like(pattern: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LIKE, pattern))

/**
 * Not like condition.
 *
 * @receiver Field to compare.
 * @param pattern Pattern that must not match.
 */
infix fun String.notLike(pattern: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, pattern))

/**
 * Contains condition.
 *
 * @receiver Field to compare.
 * @param value Value that must be contained.
 */
infix fun String.contains(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value))

/**
 * Case-insensitive contains condition.
 *
 * @receiver Field to compare.
 * @param value Value that must be contained (case-insensitive).
 */
infix fun String.containsIgnoreCase(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value))

/**
 * Not contains condition.
 *
 * @receiver Field to compare.
 * @param value Value that must not be contained.
 */
infix fun String.notContains(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value))

/**
 * Case-insensitive not contains condition.
 *
 * @receiver Field to compare.
 * @param value Value that must not be contained (case-insensitive).
 */
infix fun String.notContainsIgnoreCase(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value))

/**
 * Starts with condition.
 *
 * @receiver Field to compare.
 * @param prefix Required prefix.
 */
infix fun String.startsWith(prefix: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, prefix))

/**
 * Not starts with condition.
 *
 * @receiver Field to compare.
 * @param prefix Disallowed prefix.
 */
infix fun String.notStartsWith(prefix: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, prefix))

/**
 * Is null condition.
 *
 * @receiver Field to compare.
 */
fun String.isNull(): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.IS_NULL, null))

/**
 * Not null condition.
 *
 * @receiver Field to compare.
 */
fun String.notNull(): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_NULL, null))

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
