package com.onyx.diskmap.impl

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.IndexPostingPage
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.lang.map.ConcurrentClockCache
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiskIndexPostingMapCacheTest {
    @Test
    fun `tiny page caches preserve splits merges and reloaded postings`() {
        for (capacity in listOf(1, 2)) withStores { nodes, data ->
            val header = newHeader(nodes)
            val map = postingMap(nodes, data, header, capacity)
            val cache = pageCache(map)
            val ids = (1L..70_000L).toList()
            // Enough ascending postings to split an internal page as well as leaves.
            ids.forEach { assertTrue(map.add(42L, it)) }
            assertTrue(cache.size <= capacity)
            assertEquals(ids, recordIds(map))

            val remaining = ids.filter { it % 97L == 0L }
            ids.filter { it % 97L != 0L }.shuffled(Random(71)).forEach {
                assertTrue(map.remove(42L, it))
            }
            assertEquals(remaining.size.toLong(), map.longSize())
            assertEquals(remaining, recordIds(map))
            assertTrue(cache.size <= capacity)
            map.clearCache()
            assertEquals(remaining, recordIds(map))

            val reloadedHeader = nodes.read(header.position, Header.HEADER_SIZE, Header()) as Header
            val reloaded = postingMap(nodes, data, reloadedHeader, capacity)
            assertEquals(remaining.size.toLong(), reloaded.longSize())
            assertEquals(remaining, recordIds(reloaded))
            remaining.forEach { assertTrue(reloaded.remove(42L, it)) }
            assertTrue(recordIds(reloaded).isEmpty())
            assertTrue(reloaded.add(42L, 1L))
            assertEquals(listOf(1L), recordIds(reloaded))
        }
    }

    @Test
    fun `concurrent readers preserve postings while writes and clears evict pages`() = withStores { nodes, data ->
        val map = postingMap(nodes, data, newHeader(nodes), 2)
        val expected = (1L..10_000L).toList()
        expected.forEach { map.add(42L, it) }
        map.clearCache()
        val cache = pageCache(map)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(5)
        try {
            val readers = List(4) { worker -> executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(2_000) { ordinal ->
                    val id = (ordinal * 137L + worker * 31L) % expected.size + 1L
                    assertTrue(map.contains(42L, id))
                    assertFalse(map.contains(41L, id))
                    assertTrue(cache.size <= 2)
                }
            } }
            val writer = executor.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(500) { ordinal ->
                    val id = 10_001L + ordinal
                    assertTrue(map.add(42L, id))
                    assertTrue(map.remove(42L, id))
                    if (ordinal % 25 == 0) map.clearCache()
                }
            }
            start.countDown()
            (readers + writer).forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(expected, recordIds(map))
            assertEquals(expected.size.toLong(), map.longSize())
        } finally {
            start.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun newHeader(nodes: Store): Header = Header().also {
        it.position = nodes.allocate(Header.HEADER_SIZE)
        nodes.write(it, it.position)
    }

    private fun postingMap(nodes: Store, data: Store, header: Header, capacity: Int) =
        DiskIndexPostingMap(WeakReference(nodes), WeakReference(data), header, Long::class.java, capacity)

    private fun recordIds(map: DiskIndexPostingMap): List<Long> = buildList {
        map.forEachRecordIdInRange(null, 0L, false, null, 0L, false) { add(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pageCache(map: DiskIndexPostingMap): ConcurrentClockCache<Long, IndexPostingPage> =
        DiskIndexPostingMap::class.java.getDeclaredField("pageCache").let {
            it.isAccessible = true
            it.get(map) as ConcurrentClockCache<Long, IndexPostingPage>
        }

    private fun withStores(action: (Store, Store) -> Unit) {
        val directory = Files.createTempDirectory("onyx-posting-clock")
        val nodePath = directory.resolve("nodes")
        val dataPath = directory.resolve("data")
        val nodes = MemoryMappedStore(nodePath.toString(), null, true)
        val data = MemoryMappedStore(dataPath.toString(), null, true)
        try {
            action(nodes, data)
        } finally {
            nodes.close()
            data.close()
            Files.deleteIfExists(nodePath)
            Files.deleteIfExists(dataPath)
            Files.deleteIfExists(directory)
        }
    }
}
