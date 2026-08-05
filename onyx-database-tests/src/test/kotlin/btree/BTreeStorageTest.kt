package btree

import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.MAX_UNSIGNED_40_BIT
import com.onyx.diskmap.data.MAX_UNSIGNED_48_BIT
import com.onyx.diskmap.data.putBigInt
import com.onyx.diskmap.data.putUnsignedLong48
import com.onyx.diskmap.impl.DiskBTreeMap
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Test
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BTreeStorageTest {

    @Test
    fun entryRoundTripsAndUpdatesRecordPointer() {
        val store = createStore()
        store.allocate(99)
        val entry = BTreeEntry.create(store, record = 73L)

        assertEquals(entry, BTreeEntry.get(store, entry.position))

        entry.setRecord(store, 99L)
        assertEquals(99L, BTreeEntry.get(store, entry.position).record)
    }

    @Test
    fun entryRoundTripsMaximumSixByteRecordPointer() {
        val store = createStore()
        val entry = BTreeEntry.create(store, record = MAX_UNSIGNED_48_BIT)

        assertEquals(MAX_UNSIGNED_48_BIT, BTreeEntry.get(store, entry.position).record)
        entry.setRecord(store, 0x010203040506L)
        assertEquals(0x010203040506L, BTreeEntry.get(store, entry.position).record)
    }

    @Test
    fun packedPointerWritersRejectOverflowInsteadOfTruncating() {
        assertFailsWith<IllegalArgumentException> {
            ByteBuffer.allocate(5).putBigInt(MAX_UNSIGNED_40_BIT + 1L)
        }
        assertFailsWith<IllegalArgumentException> {
            ByteBuffer.allocate(6).putUnsignedLong48(MAX_UNSIGNED_48_BIT + 1L)
        }
    }

    @Test
    fun leafPageRoundTrips() {
        val store = createStore()
        val page = BTreePage.create(store, leaf = true)
        assertEquals(0L, page.position % BTreePage.PAGE_SIZE)
        page.insertLeaf(0, key = 11L, entry = 0xfedcba9876L, record = 1001L)
        page.insertLeaf(1, key = 22L, entry = 202L, record = 2002L)
        page.insertLeaf(2, key = 33L, entry = 303L, record = 3003L)
        page.previousLeaf = 0xedcba98765L
        page.nextLeaf = 0xdcba987654L
        page.write(store)

        val persisted = BTreePage.get(store, page.position)
        assertTrue(persisted.leaf)
        assertEquals(6, BTreeEntry.ENTRY_SIZE)
        assertEquals(BTreePage.MAX_KEYS, persisted.capacity)
        assertEquals(listOf(11L, 22L, 33L), persisted.keys.take(persisted.keyCount))
        assertEquals(listOf(0xfedcba9876L, 202L, 303L), persisted.pointers.take(persisted.keyCount))
        assertEquals(0xedcba98765L, persisted.previousLeaf)
        assertEquals(0xdcba987654L, persisted.nextLeaf)
    }

    @Test
    fun internalPageRoundTrips() {
        val store = createStore()
        val page = BTreePage.create(store, leaf = false)
        page.pointers[0] = 101L
        page.insertInternal(0, key = 11L, rightChild = 202L)
        page.insertInternal(1, key = 22L, rightChild = 303L)
        page.write(store)

        val persisted = BTreePage.get(store, page.position)
        assertFalse(persisted.leaf)
        assertTrue(persisted.recordPointers.isEmpty())
        assertEquals(listOf(11L, 22L), persisted.keys.take(persisted.keyCount))
        assertEquals(listOf(101L, 202L, 303L), persisted.pointers.take(persisted.keyCount + 1))
    }

    @Test
    fun compactRootUsesSmallPageAndRoundTrips() {
        val store = createStore()
        val position = store.getFileSize()
        val page = BTreePage.create(store, leaf = true, compact = true)
        page.insertLeaf(0, key = 7L, entry = 77L, record = 777L)
        page.write(store)

        assertEquals(position, page.position)
        assertEquals(BTreePage.COMPACT_PAGE_SIZE.toLong(), store.getFileSize() - position)
        val persisted = BTreePage.get(store, page.position)
        assertTrue(persisted.compact)
        assertEquals(7L, persisted.keys[0])
        assertEquals(77L, persisted.pointers[0])
    }

    @Test
    fun treeUsesPackedFormatAcrossSplitsAndDirectReferenceReads() {
        val store = createStore()
        val records = InMemoryStore(null, "btree-storage-packed-records")
        val header = Header().apply { position = store.allocate(Header.HEADER_SIZE) }
        val map = DiskBTreeMap<Int, Int>(WeakReference(store), WeakReference(records), header, Int::class.java)

        repeat(2_000) { map[it] = it }
        map.clearCache()
        val lastReference = map.getRecID(1_999)
        assertEquals(1_999, map.getWithRecID(lastReference))

        val pending = ArrayDeque<Long>().apply { add(map.reference.firstNode) }
        while (pending.isNotEmpty()) {
            val page = BTreePage.get(store, pending.removeFirst())
            assertEquals(BTreePage.MAX_KEYS, page.capacity)
            if (!page.leaf) {
                repeat(page.keyCount + 1) { pending.add(page.pointers[it]) }
            }
        }
    }

    @Test
    fun btreeIndexAllocationOverheadStaysBelowTenPercent() {
        val store = createStore()
        val records = InMemoryStore(null, "btree-storage-density-records")
        try {
            val header = Header().apply { position = store.allocate(Header.HEADER_SIZE) }
            val map = DiskBTreeMap<Int, Int>(WeakReference(store), WeakReference(records), header, Int::class.java)
            val entryCount = 20_000

            repeat(entryCount) { map[it] = it }

            var fullPageCount = 0L
            val visited = HashSet<Long>()
            val pending = ArrayDeque<Long>().apply { add(map.reference.firstNode) }
            while (pending.isNotEmpty()) {
                val position = pending.removeFirst()
                assertTrue(visited.add(position), "B-tree page $position was visited more than once")
                val page = BTreePage.get(store, position)
                if (!page.compact) fullPageCount++
                if (!page.leaf) {
                    repeat(page.keyCount + 1) { pending.add(page.pointers[it]) }
                }
            }

            val knownPayload = java.lang.Long.BYTES.toLong() +
                Header.HEADER_SIZE +
                BTreePage.COMPACT_PAGE_SIZE +
                entryCount.toLong() * BTreeEntry.ENTRY_SIZE +
                fullPageCount * BTreePage.PAGE_SIZE
            val maximumSize = knownPayload + knownPayload / 10L

            assertTrue(
                store.getFileSize() <= maximumSize,
                "B-tree node store used ${store.getFileSize()} bytes for $knownPayload bytes of known payload"
            )
        } finally {
            store.close()
            records.close()
        }
    }

    @Test
    fun rejectsPagesFromAnotherFormatVersion() {
        val store = createStore()
        val position = store.allocate(BTreePage.COMPACT_PAGE_SIZE)
        val fixture = ByteBuffer.allocate(BTreePage.COMPACT_PAGE_SIZE)
        fixture.putInt(BTreePage.MAGIC)
        fixture.put(2.toByte())
        fixture.flip()
        store.write(fixture, position)

        assertFailsWith<IllegalArgumentException> { BTreePage.get(store, position) }
    }

    @Test
    fun primitiveKeyPageCanSkipDecodedKeyArray() {
        val store = createStore()
        val page = BTreePage.create(store, leaf = true, cacheDecodedKeys = false)
        assertFalse(page.decodedKeys.enabled)
        page.insertLeaf(0, key = 7L, entry = 77L, record = 777L)
        page.write(store)

        val persisted = BTreePage.get(store, page.position, cacheDecodedKeys = false)
        assertFalse(persisted.decodedKeys.enabled)
        assertEquals(7L, persisted.keys[0])
        assertEquals(null, persisted.decodedKeys[0])
    }

    companion object {
        private fun createStore() = InMemoryStore(null, "btree-storage")
    }
}
