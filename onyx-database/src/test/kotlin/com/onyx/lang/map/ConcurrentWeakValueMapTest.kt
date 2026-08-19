package com.onyx.lang.map

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ConcurrentWeakValueMapTest {

    @Test
    fun `putIfAbsent retains one canonical live value`() {
        val cache = ConcurrentWeakValueMap<Long, Any>()
        val threadCount = 8
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val results = (0 until threadCount).map {
                executor.submit<Any> {
                    val candidate = Any()
                    start.await()
                    cache.putIfAbsent(42L, candidate) ?: candidate
                }
            }

            start.countDown()
            val canonical = results.first().get(10, TimeUnit.SECONDS)
            results.drop(1).forEach { assertSame(canonical, it.get(10, TimeUnit.SECONDS)) }
            assertSame(canonical, cache[42L])
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `collected values are removed from the backing map`() {
        val cache = ConcurrentWeakValueMap<Long, Any>()
        val reference = cacheWithoutRetainingValue(cache)

        var attempts = 0
        while (reference.get() != null && attempts++ < 100) {
            System.gc()
            Thread.sleep(10)
        }

        assertNull(reference.get(), "Cached value was not collected")
        assertNull(cache[1L])
        assertEquals(0, cache.retainedEntryCount())
    }

    private fun cacheWithoutRetainingValue(cache: ConcurrentWeakValueMap<Long, Any>): WeakReference<Any> {
        val value = Any()
        cache[1L] = value
        return WeakReference(value)
    }
}
