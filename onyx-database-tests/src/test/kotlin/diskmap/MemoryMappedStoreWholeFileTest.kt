package diskmap

import com.onyx.diskmap.store.impl.MemoryMappedStore
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryMappedStoreWholeFileTest {

    @Test
    fun closeTruncatesMappedFileToAllocatedSize() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-truncate")
        var store: MemoryMappedStore? = null

        try {
            val path = tempDirectory.resolve("truncate.db")
            store = MemoryMappedStore()
            assertTrue(store.open(path.toAbsolutePath().toString()))

            store.allocate(java.lang.Long.BYTES)
            val payload = ByteArray(128) { 1 }
            val payloadPosition = store.allocate(payload.size)
            store.write(ByteBuffer.wrap(payload), payloadPosition)
            val allocatedSize = store.getFileSize()

            assertEquals((java.lang.Long.BYTES + payload.size).toLong(), allocatedSize)
            assertTrue(Files.size(path) >= allocatedSize)
            assertTrue(store.close())
            store = null

            assertEquals(allocatedSize, Files.size(path))
        } finally {
            store?.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun constructorOpenedStoreReleasesMappingAndTruncatesOnClose() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-constructor")
        var store: MemoryMappedStore? = null

        try {
            val path = tempDirectory.resolve("constructor.db")
            store = MemoryMappedStore(path.toString(), null, false)

            assertTrue(Files.size(path) >= java.lang.Long.BYTES)
            assertTrue(store.close())
            store = null

            assertEquals(java.lang.Long.BYTES.toLong(), Files.size(path))
        } finally {
            store?.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun growsWholeFileMappingAndPreservesData() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-whole-file")
        val path = tempDirectory.resolve("growth.db")
        var store: MemoryMappedStore? = null

        try {
            store = openStore(path)
            store.allocate(java.lang.Long.BYTES)
            val payloadPosition = store.allocate(80)
            store.write(ByteBuffer.wrap(ByteArray(8) { 1 }), payloadPosition)
            store.write(ByteBuffer.wrap(ByteArray(8) { 2 }), payloadPosition + 64)

            val remapped = ByteBuffer.allocate(8)
            store.read(remapped, payloadPosition)

            assertContentEquals(ByteArray(8) { 1 }, remapped.array())
            assertTrue(Files.size(path) >= java.lang.Long.BYTES + 80)
            assertTrue(store.close())
            store = null

            store = MemoryMappedStore(path.toString(), null, false)
            val afterReopen = ByteBuffer.allocate(8)
            store.read(afterReopen, payloadPosition + 64)
            assertContentEquals(ByteArray(8) { 2 }, afterReopen.array())
        } finally {
            store?.close()
            deleteDirectory(tempDirectory)
        }
    }

    private fun openStore(path: Path): MemoryMappedStore {
        val store = MemoryMappedStore()
        assertTrue(store.open(path.toAbsolutePath().toString()))
        return store
    }

    private fun deleteDirectory(path: Path) {
        val files = Files.walk(path)
        try {
            files.sorted(Comparator.reverseOrder()).forEach {
                Files.deleteIfExists(it)
            }
        } finally {
            files.close()
        }
    }
}
