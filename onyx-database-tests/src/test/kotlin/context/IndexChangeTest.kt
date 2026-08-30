package context

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.SystemAttribute
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemIndex
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import entities.index.StringIdentifierEntityIndex
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexChangeTest {

    @Test
    fun addingIndexTriggersRebuild() {
        val ctx = object : DefaultSchemaContext("instance", "mem") {
            val rebuilt = mutableListOf<String>()
            public override fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
                rebuilt += indexName
            }

            fun callCheck(old: SystemEntity, new: SystemEntity) = checkForIndexChanges(old, new)
        }

        val oldEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf()
        }
        val newEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf(SystemIndex().apply { name = "newIndex" })
        }

        ctx.callCheck(oldEntity, newEntity)
        assertEquals(listOf("newIndex"), ctx.rebuilt)
    }

    @Test
    fun removingIndexDoesNotTriggerRebuild() {
        val ctx = object : DefaultSchemaContext("instance", "mem") {
            val rebuilt = mutableListOf<String>()
            public override fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
                rebuilt += indexName
            }

            fun callCheck(old: SystemEntity, new: SystemEntity) = checkForIndexChanges(old, new)
        }

        val oldEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf(SystemIndex().apply { name = "oldIndex" })
        }
        val newEntity = SystemEntity().apply {
            name = "Entity"
            indexes = mutableListOf()
        }

        ctx.callCheck(oldEntity, newEntity)
        assertTrue(ctx.rebuilt.isEmpty())
    }

    @Test
    fun addingAttributeDoesNotTriggerUnchangedIndexRebuild() {
        val ctx = object : DefaultSchemaContext("instance", "mem") {
            val rebuilt = mutableListOf<String>()
            public override fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
                rebuilt += indexName
            }

            fun callCheck(old: SystemEntity, new: SystemEntity) = checkForIndexChanges(old, new)
        }

        val oldEntity = SystemEntity().apply {
            name = "Entity"
            attributes = mutableListOf(SystemAttribute(name = "indexed"))
            indexes = mutableListOf(SystemIndex(name = "indexed"))
        }
        val newEntity = SystemEntity().apply {
            name = "Entity"
            attributes = mutableListOf(
                SystemAttribute(name = "addedBeforeIndex"),
                SystemAttribute(name = "indexed")
            )
            indexes = mutableListOf(SystemIndex(name = "indexed"))
        }

        ctx.callCheck(oldEntity, newEntity)
        assertTrue(ctx.rebuilt.isEmpty())
    }

    @Test
    fun entityChangeUsesPreviousIndexesWhenCheckingRebuilds() {
        val location = Files.createTempDirectory("onyx-index-change").toFile()
        val ctx = object : DefaultSchemaContext("index-change-${System.nanoTime()}", location.path) {
            val rebuilt = mutableListOf<String>()
            public override fun rebuildIndex(systemEntity: SystemEntity, indexName: String) {
                rebuilt += indexName
            }

            fun callCheck(descriptor: EntityDescriptor, old: SystemEntity) = checkForEntityChanges(descriptor, old)
        }
        ctx.storeType = StoreType.IN_MEMORY

        val manager = EmbeddedPersistenceManager(ctx)
        manager.context = ctx
        ctx.start()

        try {
            val descriptor = EntityDescriptor(StringIdentifierEntityIndex::class.java)
            descriptor.context = ctx
            val oldEntity = SystemEntity(descriptor).apply {
                indexes = mutableListOf()
            }

            ctx.callCheck(descriptor, oldEntity)

            assertEquals(listOf("indexValue"), ctx.rebuilt)
        } finally {
            ctx.shutdown()
            location.deleteRecursively()
        }
    }

    @Test
    fun schemaChangeWaitsForIndexRebuildBeforeReturning() {
        val location = Files.createTempDirectory("onyx-index-rebuild-wait").toFile()
        val ctx = RebuildTestSchemaContext(
            "index-rebuild-wait-${System.nanoTime()}",
            location.path
        )
        ctx.storeType = StoreType.IN_MEMORY
        val manager = EmbeddedPersistenceManager(ctx)
        manager.context = ctx
        ctx.start()

        val rebuildStarted = CountDownLatch(1)
        val allowRebuildToFinish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val (oldEntity, newEntity) = indexRevisionPair(ctx)
            ctx.targetRebuild = {
                rebuildStarted.countDown()
                check(allowRebuildToFinish.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting for the test to release the schema index rebuild"
                }
            }

            val schemaChange = executor.submit { ctx.callCheck(oldEntity, newEntity) }
            assertTrue(rebuildStarted.await(5, TimeUnit.SECONDS), "Index rebuild did not start")
            try {
                assertFalse(
                    schemaChange.isDone,
                    "Schema initialization returned before its index rebuild completed"
                )
            } finally {
                allowRebuildToFinish.countDown()
            }
            schemaChange.get(5, TimeUnit.SECONDS)
        } finally {
            allowRebuildToFinish.countDown()
            executor.shutdownNow()
            ctx.shutdown()
            location.deleteRecursively()
        }
    }

    @Test
    fun schemaChangePropagatesIndexRebuildFailure() {
        val location = Files.createTempDirectory("onyx-index-rebuild-failure").toFile()
        val ctx = RebuildTestSchemaContext(
            "index-rebuild-failure-${System.nanoTime()}",
            location.path
        )
        ctx.storeType = StoreType.IN_MEMORY
        val manager = EmbeddedPersistenceManager(ctx)
        manager.context = ctx
        ctx.start()

        try {
            val (oldEntity, newEntity) = indexRevisionPair(ctx)
            ctx.targetRebuild = { throw IllegalStateException("deliberate rebuild failure") }

            val failure = assertFailsWith<IllegalStateException> {
                ctx.callCheck(oldEntity, newEntity)
            }
            assertEquals("deliberate rebuild failure", failure.message)
        } finally {
            ctx.shutdown()
            location.deleteRecursively()
        }
    }

    private fun indexRevisionPair(ctx: DefaultSchemaContext): Pair<SystemEntity, SystemEntity> {
        val descriptor = ctx.getBaseDescriptorForEntity(StringIdentifierEntityIndex::class.java)!!
        val oldEntity = SystemEntity(descriptor).apply { indexes = mutableListOf() }
        val newEntity = SystemEntity(descriptor)
        return oldEntity to newEntity
    }

    private class RebuildTestSchemaContext(contextId: String, location: String) :
        DefaultSchemaContext(contextId, location) {

        @Volatile
        var targetRebuild: (() -> Unit)? = null

        override fun getIndexInteractor(indexDescriptor: IndexDescriptor): IndexInteractor {
            val delegate = super.getIndexInteractor(indexDescriptor)
            val rebuild = targetRebuild
            if (
                rebuild == null ||
                indexDescriptor.entityDescriptor.entityClass != StringIdentifierEntityIndex::class.java ||
                indexDescriptor.name != "indexValue"
            ) {
                return delegate
            }

            return object : IndexInteractor by delegate {
                override fun rebuild() = rebuild()
            }
        }

        fun callCheck(old: SystemEntity, new: SystemEntity) = checkForIndexChanges(old, new)
    }
}
