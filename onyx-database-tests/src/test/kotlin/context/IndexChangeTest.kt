package context

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.SystemAttribute
import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemIndex
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import entities.index.StringIdentifierEntityIndex
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
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
}
