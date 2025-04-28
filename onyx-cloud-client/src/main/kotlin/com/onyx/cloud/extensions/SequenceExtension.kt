package com.onyx.cloud.extensions

import com.onyx.cloud.QueryBuilder
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.reflect.KClass

/**
 * Returns a Sequence that streams every record in every page,
 * prefetching the next page on [executor] while you consume the current one.
 *
 * ⚠️  The sequence WILL block at page boundaries if the next page isn’t ready yet.
 */
@Suppress("unused")
fun <T : Any, R> QueryBuilder.QueryResults<T>.asSequence(
    executor: ExecutorService = Executors.newSingleThreadExecutor(),
    filter: (T) -> Boolean = { true },
    transform: (T) -> R = {
        @Suppress("UNCHECKED_CAST")
        it as R
    },
): Sequence<R> = sequence {
    var current: QueryBuilder.QueryResults<T>? = this@asSequence

    val qb = current!!.query ?: error("Missing query context")

    @Suppress("UNCHECKED_CAST")
    val clazz = current.classType as? KClass<T>
        ?: error("Missing record type")

    var nextFuture: Future<QueryBuilder.QueryResults<T>?>? =
        current.nextPage?.let { token -> submitFetch(executor, qb, clazz, token) }

    while (current != null) {
        // 1) yield every record in the current page
        current.records
            .asSequence()
            .filter(filter)
            .map(transform)
            .forEach { value -> yield(value) }

        // 2) wait for (or throw) the prefetched page
        current = nextFuture?.let { f ->
            try {
                f.get()
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ie
            } catch (ee: ExecutionException) {
                throw (ee.cause ?: ee)
            }
        }

        // 3) kick off the fetch for page N+2
        nextFuture = current
            ?.nextPage
            ?.let { token -> submitFetch(executor, qb, clazz, token) }
    }
}

/* ───────────────────────── helper ───────────────────────── */
private fun <T : Any> submitFetch(
    executor: ExecutorService,
    qb: QueryBuilder,
    clazz: KClass<T>,
    token: String,
): Future<QueryBuilder.QueryResults<T>?> =
    executor.submit(Callable { qb.nextPage(token).list(clazz) })
