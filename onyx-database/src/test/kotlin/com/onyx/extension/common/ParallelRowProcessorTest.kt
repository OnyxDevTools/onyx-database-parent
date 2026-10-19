package com.onyx.extension.common

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParallelRowProcessorTest {
    @Test
    fun `linked set rows are processed exactly once across chunk boundaries`() {
        ParallelRowProcessor(2, 31).use { processor ->
            val rows = (0 until 8_193).toCollection(LinkedHashSet())
            val visits = AtomicIntegerArray(rows.size)
            val workers = ConcurrentHashMap.newKeySet<String>()
            processor.forEach(rows) {
                visits.incrementAndGet(it)
                workers.add(Thread.currentThread().name)
            }
            rows.forEach { assertEquals(1, visits.get(it), "row $it") }
            assertTrue(workers.size <= 3, "Two workers plus caller: $workers")
        }
    }

    @Test
    fun `single processor and empty collection need no background workers`() {
        ParallelRowProcessor(1, 1).use { processor ->
            val caller = Thread.currentThread()
            val seen = ArrayList<Int>()
            processor.forEach(emptyList<Int>()) { error("Empty collection callback") }
            processor.forEach((0 until 101).toList()) {
                assertSame(caller, Thread.currentThread())
                seen.add(it)
            }
            assertEquals((0 until 101).toList(), seen)
        }
    }

    @Test
    fun `directly nested collections run inline and complete`() {
        ParallelRowProcessor(2, 1).use { processor ->
            val calls = AtomicInteger()
            processor.forEach((0 until 32).toList()) {
                val outerThread = Thread.currentThread()
                processor.forEach((0 until 17).toList()) {
                    assertSame(outerThread, Thread.currentThread())
                    calls.incrementAndGet()
                }
            }
            assertEquals(32 * 17, calls.get())
        }
    }

    @Test
    fun `saturated workers can wait on async tasks that collect rows`() {
        ParallelRowProcessor(2, 1).use { processor ->
            val allWorkersActive = CyclicBarrier(3)
            val calls = AtomicInteger()
            val outer = async {
                processor.forEach(listOf(0, 1, 2)) {
                    allWorkersActive.await(10, TimeUnit.SECONDS)
                    async {
                        assertTrue(Thread.currentThread().isDaemon)
                        assertFalse(Thread.currentThread().isVirtual)
                        processor.forEach((0 until 128).toList()) { calls.incrementAndGet() }
                    }.get(10, TimeUnit.SECONDS)
                }
            }
            outer.get(15, TimeUnit.SECONDS)
            assertEquals(3 * 128, calls.get())
        }
    }

    @Test
    fun `concurrent queries share the worker bound and preserve every result`() {
        ParallelRowProcessor(2, 13).use { processor ->
            val callers = Executors.newFixedThreadPool(8)
            try {
                val start = CountDownLatch(1)
                val workerNames = ConcurrentHashMap.newKeySet<String>()
                val queries = (0 until 8).map { query ->
                    callers.submit {
                        start.await()
                        val results = Collections.synchronizedList(ArrayList<Int>())
                        processor.forEach((0 until 2_049).toList()) {
                            if (Thread.currentThread().name.startsWith("onyx-query-row-")) {
                                workerNames.add(Thread.currentThread().name)
                            }
                            results.add(query * 10_000 + it)
                        }
                        assertEquals((0 until 2_049).map { query * 10_000 + it }, results.sorted())
                    }
                }
                start.countDown()
                queries.forEach { it.get(15, TimeUnit.SECONDS) }
                assertTrue(workerNames.size <= 2, "Worker bound exceeded: $workerNames")
            } finally {
                callers.shutdownNow()
            }
        }
    }

    @Test
    fun `failure waits for callbacks already in progress before throwing`() {
        ParallelRowProcessor(2, 1).use { processor ->
            val workerStarted = CountDownLatch(1)
            val releaseWorker = CountDownLatch(1)
            val callerFailed = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val failure = IllegalStateException("collector failure")
            val observed = AtomicReference<Throwable>()
            val completedCallbacks = AtomicInteger()
            val caller = Thread {
                try {
                    processor.forEach(listOf(0, 1)) {
                        if (Thread.currentThread().name.startsWith("onyx-query-row-")) {
                            workerStarted.countDown()
                            assertTrue(releaseWorker.await(10, TimeUnit.SECONDS))
                            completedCallbacks.incrementAndGet()
                        } else {
                            assertTrue(workerStarted.await(10, TimeUnit.SECONDS))
                            callerFailed.countDown()
                            throw failure
                        }
                    }
                } catch (caught: Throwable) {
                    observed.set(caught)
                } finally {
                    finished.countDown()
                }
            }
            caller.start()
            try {
                assertTrue(callerFailed.await(10, TimeUnit.SECONDS))
                assertFalse(finished.await(50, TimeUnit.MILLISECONDS))
            } finally {
                releaseWorker.countDown()
            }
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            assertSame(failure, assertIs<ExecutionException>(observed.get()).cause)
            assertEquals(1, completedCallbacks.get())
        }
    }

    @Test
    fun `interrupting a waiting caller drains callbacks and preserves interruption`() {
        ParallelRowProcessor(2, 1).use { processor ->
            val workerStarted = CountDownLatch(1)
            val releaseWorker = CountDownLatch(1)
            val callerReady = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val observed = AtomicReference<Throwable>()
            val interruptPreserved = AtomicReference<Boolean>()
            val completedCallbacks = AtomicInteger()
            val caller = Thread {
                try {
                    processor.forEach(listOf(0, 1)) {
                        if (Thread.currentThread().name.startsWith("onyx-query-row-")) {
                            workerStarted.countDown()
                            assertTrue(releaseWorker.await(10, TimeUnit.SECONDS))
                            completedCallbacks.incrementAndGet()
                        } else {
                            assertTrue(workerStarted.await(10, TimeUnit.SECONDS))
                            callerReady.countDown()
                        }
                    }
                } catch (caught: Throwable) {
                    observed.set(caught)
                    interruptPreserved.set(Thread.currentThread().isInterrupted)
                } finally {
                    finished.countDown()
                }
            }
            caller.start()
            try {
                assertTrue(callerReady.await(10, TimeUnit.SECONDS))
                caller.interrupt()
                assertFalse(finished.await(50, TimeUnit.MILLISECONDS))
            } finally {
                releaseWorker.countDown()
            }
            assertTrue(finished.await(10, TimeUnit.SECONDS))
            assertIs<InterruptedException>(observed.get())
            assertEquals(true, interruptPreserved.get())
            assertEquals(1, completedCallbacks.get())
        }
    }

    @Test
    fun `callback exceptions retain Future get wrapping even in sequential fallback`() {
        ParallelRowProcessor(1).use { processor ->
            val failure = IllegalArgumentException("invalid row")
            val caught = assertFailsWith<ExecutionException> {
                processor.forEach(listOf(1)) { throw failure }
            }
            assertSame(failure, caught.cause)
        }
    }

    @Test
    fun `nested general async operations do not wait for a fixed pool worker`() {
        fun nested(depth: Int): Int = if (depth == 0) 1 else async { nested(depth - 1) + 1 }.get(10, TimeUnit.SECONDS)
        val tasks = (0 until 8).map { async { nested(6) } }
        tasks.forEach { assertEquals(7, it.get(15, TimeUnit.SECONDS)) }
    }
}
