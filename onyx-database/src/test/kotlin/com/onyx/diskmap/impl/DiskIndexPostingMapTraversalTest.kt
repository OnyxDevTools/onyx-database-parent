package com.onyx.diskmap.impl

import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.IndexPostingPage
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.impl.InMemoryStore
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiskIndexPostingMapTraversalTest {
    @Test
    fun `duplicate object values are decoded once after cache eviction`() = withStores { nodes, data ->
        val map = postingMap(nodes, data, String::class.java)
        val value = "duplicate-index-value".repeat(200)
        (1L..10_000L).forEach { map.add(value, it) }
        map.add("a", 10_001L)
        map.add("z", 10_002L)
        map.clearCache()
        data.objectReads = 0

        val result = recordIds(map, String(value.toCharArray()), Long.MIN_VALUE, true,
            String(value.toCharArray()), Long.MAX_VALUE, true)

        assertEquals((1L..10_000L).toList(), result)
        assertTrue(data.objectReads <= 3, "Decoded ${data.objectReads} values for three distinct tokens")
    }

    @Test
    fun `range bound comparisons grow with pages instead of duplicate postings`() = withStores { nodes, data ->
        val comparisons = AtomicInteger()
        val value = ComparedValue(7, comparisons)
        val objects = ObjectStore(data)
        val map = postingMap(nodes, objects, ComparedValue::class.java)
        (1L..10_000L).forEach { map.add(value, it) }
        comparisons.set(0)

        val query = ComparedValue(7, comparisons)
        assertEquals(10_000, recordIds(map, query, Long.MIN_VALUE, true, query, Long.MAX_VALUE, true).size)
        assertTrue(comparisons.get() < 100,
            "Compared ${comparisons.get()} values while traversing about 34 leaf pages")
    }

    @Test
    fun `page bounds preserve all inclusive exclusive and unbounded tuple ranges`() = withStores { nodes, data ->
        val map = postingMap(nodes, data, Long::class.java)
        val postings = (1L..2_000L).flatMap { id -> (-1L..1L).map { value -> value to id } }
        postings.shuffled(Random(71)).forEach { (value, id) -> map.add(value, id) }
        val sorted = postings.sortedWith(compareBy({ it.first }, { it.second }))
        map.clearCache()

        val bounds = listOf(null, -2L to 1L, -1L to 1L, -1L to 2_000L, 0L to 250L,
            0L to 1_000L, 0L to 1_001L, 1L to 1L, 1L to 2_000L, 2L to 1L)
        val comparator = compareBy<Pair<Long, Long>>({ it.first }, { it.second })
        for (from in bounds) for (to in bounds) for (includeFrom in listOf(false, true)) {
            for (includeTo in listOf(false, true)) {
                val expected = sorted.filter { posting ->
                    (from == null || comparator.compare(posting, from).let { it > 0 || it == 0 && includeFrom }) &&
                        (to == null || comparator.compare(posting, to).let { it < 0 || it == 0 && includeTo })
                }.map { it.second }
                assertEquals(expected, recordIds(map, from?.first, from?.second ?: 0L, includeFrom,
                    to?.first, to?.second ?: 0L, includeTo),
                    "from=$from includeFrom=$includeFrom to=$to includeTo=$includeTo")
            }
        }
    }

    @Test
    fun `visit limit stops loading leaf pages`() = withStores { nodes, data ->
        val map = postingMap(nodes, data, Long::class.java)
        (1L..10_000L).forEach { map.add(1L, it) }
        map.clearCache()
        nodes.bytesRead = 0
        assertEquals(1, map.visitRecordIdsInRange(1L, Long.MIN_VALUE, true, 1L, Long.MAX_VALUE,
            true, 1) { true })
        val boundedBytes = nodes.bytesRead

        map.clearCache()
        nodes.bytesRead = 0
        assertEquals(10_000, map.visitRecordIdsInRange(1L, Long.MIN_VALUE, true, 1L, Long.MAX_VALUE,
            true, Int.MAX_VALUE) { true })
        assertTrue(nodes.bytesRead > boundedBytes * 10,
            "A one-record visit read $boundedBytes bytes; the full range read ${nodes.bytesRead}")
    }

    @Test
    fun `page loading reads only occupied slots`() = withStores { nodes, _ ->
        val page = IndexPostingPage.create(nodes, leaf = true, compact = false,
            valueKind = IndexPostingPage.ValueKind.INTEGRAL, valueTokenWidth = Long.SIZE_BYTES,
            signedValueToken = true)
        page.insertLeaf(0, -7L, 42L)
        page.write(nodes)
        nodes.bytesRead = 0

        val loaded = IndexPostingPage.get(nodes, page.position, IndexPostingPage.ValueKind.INTEGRAL,
            Long.SIZE_BYTES, true)
        assertEquals(32L + Long.SIZE_BYTES + 5L, nodes.bytesRead)
        assertEquals(1, loaded.keyCount)
        assertEquals(-7L, loaded.valueTokens[0])
        assertEquals(42L, loaded.recordIds[0])
    }

    private fun recordIds(map: IndexPostingMap, from: Any?, fromId: Long, includeFrom: Boolean,
                          to: Any?, toId: Long, includeTo: Boolean): List<Long> = buildList {
        map.forEachRecordIdInRange(from, fromId, includeFrom, to, toId, includeTo) { add(it) }
    }

    private fun postingMap(nodes: Store, data: Store, type: Class<*>): IndexPostingMap {
        val header = Header().also {
            it.position = nodes.allocate(Header.HEADER_SIZE)
            nodes.write(it, it.position)
        }
        return DiskIndexPostingMap(WeakReference(nodes), WeakReference(data), header, type)
    }

    private fun withStores(action: (CountingStore, CountingStore) -> Unit) {
        val nodes = CountingStore(InMemoryStore(null, "traversal-nodes"))
        val data = CountingStore(InMemoryStore(null, "traversal-data"))
        try {
            action(nodes, data)
        } finally {
            nodes.close()
            data.close()
        }
    }

    private class CountingStore(private val delegate: Store) : Store by delegate {
        var objectReads = 0
        var bytesRead = 0L

        override fun <T> getObject(position: Long): T {
            objectReads++
            return delegate.getObject(position)
        }

        override fun read(buffer: ByteBuffer, position: Long) {
            bytesRead += buffer.remaining()
            delegate.read(buffer, position)
        }
    }

    private class ObjectStore(delegate: Store) : Store by delegate {
        private val values = HashMap<Long, Any?>()

        override fun writeObject(value: Any?): Long = (values.size + 1L).also { values[it] = value }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(position: Long): T = values.getValue(position) as T
    }

    private class ComparedValue(val key: Int, val comparisons: AtomicInteger) : Comparable<ComparedValue> {
        override fun compareTo(other: ComparedValue): Int {
            comparisons.incrementAndGet()
            return key.compareTo(other.key)
        }
    }
}
