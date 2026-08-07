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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

            context.storeType = StoreType.MEMORY_MAPPED_FILE

            assertTrue(context.currentTransactionStore() is MemoryMappedTransactionStore)
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
        val initialCachedFileChunkCount = MemoryMappedStore.cachedFileChunkCount
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-rotation")
        val transactionStore = SmallJournalMemoryMappedTransactionStore(tempDirectory.toString())

        try {
            MemoryMappedStore.maxCachedFileChunks = 128

            val transactionFile = transactionStore.getTransactionFile()
            transactionFile.write(ByteBuffer.wrap(ByteArray(256) { 3 }))

            assertTrue(MemoryMappedStore.cachedFileChunkCount > initialCachedFileChunkCount)

            val rotatedTransactionFile = transactionStore.getTransactionFile()

            assertTrue(transactionFile !== rotatedTransactionFile)
            transactionStore.close()

            assertFalse(transactionFile.isOpen)
            assertEquals(initialCachedFileChunkCount, MemoryMappedStore.cachedFileChunkCount)
            assertEquals(256L, Files.size(tempDirectory.resolve("wal").resolve("0.wal")))
        } finally {
            transactionStore.close()
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun rotatingWalFileIsSealedAndFinalizedWithoutBlockingTheWriter() {
        val previousMax = MemoryMappedStore.maxCachedFileChunks
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-async-rotation")
        val finalizationStarted = CountDownLatch(1)
        val allowFinalization = CountDownLatch(1)
        val rotationExecutor = Executors.newSingleThreadExecutor()
        val transactionStore = object : SmallJournalMemoryMappedTransactionStore(tempDirectory.toString()) {
            override fun finalizeWalFile(walFile: FileChannel) {
                finalizationStarted.countDown()
                check(allowFinalization.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to finalize the rotated WAL file"
                }
                super.finalizeWalFile(walFile)
            }
        }

        try {
            MemoryMappedStore.maxCachedFileChunks = 1
            val firstWalFile = transactionStore.getTransactionFile()
            firstWalFile.write(ByteBuffer.wrap(ByteArray(256) { 4 }))

            val rotation = rotationExecutor.submit<FileChannel> {
                transactionStore.getTransactionFile()
            }
            val secondWalFile = rotation.get(5, TimeUnit.SECONDS)

            assertTrue(firstWalFile !== secondWalFile)
            assertTrue(finalizationStarted.await(5, TimeUnit.SECONDS))
            assertTrue(firstWalFile.isOpen)
            assertFailsWith<ClosedChannelException> {
                firstWalFile.write(ByteBuffer.wrap(byteArrayOf(9)))
            }

            secondWalFile.write(ByteBuffer.wrap(byteArrayOf(5, 6, 7)))
            allowFinalization.countDown()
            transactionStore.close()

            assertFalse(firstWalFile.isOpen)
            assertFalse(secondWalFile.isOpen)
            assertEquals(256L, Files.size(tempDirectory.resolve("wal").resolve("0.wal")))
            assertEquals(3L, Files.size(tempDirectory.resolve("wal").resolve("1.wal")))
        } finally {
            allowFinalization.countDown()
            runCatching { transactionStore.close() }
            rotationExecutor.shutdownNow()
            MemoryMappedStore.maxCachedFileChunks = previousMax
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun asynchronousWalFinalizationFailureIsReported() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-async-failure")
        val finalizationFailed = CountDownLatch(1)
        val failNextFinalization = AtomicBoolean(true)
        val transactionStore = object : SmallJournalMemoryMappedTransactionStore(tempDirectory.toString()) {
            override fun finalizeWalFile(walFile: FileChannel) {
                super.finalizeWalFile(walFile)
                if (failNextFinalization.compareAndSet(true, false)) {
                    finalizationFailed.countDown()
                    throw IOException("Injected asynchronous WAL finalization failure")
                }
            }
        }

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(ByteArray(256) { 8 }))
            transactionStore.getTransactionFile()

            assertTrue(finalizationFailed.await(5, TimeUnit.SECONDS))
            assertFailsWith<com.onyx.exception.TransactionException> {
                transactionStore.close()
            }
        } finally {
            runCatching { transactionStore.close() }
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

    private open class SmallJournalMemoryMappedTransactionStore(location: String) :
        MemoryMappedTransactionStore(location) {
        protected override val maxJournalSize: Long = 128L
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
