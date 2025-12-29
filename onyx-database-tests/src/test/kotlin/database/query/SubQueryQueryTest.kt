package database.query

import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.query.inOp
import com.onyx.persistence.query.notIn
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.to
import com.onyx.persistence.query.select
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import entities.AllAttributeForFetchChild
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class SubQueryQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun `list resolves subquery ids`() {
        val subQuery = manager.from(AllAttributeForFetch::class)
            .where("stringValue" eq "A")

        val expectedIds = subQuery.list<AllAttributeForFetch>().mapNotNull { it.id }.sorted()

        val results = manager.from(AllAttributeForFetch::class)
            .where("id" inOp subQuery)
            .orderBy("id")
            .list<AllAttributeForFetch>()
            .mapNotNull { it.id }
            .sorted()

        assertEquals(expectedIds, results)
    }

    @Test
    fun `update applies subquery ids`() {
        val subQuery = manager.from(AllAttributeForFetch::class)
            .where("stringValue" eq "A")

        val targetIds = subQuery.list<AllAttributeForFetch>().mapNotNull { it.id }

        val updatedCount = manager.from(AllAttributeForFetch::class)
            .where("id" inOp subQuery)
            .set("stringValue" to "UPDATED")
            .update()

        assertEquals(targetIds.size, updatedCount)

        val updatedRecords = manager.from(AllAttributeForFetch::class)
            .where("id" inOp targetIds)
            .list<AllAttributeForFetch>()

        assertTrue(updatedRecords.all { it.stringValue == "UPDATED" })
    }

    @Test
    fun `delete applies subquery ids`() {
        val subQuery = manager.from(AllAttributeForFetch::class)
            .where("longValue" eq 322L)

        val deletedIds = subQuery.list<AllAttributeForFetch>().mapNotNull { it.id }

        val deleteCount = manager.from(AllAttributeForFetch::class)
            .where("id" inOp subQuery)
            .delete()

        assertEquals(deletedIds.size, deleteCount)

        val remaining = manager.from(AllAttributeForFetch::class)
            .where("id" notIn deletedIds)
            .list<AllAttributeForFetch>()

        assertTrue(remaining.none { it.id in deletedIds })
    }

    @Test
    fun `subquery selections with a single map entry resolve to ids`() {
        val parent = AllAttributeForFetch().apply {
            id = "MAP_PARENT"
            stringValue = "MapSubQuery"
            child = AllAttributeForFetchChild().apply {
                someOtherField = "map-child"
            }
        }

        manager.save(parent)

        val subQuery = manager.select("child.id")
            .from(AllAttributeForFetch::class)
            .where("stringValue" eq "MapSubQuery")

        val childIds = manager.from(AllAttributeForFetchChild::class)
            .where("id" inOp subQuery)
            .list<AllAttributeForFetchChild>()
            .mapNotNull { it.id }

        assertEquals(listOf(parent.child?.id), childIds)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> =
            arrayListOf(EmbeddedPersistenceManagerFactory::class)
    }
}
