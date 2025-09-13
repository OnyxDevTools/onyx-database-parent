@file:Suppress("UNCHECKED_CAST")

package com.onyx.cloud.api

import java.math.BigInteger
import kotlin.concurrent.thread
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Array-like container for paginated query results. Provides helper
 * functions to traverse and aggregate records across pages.
 */
class QueryResultsImpl<T : Any>(
    records: Iterable<T>,
    override var nextPage: String? = null,
    private val fetcher: ((String) -> QueryResultsImpl<T>)? = null
) : ArrayList<T>(records.toList()), QueryResults<T> {

    /** Returns the first record or throws when empty. */
    override fun first(): T = firstOrNull() ?: error("No records found")

    /** Returns the first record or `null` if empty. */
    override fun firstOrNull(): T? = this.getOrNull(0)

    /** Iterates over each record on the current page only. */
    override fun forEachOnPage(action: (T) -> Unit) = forEach(action)

    /** Iterates over every record across all pages sequentially. */
    override fun forEachAll(action: (T) -> Boolean?) {
        var stop = false
        forEachPage { page ->
            for (item in page) {
                if (action(item) == false) {
                    stop = true
                    break
                }
            }
            !stop
        }
    }

    /** Iterates page by page across the result set. */
    override fun forEachPage(action: (List<T>) -> Boolean?) {
        var current: QueryResultsImpl<T>? = this
        var continueLoop = true
        while (current != null && continueLoop) {
            continueLoop = action(current) != false
            current = if (continueLoop && current.nextPage != null && fetcher != null) {
                fetcher.invoke(current.nextPage!!)
            } else {
                null
            }
        }
    }

    /** Collects all records from every page into a single list. */
    override fun getAllRecords(): List<T> {
        val out = mutableListOf<T>()
        forEachPage { page -> out.addAll(page) }
        return out
    }

    /** Filters all records using the provided predicate. */
    override fun filterAll(predicate: (T) -> Boolean): List<T> =
        getAllRecords().filter(predicate)

    /** Maps all records using the provided transform. */
    override fun <R> mapAll(transform: (T) -> R): List<R> =
        getAllRecords().map(transform)

    /** Extracts values for a field across all records. */
    override fun values(field: String): List<Any?> =
        getAllRecords().map { record ->
            when (record) {
                is Map<*, *> -> record[field]
                else -> record::class.memberProperties.firstOrNull { it.name == field }
                    ?.let { (it as KProperty1<Any, *>).get(record as Any) }
            }
        }

    /** Maximum value produced by the selector across all records. */
    override fun maxOfDouble(selector: (T) -> Double): Double =
        mapAll(selector).maxOrNull() ?: Double.NaN

    /** Minimum value produced by the selector across all records. */
    override fun minOfDouble(selector: (T) -> Double): Double =
        mapAll(selector).minOrNull() ?: Double.NaN

    /** Sum of values produced by the selector across all records. */
    override fun sumOfDouble(selector: (T) -> Double): Double =
        mapAll(selector).sum()

    /** Maximum float value from the selector. */
    override fun maxOfFloat(selector: (T) -> Float): Float =
        mapAll { selector(it) }.maxOrNull() ?: Float.NaN

    /** Minimum float value from the selector. */
    override fun minOfFloat(selector: (T) -> Float): Float =
        mapAll { selector(it) }.minOrNull() ?: Float.NaN

    /** Sum of float values from the selector. */
    override fun sumOfFloat(selector: (T) -> Float): Float =
        mapAll { selector(it) }.sum()

    /** Maximum integer value from the selector. */
    override fun maxOfInt(selector: (T) -> Int): Int =
        mapAll { selector(it) }.maxOrNull() ?: 0

    /** Minimum integer value from the selector. */
    override fun minOfInt(selector: (T) -> Int): Int =
        mapAll { selector(it) }.minOrNull() ?: 0

    /** Sum of integer values from the selector. */
    override fun sumOfInt(selector: (T) -> Int): Int =
        mapAll { selector(it) }.sum()

    /** Maximum long value from the selector. */
    override fun maxOfLong(selector: (T) -> Long): Long =
        mapAll { selector(it) }.maxOrNull() ?: 0L

    /** Minimum long value from the selector. */
    override fun minOfLong(selector: (T) -> Long): Long =
        mapAll { selector(it) }.minOrNull() ?: 0L

    /** Sum of long values from the selector. */
    override fun sumOfLong(selector: (T) -> Long): Long =
        mapAll { selector(it) }.sum()

    /** Sum of bigint values from the selector. */
    override fun sumOfBigInt(selector: (T) -> BigInteger): BigInteger =
        mapAll(selector).fold(BigInteger.ZERO) { acc, v -> acc + v }

    /** Executes an action for each page in parallel. */
    override fun forEachPageParallel(action: (T) -> Unit) {
        forEachPage { page ->
            val threads = page.map { item ->
                thread(start = true) { action(item) }
            }
            threads.forEach { it.join() }
            true
        }
    }
}
