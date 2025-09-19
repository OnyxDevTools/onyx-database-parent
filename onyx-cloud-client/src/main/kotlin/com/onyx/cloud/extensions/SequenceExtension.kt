package com.onyx.cloud.extensions

import com.onyx.cloud.api.IQueryBuilder
import com.onyx.cloud.api.IQueryResults
import com.onyx.cloud.impl.QueryResultsImpl
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Streams the entire result set as a [Sequence], prefetching pages asynchronously when possible.
 *
 * The returned sequence executes the supplied [filter] and [transform] lazily. While processing a page,
 * it preloads the next page on the provided [executor]; iteration blocks at page boundaries if the next
 * page has not completed loading.
 *
 * @receiver the original paginated results to iterate over.
 * @param executor executor used to prefetch the next page; defaults to a single-thread executor.
 * @param filter predicate applied before the transformation step.
 * @param transform projection applied to each filtered record before yielding.
 * @return a lazily-evaluated [Sequence] streaming the transformed records from all pages.
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
/**
 * Submits an asynchronous request that fetches the page identified by [token].
 *
 * @param executor executor responsible for running the request.
 * @param qb query builder used to retrieve the next page.
 * @param token pagination token representing the desired page.
 * @return a [Future] that resolves to the fetched [IQueryResults] or `null` if retrieval fails.
 */
private fun <T : Any> submitFetch(
    executor: ExecutorService,
    qb: IQueryBuilder,
    token: String,
): Future<IQueryResults<T>?> =
    executor.submit(Callable { qb.nextPage(token).list() })
