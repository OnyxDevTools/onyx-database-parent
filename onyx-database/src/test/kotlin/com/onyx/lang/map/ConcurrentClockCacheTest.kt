package com.onyx.lang.map

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcurrentClockCacheTest {
    @Test
    fun `reference bits protect reused nodes without guaranteeing exact LRU order`() {
        val cache = ConcurrentClockCache<Int, String>(3)
        for (id in 1..3) cache[id] = "value-$id"
        assertEquals("value-1", cache[1])
        cache[4] = "value-4"
        // All three original entries had a reference bit. CLOCK gives each one chance and wraps;
        // an exact LRU would have evicted 2 instead of the recently read 1.
        assertNull(cache[1])
        assertEquals("value-2", cache[2])
        cache[5] = "value-5"
        assertEquals(setOf(2, 4, 5), cache.keys)
        assertNull(cache[3])
        assertEquals(3, cache.size)
    }

    @Test
    fun `replacement removal clear and capacity one preserve values and bounds`() {
        assertFailsWith<IllegalArgumentException> { ConcurrentClockCache<Int, String>(0) }
        assertFailsWith<IllegalArgumentException> { ConcurrentClockCache<Int, String>(-1) }
        val cache = ConcurrentClockCache<Int, String>(1)
        assertNull(cache.put(1, "first"))
        assertEquals("first", cache.put(1, "updated"))
        assertEquals(1, cache.size)
        cache[2] = "second"
        assertNull(cache[1])
        assertEquals("second", cache.remove(2))
        assertTrue(cache.isEmpty())
        assertNull(cache.remove(2))
        cache[3] = "third"
        cache.clear()
        assertTrue(cache.isEmpty())
        cache[4] = "fourth"
        cache[5] = "fifth"
        assertEquals(mapOf(5 to "fifth"), cache)
    }

    @Test
    fun `backed map views preserve the ring during removals and updates`() {
        val cache = ConcurrentClockCache<Int, String>(4)
        val keys = cache.keys
        for (id in 1..4) cache[id] = "value-$id"
        assertEquals(setOf(1, 2, 3, 4), keys)
        val entry = cache.entries.first { it.key == 2 }
        assertEquals("value-2", entry.setValue("updated"))
        assertEquals("updated", cache[2])
        assertTrue(keys.remove(1)) // Remove the hand.
        assertTrue(cache.values.remove("value-4")) // Remove the tail.
        val iterator = cache.entries.iterator()
        val removed = iterator.next().key
        iterator.remove()
        assertFalse(cache.containsKey(removed))
        assertFailsWith<IllegalStateException> { iterator.remove() }
        cache.entries.clear()
        assertTrue(keys.isEmpty())
        for (id in 10..29) {
            cache[id] = "value-$id"
            assertTrue(cache.size <= 4)
        }
        assertEquals((26..29).toSet(), keys)
        assertEquals(cache.entries.associate { it.key to it.value }, cache)
    }

    @Test
    fun `stale iterator entries cannot remove or overwrite a replacement after clear`() {
        val cache = ConcurrentClockCache<Int, String>(2)
        cache[1] = "old"
        val iterator = cache.entries.iterator()
        val entry = iterator.next()
        cache.clear()
        cache[1] = "new"
        iterator.remove()
        assertEquals("new", cache[1])
        assertFailsWith<IllegalStateException> { entry.setValue("stale") }
        cache[2] = "second"
        cache[3] = "third"
        assertEquals(setOf(2, 3), cache.keys)
    }

    @Test
    fun `hits and misses complete while a writer is paused inside the cache lock`() {
        val enteredWriter = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        val cache = ConcurrentClockCache<HashKey, String>(2)
        val existing = HashKey(1)
        cache[existing] = "existing"
        val blocked = HashKey(2) {
            enteredWriter.countDown()
            check(releaseWriter.await(10, TimeUnit.SECONDS))
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer = executor.submit { cache[blocked] = "inserted" }
            assertTrue(enteredWriter.await(5, TimeUnit.SECONDS))
            val reads = executor.submit {
                repeat(1_000) {
                    assertEquals("existing", cache[existing])
                    assertNull(cache[HashKey(3)])
                }
            }
            reads.get(5, TimeUnit.SECONDS) // The writer is still paused and holds its lock.
            releaseWriter.countDown()
            writer.get(5, TimeUnit.SECONDS)
            assertEquals("inserted", cache[HashKey(2)])
        } finally {
            releaseWriter.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `concurrent reads writes removals and clears stay bounded and make progress`() {
        val cache = ConcurrentClockCache<Int, Pair<Int, Int>>(64)
        val executor = Executors.newFixedThreadPool(9)
        val start = CountDownLatch(1)
        try {
            val writers = List(4) { worker -> executor.submit {
                start.await()
                repeat(10_000) { ordinal ->
                    val key = (ordinal * 17 + worker * 31) % 256
                    cache[key] = key to ordinal
                    if (ordinal % 7 == 0) cache.remove((key + 5) % 256)
                    assertTrue(cache.size <= 64)
                }
            } }
            val readers = List(4) { worker -> executor.submit {
                start.await()
                repeat(40_000) { ordinal ->
                    val key = (ordinal + worker * 13) % 256
                    cache[key]?.let { assertEquals(key, it.first) }
                    assertTrue(cache.size <= 64)
                }
            } }
            val clears = executor.submit {
                start.await()
                repeat(100) { cache.clear(); Thread.yield() }
            }
            start.countDown()
            (writers + readers + clears).forEach { it.get(30, TimeUnit.SECONDS) }
            cache.clear()
            for (id in 1..256) cache[id] = id to id
            assertEquals((193..256).toSet(), cache.keys)
            for (id in 193..256) assertEquals(id to id, cache[id])
        } finally {
            start.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private class HashKey(val id: Int, private val beforeHash: (() -> Unit)? = null) {
        override fun hashCode(): Int { beforeHash?.invoke(); return id }
        override fun equals(other: Any?): Boolean = other is HashKey && id == other.id
    }
}
