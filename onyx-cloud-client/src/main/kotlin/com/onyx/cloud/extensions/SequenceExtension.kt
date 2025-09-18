package com.onyx.cloud.extensions

import com.onyx.cloud.api.IQueryBuilder
import com.onyx.cloud.api.IQueryResults
import com.onyx.cloud.api.QueryResultsImpl
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Returns a Sequence that streams every record in every page,
 * prefetching the next page on [executor] while you consume the current one.
 *
 * ⚠️  The sequence WILL block at page boundaries if the next page isn’t ready yet.
 */
@Suppress("unused")
fun <T : Any, R> IQueryResults<T>.asSequence(
    executor: ExecutorService = Executors.newSingleThreadExecutor(),
    filter: (T) -> Boolean = { true },
    transform: (T) -> R = {
        @Suppress("UNCHECKED_CAST")
        it as R
    },
): Sequence<R> = sequence {
    var current: IQueryResults<T>? = this@asSequence

    val qb = (current!! as QueryResultsImpl).query ?: error("Missing query context")

    var nextFuture: Future<IQueryResults<T>?>? =
        current.nextPage?.let { token -> submitFetch(executor, qb, token) }

    while (current != null) {
        // 1) yield every record in the current page
        (current as QueryResultsImpl).records
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
            ?.let { token -> submitFetch(executor, qb, token) }
    }
}

/* ───────────────────────── helper ───────────────────────── */
private fun <T : Any> submitFetch(
    executor: ExecutorService,
    qb: IQueryBuilder,
    token: String,
): Future<IQueryResults<T>?> =
    executor.submit(Callable { qb.nextPage(token).list() })
