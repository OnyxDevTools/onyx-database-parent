package com.onyx.diskmap.impl

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.impl.InMemoryStore
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiskIndexPostingMapConcurrencyTest {
    @Test
    fun `range actions release the index lock before calling readers`() = writerDuringCallback { map, block ->
        map.forEachRecordIdInRange(null, 0, false, null, 0, false) { block() }
    }

    @Test
    fun `bounded range visitors release the index lock before calling readers`() = writerDuringCallback { map, block ->
        map.visitRecordIdsInRange(null, 0, false, null, 0, false, 2_000) {
            block()
            true
        }
    }

    @Test
    fun `ordered posting visitors release the index lock before calling readers`() = writerDuringCallback { map, block ->
        map.visitPostingsInRange(null, 0, false, null, 0, false) { _, _ ->
            block()
            true
        }
    }

    @Test
    fun `distinct value actions release the index lock before calling readers`() = writerDuringCallback { map, block ->
        map.forEachDistinctValue { block() }
    }

    @Test
    fun `tuple traversal resumes after concurrent leaf merges and splits`() = withMap { map ->
        (2L..12_000L step 2).forEach { map.add(7L, it) }
        map.add(8L, 1L)
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val visited = ArrayList<Pair<Long, Long>>()
        val reader = executor.submit {
            map.visitPostingsInRange(null, 0, false, null, 0, false) { value, id ->
                visited.add(value as Long to id)
                if (visited.size == 1) {
                    entered.countDown()
                    assertTrue(resume.await(10, TimeUnit.SECONDS), "Reader callback was not released")
                }
                true
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Reader did not reach its callback")
            val writer = executor.submit {
                // Remove the copied batch's continuation tuple and merge its former leaves.
                (2L..4_000L step 2).forEach { map.remove(7L, it) }
                // Split the remaining tree while its reader has no live page cursor.
                (4_001L..11_999L step 2).forEach { map.add(7L, it) }
                map.add(9L, 1L)
            }
            writer.get(5, TimeUnit.SECONDS)
            resume.countDown()
            reader.get(5, TimeUnit.SECONDS)

            assertEquals(visited.sortedWith(compareBy({ it.first }, { it.second })), visited)
            assertEquals(visited.size, visited.toSet().size, "A merged leaf was visited twice")
            val retained = (4_002L..12_000L step 2).map { 7L to it } + (8L to 1L)
            assertTrue(visited.containsAll(retained), "Traversal skipped unchanged postings after a page mutation")
        } finally {
            resume.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `distinct object values remain unique across copied batches`() = withMap(String::class.java) { map ->
        (1L..3_000L).forEach { map.add("same", it) }
        map.add("tail", 1L)
        val values = ArrayList<Any>()
        map.forEachDistinctValue(values::add)
        assertEquals(listOf<Any>("same", "tail"), values)
    }

    private fun writerDuringCallback(scan: (DiskIndexPostingMap, () -> Unit) -> Unit) = withMap { map ->
        (1L..2_000L).forEach { map.add(7L, it) }
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val executor = Executors.newFixedThreadPool(2)
        val reader = executor.submit {
            scan(map) {
                if (first.compareAndSet(true, false)) {
                    entered.countDown()
                    assertTrue(resume.await(10, TimeUnit.SECONDS), "Reader callback was not released")
                }
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Reader did not reach its callback")
            val writer = executor.submit<Boolean> { map.add(8L, 9_000L) }
            assertTrue(writer.get(5, TimeUnit.SECONDS), "Writer could not complete while the callback was paused")
            resume.countDown()
            reader.get(5, TimeUnit.SECONDS)
        } finally {
            resume.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun withMap(type: Class<*> = Long::class.java, action: (DiskIndexPostingMap) -> Unit) {
        val nodes = InMemoryStore(null, "posting-concurrency-nodes")
        val data = InMemoryStore(null, "posting-concurrency-data")
        try {
            val header = Header().also {
                it.position = nodes.allocate(Header.HEADER_SIZE)
                nodes.write(it, it.position)
            }
            action(DiskIndexPostingMap(WeakReference(nodes), WeakReference(data), header, type))
        } finally {
            nodes.close()
            data.close()
        }
    }
}
