package context

import com.onyx.entity.SystemEntity
import com.onyx.entity.SystemIndex
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from

class IndexChangeTest {

    /* --------------------- Your original smoke tests --------------------- */

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
    fun createDb() {
        val factory = EmbeddedPersistenceManagerFactory("/Users/tosborn/Desktop/tesat.onx")
        factory.initialize()
        val db = factory.persistenceManager

        val existing = db.from<TestRecord>().where("attributeB" eq "5").list<TestRecord>()
        println(existing)

        for (i in 0..1000) {
            db.saveEntity(
                TestRecord(
                    id = i.toLong(),
                    attributeA = i,
                    attributeB = i.toString()
                )
            )
        }

        db.from<TestRecord>().forEach<TestRecord> { item  ->
            true
        }

//        db.from<TestRecord>().delete()
    }
}

@Entity
data class TestRecord(
    @Identifier
    var id: Long = 0,
    @Attribute
    var attributeA: Int = 0,
    @Attribute
    @Index
    var attributeB: String = ""
) : ManagedEntity()
