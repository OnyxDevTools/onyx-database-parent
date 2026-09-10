package diskmap

import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.impl.DiskBTreeMap
import com.onyx.diskmap.store.Store
import com.onyx.diskmap.store.StoreType
import com.onyx.diskmap.store.impl.EncryptedFileChannelStore
import com.onyx.diskmap.store.impl.EncryptedMemoryMappedStore
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import entities.SimpleEntity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class EncryptedBTreeValueUpdateModeTest(private val storeType: StoreType) {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun encryptedManagedEntityUpdatesReuseFramesAndRemainEncryptedAfterReopen() {
        val directory = temporaryFolder.newFolder()
        val database = EmbeddedPersistenceManagerFactory(
            File(directory, "schema").absolutePath, addShutdownHook = false
        )
        database.storeType = storeType
        database.encryptDatabase = true
        database.initialize()
        try {
            val initialName = "private-material-" + "A".repeat(96)
            // Register the managed entity's schema for the encrypted serializer.
            database.persistenceManager.saveEntity(entity(initialName))
            val mapPath = File(directory, "encrypted-values").absolutePath
            var maps = DefaultDiskMapFactory(mapPath, storeType, database.schemaContext)
            try {
                var map = openMap(maps)
                assertTrue(
                    map.records is EncryptedFileChannelStore || map.records is EncryptedMemoryMappedStore
                )
                map[1] = entity(initialName)
                val entryId = map.getRecID(1)
                val position = BTreeEntry.readRecord(map.fileStore, entryId)
                val originalSize = map.records.getFileSize()
                assertEncrypted(map.records, position, initialName)

                val firstReplacement = "private-material-" + "B".repeat(96)
                val result = map.putAndGet(
                    1, entity(firstReplacement), preUpdate = null, capturePreviousValue = true
                )
                assertEquals(initialName, (result.previousValue as SimpleEntity).name)
                assertEquals(position, BTreeEntry.readRecord(map.fileStore, entryId))
                assertEquals(originalSize, map.records.getFileSize())
                assertEquals(firstReplacement, map[1]?.name)
                assertEncrypted(map.records, position, firstReplacement)

                repeat(50) { index ->
                    val replacement = "private-material-" + ('A' + index % 26).toString().repeat(96)
                    map[1] = entity(replacement)
                    assertEquals(position, BTreeEntry.readRecord(map.fileStore, entryId))
                    assertEquals(originalSize, map.records.getFileSize())
                    assertEquals(replacement, map[1]?.name)
                    assertEncrypted(map.records, position, replacement)
                }

                // Ciphertext size changes retain append behavior, for both growth and shrinkage.
                var previousPosition = position
                for (length in listOf(192, 32)) {
                    val previousSize = map.records.getFileSize()
                    val replacement = "private-material-" + "Z".repeat(length)
                    map[1] = entity(replacement)
                    val updatedPosition = BTreeEntry.readRecord(map.fileStore, entryId)
                    assertNotEquals(previousPosition, updatedPosition)
                    assertTrue(map.records.getFileSize() > previousSize)
                    assertEquals(replacement, map[1]?.name)
                    assertEncrypted(map.records, updatedPosition, replacement)
                    previousPosition = updatedPosition
                }

                maps.commit()
                maps.close()
                maps = DefaultDiskMapFactory(mapPath, storeType, database.schemaContext)
                map = openMap(maps)
                val expected = "private-material-" + "Z".repeat(32)
                assertEquals(entryId, map.getRecID(1))
                assertEquals(previousPosition, BTreeEntry.readRecord(map.fileStore, entryId))
                assertEquals(expected, map[1]?.name)
                assertEquals("fixed-id", map[1]?.simpleId)
                assertEncrypted(map.records, previousPosition, expected)

                val reopenedSize = map.records.getFileSize()
                map[1] = entity("private-material-" + "Y".repeat(32))
                assertEquals(previousPosition, BTreeEntry.readRecord(map.fileStore, entryId))
                assertEquals(reopenedSize, map.records.getFileSize())
                assertEquals("private-material-" + "Y".repeat(32), map[1]?.name)
            } finally {
                maps.close()
            }
        } finally {
            database.close()
        }
    }

    private fun openMap(factory: DefaultDiskMapFactory): DiskBTreeMap<Int, SimpleEntity> =
        factory.getHashMap(Int::class.java, "values", ValueUpdateMode.OVERWRITE_SAME_SIZE)

    private fun entity(value: String) = SimpleEntity().apply {
        simpleId = "fixed-id"
        name = value
    }

    private fun assertEncrypted(store: Store, position: Long, secret: String) {
        val size = ByteBuffer.allocate(Int.SIZE_BYTES).also { store.read(it, position) }.getInt(0)
        val payload = ByteBuffer.allocate(size)
        store.read(payload, position + Int.SIZE_BYTES)
        assertFalse(payload.array().toString(Charsets.ISO_8859_1).contains(secret))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<Any>> = listOf(
            arrayOf(StoreType.FILE), arrayOf(StoreType.MEMORY_MAPPED_FILE)
        )
    }
}
