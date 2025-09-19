package com.onyx.cloud.impl

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import com.onyx.cloud.api.IQueryResults
import com.onyx.cloud.extensions.fromJsonList
import com.onyx.cloud.extensions.get
import java.math.BigInteger
import java.util.concurrent.Executors
import kotlin.reflect.KClass

/**
 * Concrete [IQueryResults] implementation backed by the raw JSON payload returned by the Onyx API.
 *
 * The class keeps the original [JsonArray] so that records can be lazily materialised into typed
 * instances when required. It also offers a suite of helper functions to iterate across paginated
 * responses and to aggregate values across all pages.
 *
 * @param recordText raw JSON array representing the current page of results.
 * @param nextPage pagination token identifying the subsequent page, or `null` when none is available.
 * @param totalResults total number of results reported by the API, if provided.
 */
data class QueryResultsImpl<T : Any>(
    @SerializedName("records")
    internal val recordText: JsonArray = JsonArray(),

    override val nextPage: String? = null,

    @SerializedName(value = "totalResults", alternate = ["totalRecords"])
    val totalResults: Int = 0,
) : List<T>, IQueryResults<T> {

    @Transient internal var query: QueryBuilder? = null
    @Transient var classType: KClass<*>? = null

    /**
     * Lazily materialised view of the records contained on the current page.
     *
     * JSON deserialisation is deferred until the first access in order to avoid unnecessary parsing
     * when only pagination metadata is required.
     */
    val records: List<T> by lazy {
        try {
            val javaType = classType?.java ?: return@lazy emptyList<T>()
            recordText.toString().fromJsonList(javaType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** [List] delegation to the current page. */
    override val size: Int get() = records.size
    override fun isEmpty(): Boolean = records.isEmpty()
    override fun iterator(): Iterator<T> = records.iterator()
    override fun get(index: Int): T = records[index]
    override fun contains(element: T): Boolean = records.contains(element)
    override fun containsAll(elements: Collection<T>): Boolean = records.containsAll(elements)
    override fun indexOf(element: T): Int = records.indexOf(element)
    override fun lastIndexOf(element: T): Int = records.lastIndexOf(element)
    override fun listIterator(): ListIterator<T> = records.listIterator()
    override fun listIterator(index: Int): ListIterator<T> = records.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = records.subList(fromIndex, toIndex)

    /** Returns the first record on the current page. */
    override fun first(): T = records.first()

    /** Returns the first record on the current page, or `null` when the page is empty. */
    override fun firstOrNull(): T? = records.firstOrNull()

    override fun forEachAll(action: (T) -> Boolean) = forEach(action)

    /**
     * Invokes [action] for every record on the current page.
     *
     * @param action callback executed for each record.
     */
    override fun forEachOnPage(action: (T) -> Unit) {
        records.forEach(action)
    }

    /**
     * Iterates across every page until [action] returns `false`.
     *
     * @param action callback executed for each record; return `false` to abort iteration.
     */
    fun forEach(action: (T) -> Boolean) {
        var keepGoing = true
        forEachPage { page ->
            for (item in page) {
                if (!action(item)) {
                    keepGoing = false
                    break
                }
            }
            keepGoing
        }
    }

    /**
     * Iterates over result pages sequentially, invoking [action] for each page until it returns `false`.
     *
     * @param action callback receiving the records from the current page; return `false` to stop paging.
     */
    override fun forEachPage(action: (pageRecords: List<T>) -> Boolean) {
        var current: QueryResultsImpl<T>? = this
        val q = query ?: error("Query context is missing for pagination.")
        var keepPaging = true

        while (current != null) {
            val page = current.records
            if (page.isNotEmpty()) {
                keepPaging = action(page)
            }
            current = if (keepPaging && !current.nextPage.isNullOrBlank()) {
                q.nextPage(current.nextPage).list<T>() as? QueryResultsImpl<T>
            } else null
        }
    }

    /**
     * Loads all available pages and returns the combined records as a list.
     *
     * @return a list containing every record across all pages.
     */
    override fun getAllRecords(): List<T> {
        val all = mutableListOf<T>()
        forEachPage { page ->
            all.addAll(page)
            true
        }
        return all
    }

    override fun filterAll(predicate: (T) -> Boolean): List<T> = getAllRecords().filter(predicate)
    override fun <R> mapAll(transform: (T) -> R): List<R> = getAllRecords().map(transform)

    /**
     * Retrieves values for the provided [field] from every record on every page.
     *
     * @param field name of the property to extract from each record.
     * @return a list containing the extracted values.
     */
    override fun values(field: String): List<Any?> = getAllRecords().map { it.get(field) }

    /** Aggregations across all pages. */
    override fun maxOfDouble(selector: (T) -> Double): Double = getAllRecords().maxOfOrNull(selector) ?: Double.NaN
    override fun minOfDouble(selector: (T) -> Double): Double = getAllRecords().minOfOrNull(selector) ?: Double.NaN
    override fun sumOfDouble(selector: (T) -> Double): Double = getAllRecords().sumOf(selector)

    override fun maxOfFloat(selector: (T) -> Float): Float = getAllRecords().maxOfOrNull(selector) ?: Float.NaN
    override fun minOfFloat(selector: (T) -> Float): Float = getAllRecords().minOfOrNull(selector) ?: Float.NaN
    override fun sumOfFloat(selector: (T) -> Float): Float =
        getAllRecords().sumOf { selector(it).toDouble() }.toFloat()

    override fun maxOfInt(selector: (T) -> Int): Int = getAllRecords().maxOfOrNull(selector) ?: Int.MIN_VALUE
    override fun minOfInt(selector: (T) -> Int): Int = getAllRecords().minOfOrNull(selector) ?: Int.MAX_VALUE
    override fun sumOfInt(selector: (T) -> Int): Int = getAllRecords().sumOf(selector)

    override fun maxOfLong(selector: (T) -> Long): Long = getAllRecords().maxOfOrNull(selector) ?: Long.MIN_VALUE
    override fun minOfLong(selector: (T) -> Long): Long = getAllRecords().minOfOrNull(selector) ?: Long.MAX_VALUE
    override fun sumOfLong(selector: (T) -> Long): Long = getAllRecords().sumOf(selector)

    override fun sumOfBigInt(selector: (T) -> BigInteger): BigInteger =
        getAllRecords().map(selector).fold(BigInteger.ZERO, BigInteger::add)

    /**
     * Iterates over records by page, processing each page in parallel while fetching subsequent pages.
     *
     * @param action callback executed for every record in the result set.
     */
    override fun forEachPageParallel(action: (T) -> Unit) {
        val fetchExecutor = Executors.newSingleThreadExecutor()
        var current: QueryResultsImpl<T>? = this
        val q = query ?: error("Query context is missing for pagination.")
        try {
            while (current != null) {
                val page = current.records
                val next = current.nextPage
                if (page.isNotEmpty()) {
                    page.parallelStream().forEach(action)
                }
                val future = if (!next.isNullOrBlank()) {
                    fetchExecutor.submit<IQueryResults<T>?> {
                        try { q.nextPage(next).list<T>() } catch (_: Exception) { null }
                    }
                } else null
                current = future?.get() as? QueryResultsImpl<T>
            }
        } finally {
            fetchExecutor.shutdown()
        }
    }
}