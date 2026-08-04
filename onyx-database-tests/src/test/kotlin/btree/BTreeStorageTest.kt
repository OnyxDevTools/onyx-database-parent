package btree

import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Test
import kotlin.test.assertEquals
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
    fun leafPageRoundTrips() {
        val store = createStore()
        val page = BTreePage.create(store, leaf = true)
        assertEquals(0L, page.position % BTreePage.PAGE_SIZE)
        page.insertLeaf(0, key = 11L, entry = 101L, record = 1001L)
        page.insertLeaf(1, key = 22L, entry = 202L, record = 2002L)
        page.insertLeaf(2, key = 33L, entry = 303L, record = 3003L)
        page.previousLeaf = 44L
        page.nextLeaf = 55L
        page.write(store)

        val persisted = BTreePage.get(store, page.position)
        assertTrue(persisted.leaf)
        assertEquals(listOf(11L, 22L, 33L), persisted.keys.take(persisted.keyCount))
        assertEquals(listOf(101L, 202L, 303L), persisted.pointers.take(persisted.keyCount))
        assertEquals(44L, persisted.previousLeaf)
        assertEquals(55L, persisted.nextLeaf)
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

    companion object {
        private fun createStore() = InMemoryStore(null, "btree-storage")
    }
}
