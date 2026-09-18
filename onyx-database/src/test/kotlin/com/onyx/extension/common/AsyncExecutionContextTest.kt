package com.onyx.extension.common

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AsyncExecutionContextTest {
    @Test
    fun `custom executor captures submission context and restores worker context`() {
        val local = AsyncExecutionContext.register(ThreadLocal<String>())
        val executor = Executors.newSingleThreadExecutor()
        val workerReady = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        try {
            executor.submit {
                local.set("worker")
                workerReady.countDown()
                check(releaseWorker.await(5, TimeUnit.SECONDS))
            }
            assertTrue(workerReady.await(5, TimeUnit.SECONDS))
            local.set("submitted")
            val result = async(executor) { local.get() }
            local.set("changed after submission")
            releaseWorker.countDown()

            assertEquals("submitted", result.get(5, TimeUnit.SECONDS))
            assertEquals("worker", executor.submit<String> { local.get() }.get(5, TimeUnit.SECONDS))
            assertEquals("changed after submission", local.get())
        } finally {
            local.remove()
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `default pool propagates nested tasks and preserves the submitting thread`() {
        val manager = AsyncExecutionContext.register(ThreadLocal<String>())
        val auth = AsyncExecutionContext.register(ThreadLocal<String>())
        try {
            manager.set("database A")
            auth.set("credential A")
            val result = async {
                assertEquals("database A", manager.get())
                assertEquals("credential A", auth.get())
                manager.set("database B")
                auth.set("credential B")
                val nested = async { manager.get() to auth.get() }.get(5, TimeUnit.SECONDS)
                assertEquals("database B", manager.get())
                nested
            }.get(5, TimeUnit.SECONDS)

            assertEquals("database B" to "credential B", result)
            assertEquals("database A", manager.get())
            assertEquals("credential A", auth.get())
        } finally {
            manager.remove()
            auth.remove()
        }
    }

    @Test
    fun `exceptions restore all worker context and subsequent contextless tasks see no tenant`() {
        val manager = AsyncExecutionContext.register(ThreadLocal<String>())
        val auth = AsyncExecutionContext.register(ThreadLocal<String>())
        val executor = Executors.newSingleThreadExecutor()
        val failure = IllegalStateException("resolver failed")
        try {
            executor.submit {
                manager.set("worker database")
                auth.set("worker credential")
            }.get(5, TimeUnit.SECONDS)
            manager.set("request database")
            auth.set("request credential")
            val result = async(executor) {
                assertEquals("request database", manager.get())
                assertEquals("request credential", auth.get())
                manager.set("resolver changed database")
                auth.remove()
                throw failure
            }
            assertSame(failure, assertFailsWith<ExecutionException> { result.get(5, TimeUnit.SECONDS) }.cause)
            assertEquals(
                "worker database" to "worker credential",
                executor.submit<Pair<String?, String?>> { manager.get() to auth.get() }.get(5, TimeUnit.SECONDS)
            )

            manager.remove()
            auth.remove()
            assertEquals(null to null, async(executor) { manager.get() to auth.get() }.get(5, TimeUnit.SECONDS))
            assertEquals(
                "worker database" to "worker credential",
                executor.submit<Pair<String?, String?>> { manager.get() to auth.get() }.get(5, TimeUnit.SECONDS)
            )
        } finally {
            manager.remove()
            auth.remove()
            executor.shutdownNow()
        }
    }

    @Test
    fun `tasks remove request values from previously empty workers`() {
        val local = AsyncExecutionContext.register(ThreadLocal<String>())
        val unregistered = ThreadLocal<String>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            local.set("tenant")
            unregistered.set("caller only")
            async(executor) {
                assertEquals("tenant", local.get())
                assertNull(unregistered.get())
                local.set("changed")
            }.get(5, TimeUnit.SECONDS)
            assertNull(executor.submit<String> { local.get() }.get(5, TimeUnit.SECONDS))
        } finally {
            local.remove()
            unregistered.remove()
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent registration of the same local is idempotent`() {
        val writes = AtomicInteger()
        val local = object : ThreadLocal<String>() {
            override fun set(value: String?) {
                writes.incrementAndGet()
                super.set(value)
            }
        }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val registrations = (0 until 20).map {
                executor.submit<ThreadLocal<String>> { AsyncExecutionContext.register(local) }
            }
            registrations.forEach { assertSame(local, it.get(5, TimeUnit.SECONDS)) }
            local.set("tenant")
            val before = writes.get()
            assertEquals("tenant", async(executor) { local.get() }.get(5, TimeUnit.SECONDS))
            assertEquals(before + 1, writes.get(), "A registered local must be installed only once")
        } finally {
            local.remove()
            executor.shutdownNow()
        }
    }
}
