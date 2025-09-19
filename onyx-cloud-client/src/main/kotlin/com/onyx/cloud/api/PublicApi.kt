@file:Suppress("unused", "UNUSED_PARAMETER", "UNCHECKED_CAST")

package com.onyx.cloud.api

import com.onyx.cloud.impl.CascadeBuilderImpl
import com.onyx.cloud.impl.ConditionBuilderImpl
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.impl.OnyxFacadeImpl
import com.onyx.cloud.impl.QueryBuilder
import com.onyx.cloud.impl.QueryCondition
import java.math.BigInteger
import java.util.Date
import kotlin.reflect.KClass

/**
 * The primary entry point to the Onyx SDK. This facade provides access to database initialization.
 *
 * Example usage:
 * ```kotlin
 * val config = OnyxConfig(databaseId = "your-db-id", apiKey = "...", apiSecret = "...")
 * val db = onyx.init<AppSchema>(config)
 * ```
 */
val onyx: OnyxFacade = OnyxFacadeImpl

/**
 * Defines the supported operators for building query criteria in a `where` clause.
 * These operators are used to compare fields with specified values.
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
 * Specifies the logical operator used to join multiple conditions in a query.
 */
enum class LogicalOperator {
    /** Joins conditions with a logical AND. */
    AND,

    /** Joins conditions with a logical OR. */
    OR
}

/**
 * Defines the direction for sorting query results.
 */
enum class SortOrder {
    /** Sorts results in ascending order. */
    ASC,

    /** Sorts results in descending order. */
    DESC
}

/**
 * Represents a sorting instruction for a query, specifying the field and direction.
 *
 * @property field The name of the field to order the results by.
 * @property order The sort direction, either [SortOrder.ASC] or [SortOrder.DESC].
 *
 * Example usage:
 * ```kotlin
 * // Creates a descending sort instruction for the 'age' field.
 * val sortByAgeDesc = Sort("age", SortOrder.DESC)
 *
 * // It's often more convenient to use the asc() and desc() helper functions.
 * val sortByNameAsc = asc("name")
 *
 * // Use in a query:
 * db.from<User>().orderBy(desc("age"), asc("name")).list()
 * ```
 */
data class Sort(
    val field: String,
    val order: SortOrder
)

/**
 * Represents a basic document or file stored within the Onyx system.
 *
 * @property documentId The unique identifier for the document.
 * @property path The storage path or location of the document.
 * @property created The timestamp when the document was created.
 * @property updated The timestamp when the document was last updated.
 * @property mimeType The MIME type of the document content (e.g., "image/jpeg").
 * @property content The actual content of the document, typically as a Base64 encoded string.
 *
 * Example usage:
 * ```kotlin
 * val myDocument = OnyxDocument(
 * mimeType = "text/plain",
 * content = "SGVsbG8sIFdvcmxkIQ==" // "Hello, World!" in Base64
 * )
 * val savedDocument = db.saveDocument(myDocument)
 * ```
 */
data class OnyxDocument(
    val documentId: String = "",
    val path: String = "null",
    val created: Date = Date(),
    val updated: Date = Date(),
    val mimeType: String? = null,
    val content: String = ""
)

/**
 * A specialized list that represents a paginated collection of query results.
 * It provides the records for the current page and convenience methods for pagination,
 * iteration, and aggregation across the entire result set.
 *
 * @param T The type of the elements in the results.
 *
 * Example usage:
 * ```kotlin
 * var results: IQueryResults<User> = db.from<User>().pageSize(50).list()
 *
 * // Process the first page
 * results.forEachOnPage { user -> println(user.name) }
 *
 * // Fetch the next page if it exists
 * results.nextPage?.let { token ->
 * results = db.from<User>().pageSize(50).nextPage(token).list()
 * }
 *
 * // Or, iterate through all users across all pages automatically
 * results.forEachAll { user ->
 * println("Processing user: ${user.name}")
 * true // return true to continue iteration
 * }
 * ```
 */
interface IQueryResults<T : Any> : List<T> {

    /** A token representing the next page of results. It is `null` if this is the last page. */
    val nextPage: String?

    /**
     * Returns the first record in the current page.
     * @return The first element.
     * @throws NoSuchElementException if the result set is empty.
     */
    fun first(): T

    /**
     * Returns the first record in the current page, or `null` if the result set is empty.
     * @return The first element, or `null`.
     */
    fun firstOrNull(): T?

    /**
     * Performs the given [action] on each record *on the current page only*.
     * @param action The action to perform on each element.
     */
    fun forEachOnPage(action: (T) -> Unit)

    /**
     * Iterates over every record across *all pages* of the result set, fetching subsequent pages
     * as needed. The iteration can be stopped early by returning `false` from the [action].
     *
     * @param action The action to perform on each element. Return `true` to continue, `false` to stop.
     */
    fun forEachAll(action: (T) -> Boolean)

    /**
     * Iterates through the result set *page by page*, fetching subsequent pages as needed.
     * The iteration can be stopped early by returning `false` from the [action].
     *
     * @param action The action to perform on each page (a `List<T>`). Return `true` to continue, `false` to stop.
     */
    fun forEachPage(action: (List<T>) -> Boolean)

    /**
     * Fetches and collects all records from every page into a single, comprehensive list.
     * **Warning**: This can consume significant memory for very large result sets.
     * @return A list containing all records from all pages.
     */
    fun getAllRecords(): List<T>

    /**
     * Filters all records across all pages using the given [predicate].
     * @param predicate The condition to filter by.
     * @return A new list containing only the elements that match the predicate.
     */
    fun filterAll(predicate: (T) -> Boolean): List<T>

    /**
     * Maps all records across all pages using the given [transform] function.
     * @param transform The function to apply to each element.
     * @return A new list containing the transformed elements.
     */
    fun <R> mapAll(transform: (T) -> R): List<R>

    /**
     * Extracts the values of a specific [field] from all records across all pages.
     * @param field The name of the field to extract.
     * @return A list of the values for the specified field.
     */
    fun values(field: String): List<Any?>

    /**
     * Executes the given [action] for each item across all pages in parallel. This can improve
     * performance for CPU-bound operations but does not guarantee order.
     *
     * @param action The action to perform on each element.
     */
    fun forEachPageParallel(action: (T) -> Unit)

    // --- Aggregation Functions ---

    /** Returns the maximum value among all records across all pages using the given [selector]. */
    fun maxOfDouble(selector: (T) -> Double): Double

    /** Returns the minimum value among all records across all pages using the given [selector]. */
    fun minOfDouble(selector: (T) -> Double): Double

    /** Returns the sum of all values produced by the [selector] function across all pages. */
    fun sumOfDouble(selector: (T) -> Double): Double

    /** Returns the maximum value among all records across all pages using the given [selector]. */
    fun maxOfFloat(selector: (T) -> Float): Float

    /** Returns the minimum value among all records across all pages using the given [selector]. */
    fun minOfFloat(selector: (T) -> Float): Float

    /** Returns the sum of all values produced by the [selector] function across all pages. */
    fun sumOfFloat(selector: (T) -> Float): Float

    /** Returns the maximum value among all records across all pages using the given [selector]. */
    fun maxOfInt(selector: (T) -> Int): Int

    /** Returns the minimum value among all records across all pages using the given [selector]. */
    fun minOfInt(selector: (T) -> Int): Int

    /** Returns the sum of all values produced by the [selector] function across all pages. */
    fun sumOfInt(selector: (T) -> Int): Int

    /** Returns the maximum value among all records across all pages using the given [selector]. */
    fun maxOfLong(selector: (T) -> Long): Long

    /** Returns the minimum value among all records across all pages using the given [selector]. */
    fun minOfLong(selector: (T) -> Long): Long

    /** Returns the sum of all values produced by the [selector] function across all pages. */
    fun sumOfLong(selector: (T) -> Long): Long

    /** Returns the sum of all values produced by the [selector] function across all pages. */
    fun sumOfBigInt(selector: (T) -> BigInteger): BigInteger
}

/**
 * A builder interface for constructing complex query conditions by chaining criteria together
 * with logical [AND][and] and [OR][or] operators.
 *
 * Example usage:
 * ```kotlin
 * // Find users who are 18 or older AND (live in "New York" OR are an "admin")
 * val results = db.from<User>().where(
 * ("age" gte 18) and (("city" eq "New York") or ("role" eq "admin"))
 * ).list()
 * ```
 */
interface IConditionBuilder {
    /**
     * Combines the current condition with another condition using a logical `AND`.
     *
     * @param builder The other condition builder to combine with.
     * @return This builder instance for further chaining.
     */
    fun and(builder: IConditionBuilder): IConditionBuilder

    /**
     * Combines the current condition with a raw [QueryCriteria] using a logical `AND`.
     *
     * @param criteria The criteria to add to the condition.
     * @return This builder instance for further chaining.
     */
    fun and(criteria: QueryCriteria): IConditionBuilder

    /**
     * Combines the current condition with another condition using a logical `OR`.
     *
     * @param builder The other condition builder to combine with.
     * @return This builder instance for further chaining.
     */
    fun or(builder: IConditionBuilder): IConditionBuilder

    /**
     * Combines the current condition with a raw [QueryCriteria] using a logical `OR`.
     *
     * @param criteria The criteria to add to the condition.
     * @return This builder instance for further chaining.
     */
    fun or(criteria: QueryCriteria): IConditionBuilder

    /**
     * Finalizes and builds the composed condition object.
     *
     * @return The resulting [QueryCondition], or `null` if no conditions were added.
     */
    fun toCondition(): QueryCondition?
}

/**
 * Represents a single comparison criteria within a query's `where` clause.
 *
 * @property field The name of the field to be compared.
 * @property operator The [QueryCriteriaOperator] used for the comparison (e.g., [EQUAL][QueryCriteriaOperator.EQUAL]).
 * @property value The value to compare against. Can be `null` for operators like [IS_NULL][QueryCriteriaOperator.IS_NULL].
 */
data class QueryCriteria(
    val field: String,
    val operator: QueryCriteriaOperator,
    val value: Any? = null
)

/**
 * A fluent builder for constructing and executing database queries for fetching, updating,
 * or deleting records, as well as establishing real-time data streams.
 */
interface IQueryBuilder {

    /**
     * Specifies a subset of fields to be returned in the query results. Can also be used
     * with aggregation functions.
     *
     * @param fields The field names or aggregation functions to select.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * // Select only the 'name' and 'email' fields
     * db.from<User>().select("name", "email").list()
     *
     * // Use with aggregation
     * db.from<Product>()
     * .select("category", avg("price"), count("id"))
     * .groupBy("category")
     * .list()
     * ```
     */
    fun select(vararg fields: String): IQueryBuilder

    /**
     * Resolves and includes related data or computed values by name.
     * @param values The names of the resolvers to execute.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * // Assuming a 'latestOrder' resolver is defined for the User entity
     * val userWithOrder = db.from<User>().resolve("latestOrder").firstOrNull()
     * ```
     */
    fun resolve(vararg values: String): IQueryBuilder

    /**
     * Adds a filter condition to the query's `WHERE` clause.
     * @param builder An [IConditionBuilder] that defines the filter logic.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<User>()
     * .where(("age" gte 18) and ("city" eq "New York"))
     * .list()
     * ```
     */
    fun where(builder: IConditionBuilder): IQueryBuilder

    /**
     * Adds an additional filter condition, joined with the existing one by `AND`.
     * @param builder An [IConditionBuilder] that defines the additional filter logic.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<User>()
     * .where("status" eq "active")
     * .and("age" gte 21)
     * .list()
     * ```
     */
    fun and(builder: IConditionBuilder): IQueryBuilder

    /**
     * Adds an additional filter condition, joined with the existing one by `OR`.
     * @param builder An [IConditionBuilder] that defines the alternative filter logic.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<User>()
     * .where("role" eq "admin")
     * .or("isSuperUser" eq true)
     * .list()
     * ```
     */
    fun or(builder: IConditionBuilder): IQueryBuilder

    /**
     * Specifies the ordering of the query results.
     * @param sorts One or more [Sort] objects, typically created with [asc] or [desc].
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<User>()
     * .orderBy(desc("age"), asc("name"))
     * .list()
     * ```
     */
    fun orderBy(vararg sorts: Sort): IQueryBuilder

    /**
     * Groups the results by one or more fields, for use with aggregate functions in `select`.
     * @param fields The field names to group by.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<Product>()
     * .select("category", avg("price"))
     * .groupBy("category")
     * .list()
     * ```
     */
    fun groupBy(vararg fields: String): IQueryBuilder

    /**
     * Ensures that only distinct (unique) records are returned.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * // Get a list of unique cities where users live
     * db.select("city").from<User>().distinct().list()
     * ```
     */
    fun distinct(): IQueryBuilder

    /**
     * Limits the maximum number of records returned by the query.
     * @param n The maximum number of records.
     * @return This builder for chaining.
     */
    fun limit(n: Int): IQueryBuilder

    /**
     * Restricts the query to a specific data partition.
     * @param partition The name of the partition.
     * @return This builder for chaining.
     */
    fun inPartition(partition: String): IQueryBuilder

    /**
     * Sets the number of records to retrieve per page for paginated queries.
     * @param n The page size.
     * @return This builder for chaining.
     */
    fun pageSize(n: Int): IQueryBuilder

    /**
     * Specifies the token to use for fetching the next page of results from a previous query.
     * The token is obtained from [IQueryResults.nextPage].
     * @param token The next page token.
     * @return This builder for chaining.
     */
    fun nextPage(token: String): IQueryBuilder

    /** Executes the query and returns the total count of matching records. */
    fun count(): Int

    /**
     * Executes the query and returns the results as a paginated list.
     * @return An [IQueryResults] object containing the first page of data.
     */
    fun <T : Any> list(): IQueryResults<T>

    /**
     * Executes the query and returns the first matching record, or `null` if no records are found.
     * This is an efficient way to retrieve a single optional item.
     */
    fun <T : Any> firstOrNull(): T?

    /**
     * Executes the query and returns exactly one record.
     * @return The single matching record.
     * @throws IllegalStateException if zero or more than one record is found.
     */
    fun <T : Any> one(): T?

    /**
     * Specifies the field updates to be applied in an `update` operation.
     * @param updates Pairs of field names and their new values.
     * @return This builder for chaining.
     *
     * Example usage:
     * ```kotlin
     * db.from<User>()
     * .where("id" eq "user-123")
     * .setUpdates("status" to "inactive", "lastLogin" to Date())
     * .update()
     * ```
     */
    fun setUpdates(vararg updates: Pair<String, Any?>): IQueryBuilder

    /**
     * Executes an update operation based on the query's `where` clause and `setUpdates`.
     * @return The number of records updated.
     */
    fun update(): Int

    /**
     * Executes a delete operation based on the query's `where` clause.
     * @return The number of records deleted.
     */
    fun delete(): Int

    // --- Stream Functions ---

    /** Registers a listener that is invoked when a new item matching the query is added. */
    fun <T : Any> onItemAdded(listener: (T) -> Unit): IQueryBuilder

    /** Registers a listener that is invoked when a matching item is updated. */
    fun <T : Any> onItemUpdated(listener: (T) -> Unit): IQueryBuilder

    /** Registers a listener that is invoked when a matching item is deleted. */
    fun <T : Any> onItemDeleted(listener: (T) -> Unit): IQueryBuilder

    /** Registers a generic listener that is invoked for any change (add, update, delete). */
    fun <T : Any> onItem(listener: (T) -> Unit): IQueryBuilder

    /**
     * Starts a real-time data stream based on the query.
     * @param includeQueryResults If `true`, the initial set of query results will be streamed before live updates.
     * @param keepAlive If `true`, the stream will attempt to reconnect automatically on failure.
     * @return An [IStreamSubscription] to manage the stream's lifecycle.
     *
     * Example usage:
     * ```kotlin
     * val query = db.from<Message>().where("chatRoomId" eq "room-42")
     *
     * val subscription = query
     * .onItemAdded { newMessage: Message -> println("New message: ${newMessage.text}") }
     * .onItemUpdated { updatedMessage: Message -> println("Edited: ${updatedMessage.text}") }
     * .stream()
     *
     * // ... later, to stop listening
     * subscription.close()
     * ```
     */
    fun <T> stream(includeQueryResults: Boolean = false, keepAlive: Boolean = false): IStreamSubscription
}

/**
 * Sets the target table for the query using a reified type parameter.
 * This provides a more convenient, type-safe way to specify the table name.
 *
 * @param T The entity class, whose simple name will be used as the table name.
 * @return The [IQueryBuilder] instance for chaining.
 *
 * Example usage:
 * ```kotlin
 * // The table name "User" is inferred from the User class
 * val userQuery = db.from<User>().where("age" gte 18).list()
 * ```
 */
inline fun <reified T> IQueryBuilder.from(): IQueryBuilder {
    (this as? QueryBuilder)?.apply {
        this.table = T::class.simpleName!!
        this.type = T::class
    }
    return this
}

/**
 * Represents an active subscription to a real-time data stream.
 * It is [AutoCloseable], allowing it to be used in `use` blocks for automatic resource management.
 *
 * Example usage:
 * ```kotlin
 * db.from<Event>().stream().use { subscription ->
 * // The stream is active inside this block.
 * println("Listening for events...")
 * Thread.sleep(10000)
 * // subscription.close() is called automatically when the block exits.
 * }
 * println("Stream closed.")
 * ```
 */
interface IStreamSubscription : AutoCloseable {
    /** Stops the stream immediately without waiting for background tasks to complete. */
    fun cancel()

    /** Waits for the stream's background thread to finish processing and terminate. */
    fun join()

    /** A convenience method that cancels the stream and then waits for it to shut down completely. */
    fun cancelAndJoin()

    /** The last error that occurred on the stream, or `null` if it is running without errors. */
    val error: Throwable?
}

/**
 * A builder for performing cascading save or delete operations across related database tables.
 */
interface ICascadeBuilder {
    /**
     * Specifies the relationships to follow when performing the cascade operation.
     * @param relationships The names of the relationships to cascade through.
     * @return This builder for chaining.
     */
    fun cascade(vararg relationships: String): ICascadeBuilder

    /**
     * Saves a single entity or a list of entities, including any specified related entities.
     *
     * @param T The type of the entity being returned.
     * @param entityOrEntities A single entity object or a `List` of entities to save.
     * @return The saved entity or a list of saved entities.
     *
     * Example usage:
     * ```kotlin
     * val userWithPosts = User(
     * name = "John Doe",
     * posts = listOf(Post(title = "First Post"), Post(title = "Second Post"))
     * )
     * // Saves the user and all associated posts in one operation.
     * db.cascade("posts").save(userWithPosts)
     * ```
     */
    fun <T> save(entityOrEntities: Any): T

    /**
     * Deletes an entity by its primary key from the specified table.
     *
     * **Note:** The `inline reified` [delete] extension function is the preferred
     * way to call this, as it infers the table name from the type and is more type-safe.
     *
     * @param table The name of the table to delete from.
     * @param primaryKey The primary key of the entity to delete.
     * @return `true` if the deletion was successful, `false` otherwise.
     */
    fun delete(table: String, primaryKey: String): Boolean
}

/**
 * Deletes an entity by its primary key, inferring the table name from the reified type `T`.
 * This is the preferred method for deleting via cascade.
 *
 * @param T The type of the entity to delete.
 * @param primaryKey The primary key of the entity.
 * @return `true` if successful, `false` otherwise.
 *
 * Example usage:
 * ```kotlin
 * // Deletes a User with the given ID and any related entities specified in the cascade.
 * db.cascade("orders", "profile").delete<User>("user-primary-key-123")
 * ```
 */
inline fun <reified T> ICascadeBuilder.delete(primaryKey: Any) =
    this.delete(T::class.simpleName!!, primaryKey.toString())

/**
 * Configuration options for initializing the Onyx database client.
 *
 * @property baseUrl The base URL of the Onyx API endpoint.
 * @property databaseId The unique identifier for the target database.
 * @property apiKey The API key for authentication.
 * @property apiSecret The API secret for authentication.
 * @property fetch An optional custom fetch implementation for making HTTP requests.
 * @property partition The default partition to use for all queries if not otherwise specified.
 * @property requestLoggingEnabled If `true`, logs HTTP request details.
 * @property responseLoggingEnabled If `true`, logs HTTP response details.
 * @property ttl The time-to-live in milliseconds for caching resolved credentials.
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

/**
 * A minimal HTTP response interface to avoid dependencies on specific networking libraries.
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
 * Represents the parameters for an HTTP request.
 */
data class FetchInit(
    val method: String? = null,
    val headers: Map<String, String>? = null,
    val body: String? = null
)

/**
 * A typealias for the fetch function signature required by the SDK. This allows clients
 * to provide their own HTTP request implementation.
 */
typealias FetchImpl = (url: String, init: FetchInit?) -> FetchResponse

/**
 * The main interface for interacting with an Onyx database.
 * It provides methods for querying, saving, finding, and deleting data.
 *
 * @param Schema A marker type representing the database schema, used for type safety.
 */
interface IOnyxDatabase<Schema : Any> {

    /**
     * Starts a new query by selecting a set of fields.
     *
     * @param fields The fields to include in the query result. If empty, all fields are returned.
     * @return An [IQueryBuilder] to continue constructing the query.
     *
     * Example usage:
     * ```kotlin
     * // This query will only return the 'name' and 'email' fields for each user.
     * val userNamesAndEmails = db.select("name", "email").from<User>().list()
     * ```
     */
    fun select(vararg fields: String): IQueryBuilder

    /**
     * Starts a cascading operation to save or delete entities across specified relationships.
     *
     * @param relationships The names of the relationships to cascade through.
     * @return An [ICascadeBuilder] for performing the save or delete.
     *
     * Example usage:
     * ```kotlin
     * val user = User(...)
     * // Assuming 'orders' is a defined relationship for the User entity, this saves
     * // the user and all associated order objects.
     * db.cascade("orders").save(user)
     * ```
     */
    fun cascade(vararg relationships: String): ICascadeBuilder =
        CascadeBuilderImpl(this, relationships.toList())

    /**
     * Saves a single entity or a list of entities to a specified table.
     *
     * **Note:** The `inline reified` [save] extension function is the preferred
     * way to call this, as it infers the table name from the entity type and provides better type safety.
     *
     * @param T The type of the entity or entities being saved.
     * @param table The [KClass] of the entity, used to determine the table name.
     * @param entityOrEntities The single entity or `List` of entities to save.
     * @param options Additional options for the save operation.
     * @return The saved entity or list of entities, often including database-generated values like IDs.
     */
    fun <T> save(
        table: KClass<*>,
        entityOrEntities: T,
        options: SaveOptions? = null,
    ): T

    /**
     * Retrieves a single entity by its primary key from a specified table.
     *
     * **Note:** The `inline reified` [findById] extension function is the preferred
     * way to call this, as it infers the table name and return type automatically.
     *
     * @param T The expected type of the entity.
     * @param table The [KClass] of the entity to determine the table name.
     * @param primaryKey The primary key of the entity to find.
     * @param options Additional options for the find operation.
     * @return The found entity of type `T`, or `null` if it does not exist.
     */
    fun <T> findById(
        table: KClass<*>,
        primaryKey: Any,
        options: FindOptions? = null
    ): T?

    /**
     * Deletes an entity by its primary key from a specified table.
     *
     * **Note:** The `inline reified` [delete] extension function is the preferred
     * way to call this, as it infers the table name from the type.
     *
     * @param table The name of the table.
     * @param primaryKey The primary key of the entity to delete.
     * @param options Additional options for the delete operation.
     * @return `true` if the deletion was successful, `false` otherwise.
     */
    fun delete(
        table: String,
        primaryKey: String,
        options: DeleteOptions? = null
    ): Boolean

    /** Stores a document (e.g., a file blob) for later retrieval. */
    fun saveDocument(doc: OnyxDocument): OnyxDocument

    /** Fetches a previously saved document by its ID. */
    fun getDocument(documentId: String, options: DocumentOptions? = null): Any?

    /** Permanently removes a stored document. */
    fun deleteDocument(documentId: String): Any?

    /** Cancels all active streams associated with this database instance. Safe to call multiple times. */
    fun close()
}

// --- IOnyxDatabase Extension Functions ---

/**
 * Sets the target table for the query using a reified type parameter.
 * This provides a more convenient, type-safe way to specify the table name.
 *
 * @param T The entity class, whose simple name will be used as the table name.
 * @return The [IQueryBuilder] instance for chaining.
 *
 * Example usage:
 * ```kotlin
 * // The table name "User" is inferred from the User class
 * val userQuery = db.from<User>().where("age" gte 18).list()
 * ```
 */
inline fun <reified T> IOnyxDatabase<*>.from(): IQueryBuilder {
    return QueryBuilder(
        client = this as OnyxClient,
        type = T::class,
        table = T::class.simpleName!!,
    )
}

/**
 * Deletes an entity by its primary key, inferring the table name from the reified type `T`.
 * This is the preferred method for simple deletes.
 *
 * @param T The type of the entity to delete (and infer table name from).
 * @param primaryKey The primary key of the entity.
 * @return `true` if successful, `false` otherwise.
 *
 * Example usage:
 * ```kotlin
 * val success = db.delete<User>("user-primary-key-123")
 * if (success) {
 * println("User deleted.")
 * }
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.delete(primaryKey: Any): Boolean =
    this.delete(T::class.simpleName!!, primaryKey.toString())

/**
 * Deletes an entity by its primary key from a specific partition, inferring the table name from `T`.
 *
 * @param T The type of the entity to delete (and infer table name from).
 * @param primaryKey The primary key of the entity.
 * @param partition The partition to delete from.
 * @return `true` if successful, `false` otherwise.
 *
 * Example usage:
 * ```kotlin
 * val success = db.deleteInPartition<User>("user-pk-456", "partition-A")
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.deleteInPartition(primaryKey: Any, partition: Any): Boolean = this.delete(
    T::class.simpleName!!, primaryKey.toString(),
    DeleteOptions(partition = partition.toString())
)

/** Deletes an entity by its primary key from a specific table and partition. */
fun IOnyxDatabase<*>.deleteInPartition(table: String, primaryKey: Any, partition: Any): Boolean = this.delete(
    table, primaryKey.toString(),
    DeleteOptions(partition = partition.toString())
)

/**
 * Saves a single entity, inferring the table name from the entity's class.
 * This is the preferred method for saving single entities.
 *
 * @param T The type of the entity.
 * @param entity The entity to save.
 * @return The saved entity, potentially updated with database-generated values.
 *
 * Example usage:
 * ```kotlin
 * val newUser = User(name = "Jane Doe", email = "jane.doe@example.com")
 * val savedUser = db.save(newUser)
 * println("Saved user with ID: ${savedUser.id}")
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.save(entity: T): T = this.save(T::class, entity)

/**
 * Saves a list of entities in batches to improve performance and avoid request size limits.
 *
 * @param T The type of the entities.
 * @param entities The list of entities to save.
 * @param batchSize The number of entities to include in each batch request.
 *
 * Example usage:
 * ```kotlin
 * val newUsers = listOf(
 * User(name = "User A"),
 * User(name = "User B")
 * )
 * db.batchSave(newUsers, batchSize = 100)
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.batchSave(entities: List<T>, batchSize: Int = 1000) {
    entities.chunked(batchSize).forEach { chunk ->
        if (chunk.isNotEmpty()) this.save(T::class, chunk)
    }
}

/**
 * Finds an entity by its primary key, inferring the table name from the reified type `T`.
 * This is the preferred method for finding by ID.
 *
 * @param T The expected result type (and infer table name from).
 * @param id The primary key value.
 * @return The found entity, or `null`.
 *
 * Example usage:
 * ```kotlin
 * val user = db.findById<User>("user-id-789")
 * user?.let { println("Found user: ${it.name}") }
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.findById(id: Any): T? = findById(T::class, id.toString())

/**
 * Finds an entity by its primary key within a specific partition, inferring the table name from `T`.
 *
 * @param T The expected result type (and infer table name from).
 * @param primaryKey The primary key value.
 * @param partition The partition to search within.
 * @return The found entity, or `null`.
 *
 * Example usage:
 * ```kotlin
 * val product: Product? = db.findByIdInPartition<Product>(
 * primaryKey = "prod-id-123",
 * partition = "electronics"
 * )
 * ```
 */
inline fun <reified T : Any> IOnyxDatabase<*>.findByIdInPartition(primaryKey: String, partition: String): T? =
    findById(T::class, primaryKey, FindOptions(partition = partition))

// --- Operation-specific Option classes ---

/** @property relationships A list of relationship names to include in a cascading save. */
data class SaveOptions(val relationships: List<String>? = null)

/**
 * @property partition The specific partition to search within.
 * @property resolvers A list of related values to resolve and include in the result.
 */
data class FindOptions(val partition: String? = null, val resolvers: List<String>? = null)

/**
 * @property partition The partition containing the entity to be deleted.
 * @property relationships A list of relationship names to include in a cascading delete.
 */
data class DeleteOptions(val partition: String? = null, val relationships: List<String>? = null)

/**
 * @property width The desired width for image documents (the system will attempt to resize).
 * @property height The desired height for image documents.
 */
data class DocumentOptions(val width: Int? = null, val height: Int? = null)

/**
 * A facade for creating and managing database client instances.
 */
interface OnyxFacade {
    /**
     * Initializes and returns a new database client with the specified configuration.
     *
     * @param baseUrl Onyx Cloud database endpoint defaulted to onyx cloud api
     * @param databaseId Database identifier for the database in which you want to connect to
     * @param apiSecret Database ApiKey secret
     * @param apiKey Database ApiKey key
     */
    fun init(
        baseUrl: String = "https://api.onyx.dev",
        databaseId: String,
        apiKey: String,
        apiSecret: String
    ): IOnyxDatabase<Any>

    /**
     * Initializes and returns a new database client with the specified configuration.
     *
     * @param Schema A marker type representing the database schema.
     * @param config The [OnyxConfig] settings for the database connection.
     * @return An initialized [IOnyxDatabase] client.
     */
    fun <Schema : Any> init(config: OnyxConfig? = null): IOnyxDatabase<Schema>

    /**
     * Clears any cached configuration or credentials. The next call to [init] will
     * re-resolve all settings.
     */
    fun clearCacheConfig()
}

// --- Query Condition and Sort Helpers ---

/** Creates an ascending sort instruction for a given field. */
fun asc(field: String): Sort = Sort(field, SortOrder.ASC)

/** Creates a descending sort instruction for a given field. */
fun desc(field: String): Sort = Sort(field, SortOrder.DESC)

/** Creates an equality (`=`) condition. */
infix fun String.eq(value: Any?): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.EQUAL, value))

/** Creates an inequality (`!=`) condition. */
infix fun String.neq(value: Any?): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_EQUAL, value))

/** Creates an `IN (...)` condition. */
infix fun String.inOp(values: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.IN, values))

/** Creates a `NOT IN (...)` condition. */
infix fun String.notIn(values: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_IN, values))

/** Creates a `BETWEEN x AND y` condition using a [Pair]. */
infix fun <T : Any> String.between(bounds: Pair<T, T>): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(bounds.first, bounds.second)))

/** Creates a `BETWEEN x AND y` condition using two arguments. */
fun <T : Any> String.between(from: T, to: T): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.BETWEEN, listOf(from, to)))

/** Creates a greater than (`>`) condition. */
infix fun String.gt(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN, value))

/** Creates a greater than or equal to (`>=`) condition. */
infix fun String.gte(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.GREATER_THAN_EQUAL, value))

/** Creates a less than (`<`) condition. */
infix fun String.lt(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN, value))

/** Creates a less than or equal to (`<=`) condition. */
infix fun String.lte(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LESS_THAN_EQUAL, value))

/** Creates a regular expression `MATCHES` condition. */
infix fun String.matches(regex: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.MATCHES, regex))

/** Creates a negative regular expression `NOT MATCHES` condition. */
infix fun String.notMatches(regex: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_MATCHES, regex))

/** Creates a `LIKE` condition (supports wildcards like `%` and `_`). */
infix fun String.like(pattern: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.LIKE, pattern))

/** Creates a `NOT LIKE` condition. */
infix fun String.notLike(pattern: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_LIKE, pattern))

/** Creates a `CONTAINS` condition (case-sensitive). */
infix fun String.contains(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.CONTAINS, value))

/** Creates a `CONTAINS` condition (case-insensitive). */
infix fun String.containsIgnoreCase(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.CONTAINS_IGNORE_CASE, value))

/** Creates a `NOT CONTAINS` condition (case-sensitive). */
infix fun String.notContains(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS, value))

/** Creates a `NOT CONTAINS` condition (case-insensitive). */
infix fun String.notContainsIgnoreCase(value: Any): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, value))

/** Creates a `STARTS WITH` condition. */
infix fun String.startsWith(prefix: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.STARTS_WITH, prefix))

/** Creates a `NOT STARTS WITH` condition. */
infix fun String.notStartsWith(prefix: String): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_STARTS_WITH, prefix))

/** Creates an `IS NULL` condition. */
fun String.isNull(): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.IS_NULL, null))

/** Creates an `IS NOT NULL` condition. */
fun String.notNull(): IConditionBuilder =
    ConditionBuilderImpl(QueryCriteria(this, QueryCriteriaOperator.NOT_NULL, null))

// --- Aggregation and Transform Function Helpers ---

/** Helper to create an `avg(attribute)` string for use in `select` clauses. */
fun avg(attribute: String): String = "avg($attribute)"

/** Helper to create a `sum(attribute)` string for use in `select` clauses. */
fun sum(attribute: String): String = "sum($attribute)"

/** Helper to create a `count(attribute)` string for use in `select` clauses. */
fun count(attribute: String): String = "count($attribute)"

/** Helper to create a `min(attribute)` string for use in `select` clauses. */
fun min(attribute: String): String = "min($attribute)"

/** Helper to create a `max(attribute)` string for use in `select` clauses. */
fun max(attribute: String): String = "max($attribute)"

/** Helper to create a `std(attribute)` string for use in `select` clauses. */
fun std(attribute: String): String = "std($attribute)"

/** Helper to create a `variance(attribute)` string for use in `select` clauses. */
fun variance(attribute: String): String = "variance($attribute)"

/** Helper to create a `median(attribute)` string for use in `select` clauses. */
fun median(attribute: String): String = "median($attribute)"

/** Helper to create an `upper(attribute)` string for transforming text to uppercase. */
fun upper(attribute: String): String = "upper($attribute)"

/** Helper to create a `lower(attribute)` string for transforming text to lowercase. */
fun lower(attribute: String): String = "lower($attribute)"

/** Helper to create a `substring(attribute, from, length)` string. */
fun substring(attribute: String, from: Int, length: Int): String =
    "substring($attribute,$from,$length)"

/** Helper to create a `replace(attribute, pattern, replacement)` string. */
fun replace(attribute: String, pattern: String, replacement: String): String =
    "replace($attribute,$pattern,$replacement)"

/** Helper to create a `percentile(attribute, p)` string for statistical analysis. */
fun percentile(attribute: String, p: Number): String = "percentile($attribute,$p)"