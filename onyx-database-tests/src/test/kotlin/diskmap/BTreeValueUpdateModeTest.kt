package diskmap

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.impl.DiskBTreeMap
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.StoreType
import com.onyx.diskmap.store.impl.FileChannelStore
import com.onyx.diskmap.store.impl.InMemoryStore
import com.onyx.diskmap.store.impl.MemoryMappedStore
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class BTreeValueUpdateModeTest(private val storeType: StoreType) {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun equalSerializedFramesReuseTheValuePositionWithoutGrowingEitherStore() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            map[1] = ByteArray(2_752) { 1 }
            map[2] = ByteArray(2_752) { 2 }
            val entryId = map.getRecID(1)
            val position = BTreeEntry.readRecord(tree.nodes, entryId)
            val dataSize = tree.records.getFileSize()
            val nodeSize = tree.nodes.getFileSize()

            repeat(1_000) { update ->
                val expected = ByteArray(2_752) { update.toByte() }
                map[1] = expected
                assertEquals(entryId, map.getRecID(1))
                assertEquals(position, BTreeEntry.readRecord(tree.nodes, entryId))
                assertEquals(dataSize, tree.records.getFileSize())
                assertEquals(nodeSize, tree.nodes.getFileSize())
                assertContentEquals(expected, map[1] as ByteArray)
            }

            // An overwritten frame must never enter the retired-frame reuse queue.
            tree.commit()
            map[3] = ByteArray(2_752) { 3 }
            map.clearCache()
            assertContentEquals(ByteArray(2_752) { 999.toByte() }, map[1] as ByteArray)
            assertContentEquals(ByteArray(2_752) { 2 }, map[2] as ByteArray)
        }
    }

    @Test
    fun changedFrameLengthsUseTheExistingAllocationPath() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            map[1] = ByteArray(256) { 1 }
            val entryId = map.getRecID(1)
            var position = BTreeEntry.readRecord(tree.nodes, entryId)

            for (length in listOf(128, 512, 64)) {
                val previousSize = tree.records.getFileSize()
                val expected = ByteArray(length) { length.toByte() }
                map[1] = expected
                val replacement = BTreeEntry.readRecord(tree.nodes, entryId)
                assertNotEquals(position, replacement)
                assertTrue(tree.records.getFileSize() > previousSize)
                assertContentEquals(expected, map[1] as ByteArray)
                position = replacement
            }
        }
    }

    @Test
    fun defaultModeStillAllocatesReplacementFramesOfTheSameLength() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            // Exercise the original constructor signature with its unchanged default.
            val map = DiskBTreeMap<Int, Any?>(
                WeakReference(tree.nodes), WeakReference(tree.records), tree.header, Int::class.java
            )
            assertEquals(ValueUpdateMode.APPEND, map.valueUpdateMode)
            map[1] = ByteArray(256) { 1 }
            val entryId = map.getRecID(1)
            val firstPosition = BTreeEntry.readRecord(tree.nodes, entryId)
            val firstSize = tree.records.getFileSize()
            map[1] = ByteArray(256) { 2 }
            assertNotEquals(firstPosition, BTreeEntry.readRecord(tree.nodes, entryId))
            assertTrue(tree.records.getFileSize() > firstSize)
        }
    }

    @Test
    fun overwriteCapturesThePreviouslyPersistedValueBeforeItChanges() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            val before = ByteArray(256) { 1 }
            val after = ByteArray(256) { 2 }
            map[1] = before
            val entryId = map.getRecID(1)
            val result = map.putAndGet(1, after, preUpdate = null, capturePreviousValue = true)

            assertFalse(result.isInsert)
            assertEquals(entryId, result.recordId)
            assertContentEquals(before, result.previousValue as ByteArray)
            assertContentEquals(after, map[1] as ByteArray)
        }
    }

    @Test
    fun nullTransitionsAndRemovedEntryReinsertionPreserveValues() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            map[1] = ByteArray(256) { 1 }
            val entryId = map.getRecID(1)
            map[1] = null
            assertNull(map[1])
            assertEquals(BTreeEntry.NULL_RECORD, BTreeEntry.readRecord(tree.nodes, entryId))
            map[1] = ByteArray(256) { 2 }
            assertContentEquals(ByteArray(256) { 2 }, map[1] as ByteArray)
            map.remove(1)
            tree.commit()
            map[1] = ByteArray(256) { 3 }
            assertContentEquals(ByteArray(256) { 3 }, map[1] as ByteArray)
        }
    }

    @Test
    fun failedSerializationDoesNotModifyTheLiveValueOrAllocateAFrame() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            map[1] = ByteArray(256) { 1 }
            val entryId = map.getRecID(1)
            val position = BTreeEntry.readRecord(tree.nodes, entryId)
            val size = tree.records.getFileSize()

            assertFails { map[1] = FailingValue() }

            assertEquals(position, BTreeEntry.readRecord(tree.nodes, entryId))
            assertEquals(size, tree.records.getFileSize())
            assertContentEquals(ByteArray(256) { 1 }, map[1] as ByteArray)
            map[1] = ByteArray(256) { 2 }
            assertEquals(position, BTreeEntry.readRecord(tree.nodes, entryId))
            assertContentEquals(ByteArray(256) { 2 }, map[1] as ByteArray)
        }
    }

    @Test
    fun delegatingCustomStoreRetainsItsOriginalWriterOverride() {
        TreeFiles(temporaryFolder.newFolder(), storeType).use { tree ->
            val wrapped = CountingStore(tree.records)
            val map = DiskBTreeMap<Int, Any?>(
                WeakReference(tree.nodes), WeakReference(wrapped), tree.header,
                Int::class.java, ValueUpdateMode.OVERWRITE_SAME_SIZE
            )
            map[1] = ByteArray(256) { 1 }
            val firstPosition = BTreeEntry.readRecord(tree.nodes, map.getRecID(1))
            map[1] = ByteArray(256) { 2 }
            assertEquals(2, wrapped.objectWrites)
            assertNotEquals(firstPosition, BTreeEntry.readRecord(tree.nodes, map.getRecID(1)))
            assertContentEquals(ByteArray(256) { 2 }, map[1] as ByteArray)
        }
    }

    @Test
    fun overwrittenValuesAndPointersSurviveReopen() {
        assumeTrue(storeType != StoreType.IN_MEMORY)
        val directory = temporaryFolder.newFolder()
        var expectedPosition: Long
        var expectedEntry: Long
        var headerPosition: Long
        TreeFiles(directory, storeType).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            map[1] = ByteArray(256) { 1 }
            map[1] = ByteArray(256) { 2 }
            expectedEntry = map.getRecID(1)
            expectedPosition = BTreeEntry.readRecord(tree.nodes, expectedEntry)
            headerPosition = tree.header.position
            tree.commit()
        }

        TreeFiles(directory, storeType, headerPosition).use { tree ->
            val map = tree.map(ValueUpdateMode.OVERWRITE_SAME_SIZE)
            assertEquals(expectedEntry, map.getRecID(1))
            assertEquals(expectedPosition, BTreeEntry.readRecord(tree.nodes, expectedEntry))
            assertContentEquals(ByteArray(256) { 2 }, map[1] as ByteArray)
            val size = tree.records.getFileSize()
            map[1] = ByteArray(256) { 3 }
            assertEquals(expectedPosition, BTreeEntry.readRecord(tree.nodes, expectedEntry))
            assertEquals(size, tree.records.getFileSize())
        }
    }

    private class TreeFiles(directory: File, storeType: StoreType, existingHeader: Long? = null) : AutoCloseable {
        val nodes: Store = createStore(File(directory, "nodes"), storeType)
        val records: Store = createStore(File(directory, "records"), storeType)
        val header: Header = if (existingHeader == null) {
            Header().apply {
                position = nodes.allocate(Header.HEADER_SIZE)
                nodes.write(this, position)
            }
        } else {
            nodes.read(existingHeader, Header.HEADER_SIZE, Header()) as Header
        }

        fun map(mode: ValueUpdateMode) = DiskBTreeMap<Int, Any?>(
            WeakReference(nodes), WeakReference(records), header, Int::class.java, mode
        )

        fun commit() {
            records.prepareRetiredObjects()
            nodes.prepareRetiredObjects()
            records.commit()
            nodes.commit()
            records.publishRetiredObjects()
            nodes.publishRetiredObjects()
        }

        override fun close() {
            records.close()
            nodes.close()
        }

        private fun createStore(path: File, type: StoreType): Store = when (type) {
            StoreType.FILE -> FileChannelStore(path.absolutePath, null, false)
            StoreType.MEMORY_MAPPED_FILE -> MemoryMappedStore(path.absolutePath, null, false)
            StoreType.IN_MEMORY -> InMemoryStore(null, path.absolutePath)
        }
    }

    class FailingValue : BufferStreamable {
        override fun write(buffer: BufferStream) {
            buffer.putObject("partial serialization")
            error("Intentional serialization failure")
        }
    }

    private class CountingStore(private val delegate: Store) : Store by delegate {
        var objectWrites = 0
        override fun writeObject(value: Any?): Long {
            objectWrites++
            return delegate.writeObject(value)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<Any>> = StoreType.entries.map { arrayOf(it) }
    }
}
