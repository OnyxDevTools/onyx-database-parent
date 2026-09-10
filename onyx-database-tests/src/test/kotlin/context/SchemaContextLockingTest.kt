package context

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemPartitionEntry
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import entities.partition.BasicPartitionEntity
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchemaContextLockingTest {

    @Test
    fun descriptorIsPublishedOnlyAfterInitializationCompletes() {
        val location = Files.createTempDirectory("onyx-descriptor-publication").toFile()
        val context = ObservableSchemaContext("descriptor-publication-${System.nanoTime()}", location.path)
        context.storeType = StoreType.IN_MEMORY
        val manager = EmbeddedPersistenceManager(context)
        manager.context = context
        context.start()

        val partition = 982734L
        val firstResult = AtomicReference<EntityDescriptor>()
        val secondResult = AtomicReference<EntityDescriptor>()
        val firstFailure = AtomicReference<Throwable>()
        val secondFailure = AtomicReference<Throwable>()
        val firstCompleted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondCompleted = CountDownLatch(1)
        var first: Thread? = null
        var second: Thread? = null

        try {
            context.pausePartitionInitialization(partition)
            first = thread(name = "first-descriptor-initializer") {
                try {
                    firstResult.set(context.getDescriptorForEntity(BasicPartitionEntity::class.java, partition))
                } catch (throwable: Throwable) {
                    firstFailure.set(throwable)
                } finally {
                    firstCompleted.countDown()
                }
            }

            assertTrue(
                context.partitionInitializationReached.await(5, TimeUnit.SECONDS),
                "The first lookup did not reach partition initialization"
            )

            second = thread(name = "concurrent-descriptor-reader") {
                secondStarted.countDown()
                try {
                    secondResult.set(context.getDescriptorForEntity(BasicPartitionEntity::class.java, partition))
                } catch (throwable: Throwable) {
                    secondFailure.set(throwable)
                } finally {
                    secondCompleted.countDown()
                }
            }

            assertTrue(secondStarted.await(1, TimeUnit.SECONDS), "The concurrent lookup did not start")
            assertFalse(
                secondCompleted.await(100, TimeUnit.MILLISECONDS),
                "A descriptor must not be visible before its partition metadata is initialized"
            )

            context.releasePartitionInitialization.countDown()
            assertTrue(firstCompleted.await(5, TimeUnit.SECONDS), "The descriptor initializer did not complete")
            assertTrue(secondCompleted.await(5, TimeUnit.SECONDS), "The waiting descriptor lookup did not complete")
            firstFailure.get()?.let { throw it }
            secondFailure.get()?.let { throw it }
            assertTrue(firstResult.get() === secondResult.get(), "Concurrent lookups must share one descriptor instance")
            assertEquals(1, context.partitionInitializationCount.get())
        } finally {
            context.releasePartitionInitialization.countDown()
            first?.join(5000)
            second?.join(5000)
            context.shutdown()
            location.deleteRecursively()
        }
    }

    @Test
    fun cachedDescriptorLookupDoesNotRequireDescriptorInitializationMonitor() {
        val location = Files.createTempDirectory("onyx-descriptor-locking").toFile()
        val context = ObservableSchemaContext("descriptor-locking-${System.nanoTime()}", location.path)
        context.storeType = StoreType.IN_MEMORY
        val manager = EmbeddedPersistenceManager(context)
        manager.context = context
        context.start()

        val releaseMonitor = CountDownLatch(1)
        try {
            val expected = context.getBaseDescriptorForEntity(SystemPartitionEntry::class.java)!!
            val monitorHeld = CountDownLatch(1)

            val holder = thread(name = "descriptor-initialization-monitor-holder") {
                context.withDescriptorInitializationMonitor {
                    monitorHeld.countDown()
                    releaseMonitor.await(5, TimeUnit.SECONDS)
                }
            }

            assertTrue(monitorHeld.await(1, TimeUnit.SECONDS), "The test did not acquire the descriptor monitor")

            val completed = CountDownLatch(1)
            val result = AtomicReference<EntityDescriptor>()
            val failure = AtomicReference<Throwable>()
            val worker = thread(name = "cached-descriptor-reader") {
                try {
                    result.set(context.getBaseDescriptorForEntity(SystemPartitionEntry::class.java))
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                } finally {
                    completed.countDown()
                }
            }

            assertTrue(
                completed.await(1, TimeUnit.SECONDS),
                "A cached descriptor lookup must not wait for another descriptor initialization"
            )
            failure.get()?.let { throw it }
            assertTrue(result.get() === expected, "The ready descriptor cache must return the initialized instance")

            releaseMonitor.countDown()
            holder.join(1000)
            worker.join(1000)
        } finally {
            releaseMonitor.countDown()
            context.shutdown()
            location.deleteRecursively()
        }
    }

    @Test
    fun getDataFileDoesNotRequireSchemaContextMonitor() {
        val location = Files.createTempDirectory("onyx-schema-context-locking").toFile()
        val context = DefaultSchemaContext("schema-locking-${System.nanoTime()}", location.path)
        context.storeType = StoreType.IN_MEMORY
        val manager = EmbeddedPersistenceManager(context)
        manager.context = context
        context.start()

        val releaseMonitor = CountDownLatch(1)
        try {
            val descriptor = context.getBaseDescriptorForEntity(SystemEntity::class.java)!!
            val monitorHeld = CountDownLatch(1)

            val holder = thread(name = "schema-context-monitor-holder") {
                synchronized(context) {
                    monitorHeld.countDown()
                    releaseMonitor.await(5, TimeUnit.SECONDS)
                }
            }

            assertTrue(monitorHeld.await(1, TimeUnit.SECONDS), "The test did not acquire the schema context monitor")

            val completed = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val worker = thread(name = "schema-context-data-file-worker") {
                try {
                    context.getDataFile(descriptor)
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                } finally {
                    completed.countDown()
                }
            }

            assertTrue(completed.await(1, TimeUnit.SECONDS), "getDataFile should not wait for the schema context monitor")
            failure.get()?.let { throw it }

            releaseMonitor.countDown()
            holder.join(1000)
            worker.join(1000)
        } finally {
            releaseMonitor.countDown()
            context.shutdown()
            location.deleteRecursively()
        }
    }

    private class ObservableSchemaContext(contextId: String, location: String) :
        DefaultSchemaContext(contextId, location) {

        val partitionInitializationReached = CountDownLatch(1)
        val releasePartitionInitialization = CountDownLatch(1)
        val partitionInitializationCount = AtomicInteger()
        @Volatile private var pausedPartition: String? = null

        fun pausePartitionInitialization(partition: Any) {
            pausedPartition = partition.toString()
        }

        override fun checkForValidDescriptorPartition(descriptor: EntityDescriptor) {
            if (descriptor.entityClass == BasicPartitionEntity::class.java &&
                descriptor.partition?.partitionValue == pausedPartition
            ) {
                partitionInitializationCount.incrementAndGet()
                partitionInitializationReached.countDown()
                check(releasePartitionInitialization.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to finish descriptor initialization"
                }
            }
            super.checkForValidDescriptorPartition(descriptor)
        }

        fun withDescriptorInitializationMonitor(block: () -> Unit) = synchronized(descriptors) {
            block()
        }
    }
}
