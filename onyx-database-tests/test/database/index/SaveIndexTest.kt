package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.database.base.DatabaseBaseTest
import entities.identifiers.StringIdentifierEntity
import entities.index.StringIdentifierEntityIndex
import org.junit.After
import org.junit.Before
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

    @Before
    fun before() {
        initialize()
    }

    @After
    fun after() {
        shutdown()
    }

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
