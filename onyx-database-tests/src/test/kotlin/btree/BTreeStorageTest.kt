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

        assertEquals(0L, entry.position % BTreeEntry.ENTRY_SIZE)
        assertEquals(entry, BTreeEntry.get(store, entry.position))

        entry.setRecord(store, 99L)
        assertEquals(99L, BTreeEntry.get(store, entry.position).record)
    }

    @Test
    fun packedEntryRoundTripsSixByteRecordPointer() {
        val store = createStore()
        val entry = BTreeEntry.create(
            store,
            record = MAX_UNSIGNED_48_BIT,
            recordSize = BTreeEntry.PACKED_ENTRY_SIZE
        )

        assertEquals(
            MAX_UNSIGNED_48_BIT,
            BTreeEntry.get(store, entry.position, BTreeEntry.PACKED_ENTRY_SIZE).record
        )
        entry.setRecord(store, 0x010203040506L)
        assertEquals(
            0x010203040506L,
            BTreeEntry.get(store, entry.position, BTreeEntry.PACKED_ENTRY_SIZE).record
        )
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
        assertTrue(persisted.packedPointers)
        assertTrue(persisted.packedEntryRecords)
        assertEquals(BTreeEntry.PACKED_ENTRY_SIZE, persisted.entryRecordSize)
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
    fun legacyPageLayoutStillRoundTripsAndSupportsTargetedWrites() {
        val store = createStore()
        val page = BTreePage.create(store, leaf = true, packedPointers = false)
        assertEquals(BTreePage.LEGACY_MAX_KEYS, page.capacity)
        page.insertLeaf(0, key = 11L, entry = 101L, record = 1001L)
        page.insertLeaf(1, key = 22L, entry = 202L, record = 2002L)
        page.previousLeaf = 44L
        page.nextLeaf = 55L
        page.write(store)

        val persisted = BTreePage.get(store, page.position)
        assertFalse(persisted.packedPointers)
        assertEquals(BTreePage.LEGACY_MAX_KEYS, persisted.capacity)
        persisted.removeLeaf(0)
        persisted.writeSlots(store, 0)
        persisted.writeCount(store)

        val rewritten = BTreePage.get(store, page.position)
        assertFalse(rewritten.packedPointers)
        assertEquals(listOf(22L), rewritten.keys.take(rewritten.keyCount))
        assertEquals(listOf(202L), rewritten.pointers.take(rewritten.keyCount))
        assertEquals(44L, rewritten.previousLeaf)
        assertEquals(55L, rewritten.nextLeaf)
    }

    @Test
    fun pagesSplitFromLegacyTreeKeepLegacyLayout() {
        val store = createStore()
        val records = InMemoryStore(null, "btree-storage-records")
        val headerPosition = store.allocate(Header.HEADER_SIZE)
        val root = BTreePage.create(store, leaf = true, compact = true, packedPointers = false)
        root.write(store)
        val header = Header().apply {
            firstNode = root.position
            position = headerPosition
        }
        val map = DiskBTreeMap<Int, Int>(WeakReference(store), WeakReference(records), header, Int::class.java)

        repeat(2_000) { map[it] = it }
        map.clearCache()
        assertEquals((0 until 2_000).toList(), map.keys.toList())

        val pending = ArrayDeque<Long>().apply { add(map.reference.firstNode) }
        while (pending.isNotEmpty()) {
            val page = BTreePage.get(store, pending.removeFirst())
            assertFalse(page.packedPointers)
            assertFalse(page.packedEntryRecords)
            if (!page.leaf) {
                repeat(page.keyCount + 1) { pending.add(page.pointers[it]) }
            }
        }
    }

    @Test
    fun versionThreeTreeKeepsEightByteEntriesAcrossSplits() {
        val store = createStore()
        val records = InMemoryStore(null, "btree-storage-v3-records")
        val headerPosition = store.allocate(Header.HEADER_SIZE)
        val root = BTreePage.create(
            store,
            leaf = true,
            compact = true,
            packedPointers = true,
            packedEntryRecords = false
        )
        root.write(store)
        val header = Header().apply {
            firstNode = root.position
            position = headerPosition
        }
        val map = DiskBTreeMap<Int, Int>(WeakReference(store), WeakReference(records), header, Int::class.java)

        repeat(2_000) { map[it] = it }
        map.clearCache()
        val lastReference = map.getRecID(1_999)
        assertEquals(1_999, map.getWithRecID(lastReference))

        val pending = ArrayDeque<Long>().apply { add(map.reference.firstNode) }
        while (pending.isNotEmpty()) {
            val page = BTreePage.get(store, pending.removeFirst())
            assertTrue(page.packedPointers)
            assertFalse(page.packedEntryRecords)
            assertEquals(BTreeEntry.LEGACY_ENTRY_SIZE, page.entryRecordSize)
            if (!page.leaf) {
                repeat(page.keyCount + 1) { pending.add(page.pointers[it]) }
            }
        }
    }

    @Test
    fun newTreeUsesSixByteEntriesAcrossSplitsAndDirectReferenceReads() {
        val store = createStore()
        val records = InMemoryStore(null, "btree-storage-v4-records")
        val header = Header().apply { position = store.allocate(Header.HEADER_SIZE) }
        val map = DiskBTreeMap<Int, Int>(WeakReference(store), WeakReference(records), header, Int::class.java)

        repeat(2_000) { map[it] = it }
        map.clearCache()
        val lastReference = map.getRecID(1_999)
        assertEquals(1_999, map.getWithRecID(lastReference))

        val pending = ArrayDeque<Long>().apply { add(map.reference.firstNode) }
        while (pending.isNotEmpty()) {
            val page = BTreePage.get(store, pending.removeFirst())
            assertTrue(page.packedPointers)
            assertTrue(page.packedEntryRecords)
            assertEquals(BTreeEntry.PACKED_ENTRY_SIZE, page.entryRecordSize)
            if (!page.leaf) {
                repeat(page.keyCount + 1) { pending.add(page.pointers[it]) }
            }
        }
    }

    @Test
    fun readsAndPreservesHandBuiltLegacyV2Fixture() {
        val store = createStore()
        val position = store.allocate(BTreePage.COMPACT_PAGE_SIZE)
        val fixture = ByteBuffer.allocate(BTreePage.COMPACT_PAGE_SIZE)
        fixture.putInt(BTreePage.MAGIC)
        fixture.put(2.toByte())
        fixture.put(3.toByte()) // leaf and compact flags
        fixture.putShort(2)
        fixture.putLong(0x0102030405060708L)
        fixture.putLong(0x1112131415161718L)
        fixture.putLong(0L)
        fixture.putLong(7L)
        fixture.putLong(0x2122232425262728L)
        fixture.putLong(9L)
        fixture.putLong(0x3132333435363738L)
        repeat(2) {
            fixture.putLong(0L)
            fixture.putLong(0L)
        }
        fixture.flip()
        store.write(fixture, position)

        val page = BTreePage.get(store, position)
        assertFalse(page.packedPointers)
        assertTrue(page.compact)
        assertEquals(listOf(7L, 9L), page.keys.take(page.keyCount))
        assertEquals(
            listOf(0x2122232425262728L, 0x3132333435363738L),
            page.pointers.take(page.keyCount)
        )
        assertEquals(0x0102030405060708L, page.previousLeaf)
        assertEquals(0x1112131415161718L, page.nextLeaf)

        page.write(store)
        val version = ByteBuffer.allocate(1)
        store.read(version, position + Integer.BYTES)
        version.flip()
        assertEquals(2.toByte(), version.get())
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
