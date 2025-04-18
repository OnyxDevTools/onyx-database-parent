package skiplist

import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class SkipBTreeNodeTest {

    @Test
    fun testCreate() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore)
        assertEquals(99 + 8, skipNode.position, "Skip List node was not allocated correctly")
        assertEquals(SkipNode.get(memoryStore, skipNode.position), skipNode, "Skip node was not saved correctly")
    }


    @Ignore
    @Test
    fun testSetRecord() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6u)
        skipNode.setRecord(memoryStore, 1)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(1, skipNode.record, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSeTop() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6u)
        skipNode.setTop(memoryStore, 9393L)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393L, skipNode.up, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetBottom() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6u)
        skipNode.setBottom(memoryStore, 9393L)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393L, skipNode.down, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetLeft() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6u)
        skipNode.setLeft(memoryStore, 9393L)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393L, skipNode.left, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetRight() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6u)
        skipNode.setRight(memoryStore, 9393L)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393L, skipNode.right, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    companion object {
        private fun createStore() = InMemoryStore(null, "1")
    }
}