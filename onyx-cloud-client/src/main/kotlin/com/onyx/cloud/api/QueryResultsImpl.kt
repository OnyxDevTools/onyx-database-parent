@file:Suppress("UNCHECKED_CAST")

package com.onyx.cloud.api

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.onyx.cloud.QueryBuilder
import com.onyx.cloud.extensions.fromJsonList
import com.onyx.cloud.extensions.get
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.Executors
import kotlin.reflect.KClass

data class QueryResultsImpl<T : Any>(
    @SerializedName("records")
    internal val recordText: JsonArray = JsonArray(),

    override val nextPage: String? = null,

    @SerializedName(value = "totalResults", alternate = ["totalRecords"])
    val totalResults: Int = 0,
) : List<T>, IQueryResults<T> {

    @Transient internal var query: QueryBuilder? = null
    @Transient var classType: KClass<*>? = null

    /** Current page records (typed). */
    val records: List<T> by lazy {
        try {
            val javaType = classType?.java ?: return@lazy emptyList<T>()
            recordText.toString().fromJsonList(javaType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** List<T> delegation to the current page */
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

    /** First record on the current page. */
    override fun first(): T = records.first()

    /** First record or null when empty. */
    override fun firstOrNull(): T? = records.firstOrNull()

    override fun forEachAll(action: (T) -> Boolean) = forEach(action)

    /** Iterates over current page records. */
    override fun forEachOnPage(action: (T) -> Unit) {
        records.forEach(action)
    }

    /** Iterates across all pages and records until action returns false. */
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

    /** Iterates page-by-page until the action returns false. */
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

    /** Loads all pages and returns all records as a list. */
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

    /** Values across ALL pages for a field name using your extension getter. */
    override fun values(field: String): List<Any?> = getAllRecords().map { it.get(field) }

    /** Aggregations across ALL pages. */
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

    fun sumOfBigDecimal(selector: (T) -> BigDecimal): BigDecimal =
        getAllRecords().map(selector).fold(BigDecimal.ZERO, BigDecimal::add)

    /** Page-parallel helper (unchanged). */
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
