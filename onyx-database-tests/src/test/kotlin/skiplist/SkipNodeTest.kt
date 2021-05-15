package skiplist

import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.impl.InMemoryStore
import org.junit.Test
import kotlin.test.assertEquals

class SkipNodeTest {

    @Test
    fun testCreate() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore)
        assertEquals(99 + 8, skipNode.position, "Skip List node was not allocated correctly")
        assertEquals(SkipNode.get(memoryStore, skipNode.position), skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetRecord() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6)
        skipNode.setRecord(memoryStore, 1)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(1, skipNode.record, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSeTop() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6)
        skipNode.setTop(memoryStore, 9393)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393, skipNode.up, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetBottom() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6)
        skipNode.setBottom(memoryStore, 9393)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393, skipNode.down, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetLeft() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6)
        skipNode.setLeft(memoryStore, 9393)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393, skipNode.left, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    @Test
    fun testSetRight() {
        val memoryStore = createStore()
        memoryStore.allocate(99)
        val skipNode = SkipNode.create(memoryStore, 1, 2, 3, 4, 5, 6)
        skipNode.setRight(memoryStore, 9393)
        val persistedNode = SkipNode.get(memoryStore, skipNode.position)
        assertEquals(9393, skipNode.right, "Value was not updated")
        assertEquals(persistedNode, skipNode, "Skip node was not saved correctly")
    }

    companion object {
        private fun createStore() = InMemoryStore(null, "1")
    }
}