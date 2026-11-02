package com.onyx.lang.map

import java.util.AbstractMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
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
    fun `many independent caches share one aggregate entry bound`() {
        val capacity = ConcurrentClockCache.SharedCapacity(7)
        val caches = List(128) { ConcurrentClockCache<Int, String>(7, capacity) }
        caches.forEachIndexed { partition, cache ->
            repeat(3) { key ->
                cache[key] = "$partition-$key"
                assertEquals("$partition-$key", cache[key])
                assertTrue(caches.sumOf { it.size } <= 7)
            }
        }
        assertEquals(7, caches.sumOf { it.size })
        assertEquals(mapOf(0 to "127-0", 1 to "127-1", 2 to "127-2"), caches.last())
        assertTrue(caches.first().isEmpty())
    }

    @Test
    fun `shared CLOCK gives hits a second chance across cache instances`() {
        val capacity = ConcurrentClockCache.SharedCapacity(3)
        val caches = List(5) { ConcurrentClockCache<Int, String>(3, capacity) }
        for (index in 0..3) caches[index][1] = "value-$index"
        assertTrue(caches[0].isEmpty())
        assertEquals("value-1", caches[1][1])
        caches[4][1] = "value-4"
        assertTrue(caches[2].isEmpty())
        assertEquals("value-1", caches[1][1])
        assertEquals("value-3", caches[3][1])
        assertEquals("value-4", caches[4][1])
    }

    @Test
    fun `shared capacity preserves local limits and releases removed entries`() {
        assertFailsWith<IllegalArgumentException> { ConcurrentClockCache.SharedCapacity(0) }
        assertFailsWith<IllegalArgumentException> { ConcurrentClockCache.SharedCapacity(-1) }
        val capacity = ConcurrentClockCache.SharedCapacity(3)
        val first = ConcurrentClockCache<Int, String>(1, capacity)
        val second = ConcurrentClockCache<Int, String>(3, capacity)
        repeat(100) { round ->
            first[1] = "old"
            val iterator = first.entries.iterator()
            val stale = iterator.next()
            first[2] = "replacement"
            iterator.remove()
            assertFailsWith<IllegalStateException> { stale.setValue("stale") }
            assertEquals(mapOf(2 to "replacement"), first)
            second[1] = "independent"
            assertEquals("replacement", first[2])
            assertEquals("independent", second.putIfAbsent(1, "ignored"))
            when (round % 5) {
                0 -> assertEquals("replacement", first.remove(2))
                1 -> assertTrue(first.keys.remove(2))
                2 -> assertTrue(first.values.remove("replacement"))
                3 -> assertTrue(first.entries.remove(AbstractMap.SimpleEntry(2, "replacement")))
                else -> first.clear()
            }
            assertEquals(mapOf(1 to "independent"), second)
            second[2] = "second"
            second[3] = "third"
            assertEquals(3, second.size, "Removed entries must release the shared budget")
            first[4] = "fourth"
            assertEquals(3, first.size + second.size)
            first.clear()
            second.clear()
        }
    }

    @Test
    fun `concurrent caches share a bound during writes removals clears and iteration`() {
        val capacity = ConcurrentClockCache.SharedCapacity(17)
        val caches = List(8) { ConcurrentClockCache<Int, Pair<Int, Int>>(7, capacity) }
        val barrier = CyclicBarrier(caches.size) {
            assertTrue(caches.sumOf { it.size } <= 17)
            assertTrue(caches.all { it.size <= 7 })
        }
        val executor = Executors.newFixedThreadPool(caches.size)
        try {
            val workers = caches.mapIndexed { partition, cache -> executor.submit {
                repeat(200) { round ->
                    repeat(10) { key ->
                        cache[key] = partition to key
                        cache[key]?.let { assertEquals(partition to key, it) }
                        if (key % 3 == 0) cache.remove(key - 1)
                    }
                    if (round % 7 == 0) cache.clear()
                    val iterator = cache.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        assertEquals(partition to entry.key, entry.value)
                        if (entry.key % 2 == 0) iterator.remove()
                    }
                    barrier.await(10, TimeUnit.SECONDS)
                    // Keep every cache stable until the barrier's aggregate assertion finishes.
                    barrier.await(10, TimeUnit.SECONDS)
                }
            } }
            workers.forEach { it.get(30, TimeUnit.SECONDS) }
            caches.forEach { it.clear() }
            caches.forEachIndexed { index, cache -> cache[1] = index to 1 }
            assertEquals(8, caches.sumOf { it.size })
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

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
    fun `putIfAbsent preserves existing values and gives them a second chance`() {
        val cache = ConcurrentClockCache<Int, String>(3)
        for (id in 1..3) assertNull(cache.putIfAbsent(id, "value-$id"))
        assertNull(cache.putIfAbsent(4, "value-4"))
        assertEquals(setOf(2, 3, 4), cache.keys)
        assertEquals("value-2", cache.putIfAbsent(2, "replacement"))
        assertNull(cache.putIfAbsent(5, "value-5"))
        assertEquals(setOf(2, 4, 5), cache.keys)
        assertEquals("value-2", cache[2])
        assertEquals(3, cache.size)
    }

    @Test
    fun `racing putIfAbsent calls publish one winner while evicting older keys`() {
        val workerCount = 8
        val rounds = 100
        val cache = ConcurrentClockCache<Int, String>(1)
        val executor = Executors.newFixedThreadPool(workerCount)
        val barrier = CyclicBarrier(workerCount)
        try {
            val workers = List(workerCount) { worker -> executor.submit<List<String?>> {
                List(rounds) { round ->
                    barrier.await(10, TimeUnit.SECONDS)
                    val previous = cache.putIfAbsent(round, "worker-$worker")
                    assertEquals(1, cache.size)
                    // Every contender must finish before the next key can evict this round's winner.
                    barrier.await(10, TimeUnit.SECONDS)
                    previous
                }
            } }
            val results = workers.map { it.get(30, TimeUnit.SECONDS) }
            repeat(rounds) { round ->
                val winners = results.indices.filter { results[it][round] == null }
                assertEquals(1, winners.size, "round $round")
                val winner = winners.single()
                results.indices.filter { it != winner }.forEach { worker ->
                    assertEquals("worker-$winner", results[worker][round], "round $round")
                }
            }
            val finalWinner = results.indices.single { results[it].last() == null }
            assertEquals(mapOf(rounds - 1 to "worker-$finalWinner"), cache)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
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
    fun `readers may look up entries concurrently`() {
        val readersEntered = CountDownLatch(2)
        val releaseReaders = CountDownLatch(1)
        val cache = ConcurrentClockCache<HashKey, String>(2)
        for (id in 1..2) cache[HashKey(id)] = "value-$id"
        val executor = Executors.newFixedThreadPool(2)
        try {
            val readers = (1..2).map { id ->
                executor.submit<String?> {
                    cache[HashKey(id) {
                        readersEntered.countDown()
                        check(releaseReaders.await(10, TimeUnit.SECONDS))
                    }]
                }
            }
            assertTrue(readersEntered.await(5, TimeUnit.SECONDS), "Readers should share access to the table")
            releaseReaders.countDown()
            readers.forEachIndexed { index, reader ->
                assertEquals("value-${index + 1}", reader.get(5, TimeUnit.SECONDS))
            }
        } finally {
            releaseReaders.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
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
            reads.get(5, TimeUnit.SECONDS)
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
    fun `concurrent reads writes removals clears and iteration stay bounded and make progress`() {
        val cache = ConcurrentClockCache<Int, Pair<Int, Int>>(64)
        val executor = Executors.newFixedThreadPool(10)
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
            val iterations = executor.submit {
                start.await()
                repeat(2_000) {
                    val iterator = cache.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        assertEquals(entry.key, entry.value.first)
                        if (entry.key % 7 == 0) iterator.remove()
                    }
                }
            }
            start.countDown()
            (writers + readers + clears + iterations).forEach { it.get(30, TimeUnit.SECONDS) }
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
