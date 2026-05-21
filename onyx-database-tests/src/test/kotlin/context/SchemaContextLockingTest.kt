package context

import com.onyx.diskmap.store.StoreType
import com.onyx.entity.SystemEntity
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertTrue

class SchemaContextLockingTest {

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
}
