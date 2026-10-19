package com.onyx.extension.common

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val queryRowProcessor = ParallelRowProcessor(Runtime.getRuntime().availableProcessors())

// Keep the public inline extension's bytecode independent of the implementation.
@PublishedApi
internal fun <A> processRows(rows: Collection<A>, block: (A) -> Unit) =
    queryRowProcessor.forEach(rows, block)

/**
 * At most [parallelism] background workers exist across concurrent traversals.
 * Admission never waits: a caller unable to reserve a worker processes its own
 * rows. This also prevents starvation when a callback waits for async work which
 * in turn collects rows. Directly nested traversals always execute inline.
 *
 * Worker tasks claim small chunks from one iterator, supporting sets and linked
 * collections without indexed access or a full additional copy. Each traversal
 * submits at most [parallelism] tasks, rather than one Future per row.
 */
internal class ParallelRowProcessor(
    private val parallelism: Int,
    private val chunkSize: Int = 256
) : AutoCloseable {
    init {
        require(parallelism > 0)
        require(chunkSize > 0)
    }

    private val permits = Semaphore(parallelism)
    private val processing = ThreadLocal<Boolean>()
    private val workerNumber = AtomicInteger()
    private val executor = Executors.newFixedThreadPool(parallelism) { task ->
        Thread(task, "onyx-query-row-${workerNumber.incrementAndGet()}").apply { isDaemon = true }
    }

    fun <A> forEach(rows: Collection<A>, block: (A) -> Unit) {
        if (rows.isEmpty()) return
        val traversal = Traversal(rows.iterator(), block)
        val completions = ArrayList<CountDownLatch>()
        if (processing.get() != true && parallelism > 1) {
            // Leave one chunk for the caller. Do not wait for capacity held by
            // other queries, including queries waiting on this invocation.
            val workers = minOf(parallelism, (rows.size - 1) / chunkSize)
            repeat(workers) {
                if (!permits.tryAcquire()) return@repeat
                val completed = CountDownLatch(1)
                try {
                    executor.execute {
                        try {
                            process(traversal)
                        } finally {
                            permits.release()
                            completed.countDown()
                        }
                    }
                    completions.add(completed)
                } catch (failure: Throwable) {
                    permits.release()
                    traversal.fail(failure)
                }
            }
        }

        process(traversal)

        // Future.cancel/get can report completion while its callback is still
        // running. Use actual task-exit latches and drain even after interruption
        // so a failed query cannot keep mutating its collector after returning.
        var interruption: InterruptedException? = if (Thread.interrupted()) {
            InterruptedException("Interrupted while collecting query rows").also(traversal::fail)
        } else null
        completions.forEach { completed ->
            while (true) {
                try {
                    completed.await()
                    break
                } catch (interrupted: InterruptedException) {
                    if (interruption == null) interruption = interrupted
                    traversal.fail(interrupted)
                }
            }
        }
        if (interruption != null) {
            Thread.currentThread().interrupt()
            throw interruption!!
        }
        traversal.failure.get()?.let { throw ExecutionException(it) }
    }

    private fun <A> process(traversal: Traversal<A>) {
        val previous = processing.get()
        processing.set(true)
        try {
            traversal.process()
        } finally {
            if (previous == null) processing.remove() else processing.set(previous)
        }
    }

    override fun close() {
        executor.shutdown()
    }

    private inner class Traversal<A>(private val iterator: Iterator<A>, private val block: (A) -> Unit) {
        val failure = AtomicReference<Throwable>()

        fun fail(cause: Throwable) {
            failure.compareAndSet(null, cause)
        }

        private fun nextChunk(): List<A> = synchronized(iterator) {
            if (failure.get() != null) return@synchronized emptyList()
            val chunk = ArrayList<A>(chunkSize)
            while (chunk.size < chunkSize && iterator.hasNext()) chunk.add(iterator.next())
            chunk
        }

        fun process() {
            try {
                while (failure.get() == null) {
                    val chunk = nextChunk()
                    if (chunk.isEmpty()) return
                    for (row in chunk) {
                        if (failure.get() != null) return
                        if (Thread.currentThread().isInterrupted) {
                            fail(InterruptedException("Interrupted while collecting query rows"))
                            return
                        }
                        block(row)
                    }
                }
            } catch (cause: Throwable) {
                fail(cause)
            }
        }
    }
}
