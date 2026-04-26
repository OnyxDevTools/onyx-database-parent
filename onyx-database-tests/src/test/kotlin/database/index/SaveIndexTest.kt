package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.identifiers.StringIdentifierEntity
import entities.index.StringIdentifierEntityIndex
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Arrays
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 1/23/15.
 */
@RunWith(Parameterized::class)
class SaveIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun saveStringIndexUpdateTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")

        entity.indexValue = "BLA"
        manager.saveEntity<IManagedEntity>(entity)

        results = manager.executeQuery(findQuery)
        assertTrue(results.isEmpty(), "Indexed Value was not updated")

        val findQuery2 = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "BLA"))
        results = manager.executeQuery(findQuery2)

        assertEquals(1,results.size, "New Indexed entity was not found")
    }

    @Test
    fun rebuildStringIndexUsesIndexedAttributeValue() {
        if (factoryClass != EmbeddedPersistenceManagerFactory::class) {
            return
        }

        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        assertEquals(1, manager.executeQuery<StringIdentifierEntityIndex>(findQuery).size, "Indexed entity was not found before rebuild")

        val descriptor = factory.schemaContext.getDescriptorForEntity(StringIdentifierEntityIndex::class.java, "")
        val indexInteractor = factory.schemaContext.getIndexInteractor(descriptor.indexes["indexValue"]!!)
        indexInteractor.clear()

        assertTrue(manager.executeQuery<StringIdentifierEntityIndex>(findQuery).isEmpty(), "Index was not cleared")

        indexInteractor.rebuild()

        val results = manager.executeQuery<StringIdentifierEntityIndex>(findQuery)
        assertEquals(1, results.size, "Indexed entity was not found after rebuild")
        assertEquals("A", results.first().identifier, "Rebuilt index returned the wrong entity")
    }

    @Test
    fun saveStringIndexDeleteTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")

        entity.indexValue = "BLA"
        manager.deleteEntity(entity)

        results = manager.executeQuery(findQuery)
        assertTrue(results.isEmpty(), "Indexed Value was not deleted")
    }

    @Test
    fun saveStringIndexDeleteQueryTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")

        val deleteQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        manager.executeDelete(deleteQuery)

        results = manager.executeQuery(findQuery)
        assertTrue(results.isEmpty(), "Indexed Value was not deleted")
    }

    @Test
    fun saveStringIndexUpdateQueryTest() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        var results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")

        val updateQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"), AttributeUpdate("indexValue", "HIYA"))
        manager.executeUpdate(updateQuery)

        results = manager.executeQuery(findQuery)
        assertTrue(results.isEmpty(), "Indexed Value was not updated")


        val updatedFindQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "HIYA"))

        results = manager.executeQuery(updatedFindQuery)
        assertEquals(1,results.size, "New Indexed entity was not found")
    }

    @Test
    fun testSaveWithExistingFullScanPrior() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        val results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")
    }

    @Test
    fun testSaveWithExistingFullScanPriorWitIn() {
        val entity = StringIdentifierEntityIndex()
        entity.identifier = "A"
        entity.indexValue = "INDEX VALUE"
        manager.saveEntity<IManagedEntity>(entity)

        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.IN, Arrays.asList("INDEX VALUE")))
        val results = manager.executeQuery<StringIdentifierEntity>(findQuery)

        assertEquals(1,results.size, "Indexed entity was not found")
    }
}
