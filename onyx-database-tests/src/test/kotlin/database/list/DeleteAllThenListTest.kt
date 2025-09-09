package database.list

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.from
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetch
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Verify that deleting all entities leaves the skiplist in a valid state
 * and iterating over a list query on the emptied set does not throw.
 */
@RunWith(Parameterized::class)
class DeleteAllThenListTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @After
    fun cleanup() {
        manager.from(AllAttributeForFetch::class).delete()
    }

    @Test
    fun testListAfterDeletingAll() {
        // insert some data
        for (i in 0 until 10) {
            val entity = AllAttributeForFetch()
            entity.id = "ID" + i
            entity.stringValue = "value" + i
            manager.saveEntity<IManagedEntity>(entity)
        }

        // remove everything
        manager.from(AllAttributeForFetch::class).delete()

        // executing a list query and iterating the results should be safe
        val results = manager.from(AllAttributeForFetch::class).list<AllAttributeForFetch>()
        var count = 0
        for (entity in results) {
            count++
        }
        assertEquals(0, count, "All entities should have been removed")
    }
}
