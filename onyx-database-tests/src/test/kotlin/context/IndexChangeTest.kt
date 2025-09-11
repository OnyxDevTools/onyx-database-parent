package context

import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemIndex
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.onyx.persistence.context.impl.DefaultSchemaContext

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
}
