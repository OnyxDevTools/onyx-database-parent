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

class MemoryMappedStoreCacheTest {

    @Test
    fun constructorOpenedStoreRemovesChunksOnClose() {
        val previousMax = MemoryMappedStore.maxCachedFileChunks
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-constructor")
        var store: MemoryMappedStore? = null

        try {
            MemoryMappedStore.maxCachedFileChunks = 2
            store = MemoryMappedStore(tempDirectory.resolve("constructor.db").toString(), null, false)

            assertTrue(MemoryMappedStore.cachedFileChunkCount > 0)
            assertTrue(store.close())
            store = null

            assertEquals(0, MemoryMappedStore.cachedFileChunkCount)
        } finally {
            store?.close()
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun evictsFileChunksGloballyAndRemapsData() {
        val previousMax = MemoryMappedStore.maxCachedFileChunks
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-cache")
        val stores = ArrayList<MemoryMappedStore>()

        try {
            MemoryMappedStore.maxCachedFileChunks = 2

            val first = openStore(tempDirectory.resolve("first.db")).also(stores::add)
            val second = openStore(tempDirectory.resolve("second.db")).also(stores::add)

            first.write(ByteBuffer.wrap(ByteArray(8) { 1 }), 0)
            first.write(ByteBuffer.wrap(ByteArray(8) { 2 }), 16)
            second.write(ByteBuffer.wrap(ByteArray(8) { 3 }), 0)

            assertTrue(MemoryMappedStore.cachedFileChunkCount <= 2)

            val remapped = ByteBuffer.allocate(8)
            first.read(remapped, 0)

            assertContentEquals(ByteArray(8) { 1 }, remapped.array())
            assertTrue(MemoryMappedStore.cachedFileChunkCount <= 2)

            stores.forEach {
                assertTrue(it.close())
            }
            stores.clear()

            assertEquals(0, MemoryMappedStore.cachedFileChunkCount)
        } finally {
            stores.forEach {
                it.close()
            }
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    private fun openStore(path: Path): MemoryMappedStore {
        val store = MemoryMappedStore()
        store.bufferSliceSize = 16
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
