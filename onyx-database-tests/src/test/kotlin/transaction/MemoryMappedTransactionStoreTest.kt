package transaction

import com.onyx.diskmap.store.StoreType
import com.onyx.diskmap.store.impl.MemoryMappedStore
import com.onyx.interactors.transaction.TransactionStore
import com.onyx.interactors.transaction.impl.DefaultTransactionStore
import com.onyx.interactors.transaction.impl.MemoryMappedTransactionStore
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import entities.AllAttributeEntity
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryMappedTransactionStoreTest {

    @Test
    fun memoryMappedStoreTypeRebuildsTransactionStore() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-context")
        val context = InspectableSchemaContext(
            "memory-mapped-transaction-${System.nanoTime()}",
            tempDirectory.toString()
        )

        try {
            assertTrue(context.currentTransactionStore() is DefaultTransactionStore)

            context.storeType = StoreType.MEMORY_MAPPED_FILE

            assertTrue(context.currentTransactionStore() is MemoryMappedTransactionStore)

            context.storeType = StoreType.FILE

            assertTrue(context.currentTransactionStore() is DefaultTransactionStore)
        } finally {
            context.shutdown()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun transactionMappingsUseSharedCacheAndUnmapOnClose() {
        val previousMax = MemoryMappedStore.maxCachedFileChunks
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-cache")
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())
        val dataStore = MemoryMappedStore()

        try {
            MemoryMappedStore.maxCachedFileChunks = 1

            val transactionFile = transactionStore.getTransactionFile()
            transactionFile.write(ByteBuffer.wrap(ByteArray(128) { 1 }))

            assertEquals(1, MemoryMappedStore.cachedFileChunkCount)

            dataStore.bufferSliceSize = 16
            assertTrue(dataStore.open(tempDirectory.resolve("data.db").toString()))
            dataStore.write(ByteBuffer.wrap(ByteArray(8) { 2 }), 0)

            assertTrue(MemoryMappedStore.cachedFileChunkCount <= 1)

            transactionStore.close()
            assertTrue(dataStore.close())

            assertEquals(0, MemoryMappedStore.cachedFileChunkCount)
            assertEquals(128L, Files.size(tempDirectory.resolve("wal").resolve("0.wal")))
        } finally {
            transactionStore.close()
            dataStore.close()
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun rotatingWalFileUnmapsDiscardedMappings() {
        val previousMax = MemoryMappedStore.maxCachedFileChunks
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-rotation")
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            MemoryMappedStore.maxCachedFileChunks = 128

            val transactionFile = transactionStore.getTransactionFile()
            val payload = ByteBuffer.wrap(ByteArray(1024 * 1024) { 3 })
            repeat(21) {
                payload.rewind()
                transactionFile.write(payload)
            }

            assertTrue(MemoryMappedStore.cachedFileChunkCount > 0)

            val rotatedTransactionFile = transactionStore.getTransactionFile()

            assertTrue(transactionFile !== rotatedTransactionFile)
            assertEquals(0, MemoryMappedStore.cachedFileChunkCount)
        } finally {
            transactionStore.close()
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun recoveryIgnoresOpenWalMappedPadding() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-recovery")
        val sourceLocation = tempDirectory.resolve("source.oxd")
        val recoveredLocation = tempDirectory.resolve("recovered.oxd")
        val contextSuffix = System.nanoTime()
        var sourceFactory: EmbeddedPersistenceManagerFactory? = null
        var recoveredFactory: EmbeddedPersistenceManagerFactory? = null

        try {
            sourceFactory = EmbeddedPersistenceManagerFactory(
                sourceLocation.toString(),
                "source-$contextSuffix",
                addShutdownHook = false
            )
            sourceFactory.storeType = StoreType.MEMORY_MAPPED_FILE
            sourceFactory.isEnableJournaling = true
            sourceFactory.initialize()

            val savedEntity = AllAttributeEntity()
            savedEntity.id = "memory-mapped-recovery"
            savedEntity.intValue = 7
            sourceFactory.persistenceManager.saveEntity<IManagedEntity>(savedEntity)

            recoveredFactory = EmbeddedPersistenceManagerFactory(
                recoveredLocation.toString(),
                "recovered-$contextSuffix",
                addShutdownHook = false
            )
            recoveredFactory.storeType = StoreType.MEMORY_MAPPED_FILE
            recoveredFactory.initialize()

            recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(sourceLocation.resolve("wal").toString()) { true }

            val recovered = recoveredFactory.persistenceManager.findById<AllAttributeEntity>(
                AllAttributeEntity::class.java,
                savedEntity.id!!
            )

            assertEquals(savedEntity.id, recovered?.id)
        } finally {
            sourceFactory?.close()
            recoveredFactory?.close()
            deleteDirectory(tempDirectory)
        }
    }

    private class InspectableSchemaContext(contextId: String, location: String) : DefaultSchemaContext(contextId, location) {
        fun currentTransactionStore(): TransactionStore? = transactionStore
    }

    companion object {
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
}
