package com.onyx.lang.map

import java.lang.ref.Reference
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimisticWeakHashMapTest {

    @Test
    fun `concurrent readers expunge stale colliding keys without losing live entries`() {
        val readerCount = 8
        val executor = Executors.newFixedThreadPool(readerCount)
        try {
            repeat(8) {
                val cache = OptimisticLockingMap(WeakHashMap<CollidingKey, Int>())
                val keys = List(256) { CollidingKey(it) }
                keys.forEach { cache[it] = it.id }
                val liveKeys = keys.filter { it.id % 2 == 0 }

                // WeakHashMap exposes its entries as Reference objects. Clear and enqueue
                // them explicitly so this exercises cleanup without depending on GC timing.
                val staleEntries = cache.entries.filter { it.key.id % 2 != 0 }
                    .map { it as Reference<*> }
                staleEntries.forEach {
                    it.clear()
                    assertTrue(it.enqueue())
                }
                Reference.reachabilityFence(keys)

                val readersInsideGet = CountDownLatch(readerCount)
                val releaseReaders = CountDownLatch(1)
                val results = (0 until readerCount).map { reader ->
                    executor.submit {
                        // hashCode runs inside both the wrapper's read lock and the
                        // delegated get, immediately before WeakHashMap drains its queue.
                        val firstKey = CollidingKey(0) {
                            readersInsideGet.countDown()
                            assertTrue(releaseReaders.await(10, TimeUnit.SECONDS))
                        }
                        assertEquals(0, cache[firstKey])
                        repeat(8) { pass ->
                            liveKeys.indices.forEach { offset ->
                                val key = liveKeys[(offset + reader + pass) % liveKeys.size]
                                assertEquals(key.id, cache[key])
                            }
                        }
                    }
                }

                try {
                    assertTrue(
                        readersInsideGet.await(10, TimeUnit.SECONDS),
                        "All readers should enter the delegated get concurrently"
                    )
                } finally {
                    releaseReaders.countDown()
                }
                results.forEach { it.get(10, TimeUnit.SECONDS) }
                assertEquals(liveKeys.size, cache.size)
                liveKeys.forEach { assertEquals(it.id, cache[it]) }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private class CollidingKey(val id: Int, private val beforeHash: (() -> Unit)? = null) {
        override fun hashCode(): Int {
            beforeHash?.invoke()
            return 0
        }

        override fun equals(other: Any?): Boolean = other is CollidingKey && id == other.id
    }
}
