package database.query

import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gte
import com.onyx.persistence.query.startsWith
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import entities.AllAttributeForFetchChild
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class DeleteQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testExecuteDeleteQuery() {
        val results = manager.from(AllAttributeForFetch::class).where("stringValue" eq "Some test string3").delete()
        assertEquals(1, results, "One entity should have been deleted")
        assertEquals(0,  manager.from(AllAttributeForFetch::class).where("stringValue" eq "Some test string3").count(), "Deleted entity should not exist")
    }

    @Test
    fun testExecuteDeleteRangeQuery() {
       val results = manager.from(AllAttributeForFetch::class)
                            .where("stringValue" startsWith  "Some")
                            .first(2)
                            .limit(1)
                            .delete()
        assertEquals(1, results, "One entity should have been deleted")

        val remaining = manager.from(AllAttributeForFetch::class)
                               .where("stringValue" startsWith  "Some")
                               .first(2)
                               .limit(1)
                               .count()
        assertEquals(3, remaining, "There should be 3 remaining entities matching criteria")
    }

    @Test(expected = NoResultsException::class)
    fun testCascadeRelationship() {
        val results = manager.from(AllAttributeForFetch::class)
                             .where(("intPrimitive" gte  0) and ("child.someOtherField" startsWith "HIYA"))
                             .list<AllAttributeForFetch>()

        val deleted = manager.from(AllAttributeForFetch::class)
                             .where(("intPrimitive" gte  0) and ("child.someOtherField" startsWith "HIYA"))
                             .delete()

        assertEquals(2, deleted, "2 entities should have been deleted")
        assertEquals(0, manager.from(AllAttributeForFetch::class)
                               .where(("intPrimitive" gte  0) and ("child.someOtherField" startsWith "HIYA"))
                               .count()
                      , "Entities should have been deleted")

        val child = AllAttributeForFetchChild()
        child.id = results[0].child?.id
        manager.find<IManagedEntity>(child)
    }
}