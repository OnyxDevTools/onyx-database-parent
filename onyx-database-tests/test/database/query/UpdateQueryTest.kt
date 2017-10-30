package database.query

import com.onyx.extension.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class UpdateQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testExecuteUpdateQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
        val query = Query(AllAttributeForFetch::class.java, criteria, AttributeUpdate("stringValue", "B"))

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expecting 1 updated record")

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"))
        val listResults = manager.executeQuery<AllAttributeForFetch>(fetchQuery)

        val res = listResults[0]
        res.stringValue = "Some test strin3"
        manager.saveEntity<IManagedEntity>(res)

        assertEquals(1, listResults.size, "Expected 1 result")
    }

    @Test
    fun testExecuteUpdateMultipleQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria, AttributeUpdate("stringValue", "B"))

        query.firstRow = 2
        query.maxResults = 2

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 updated records")

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"))
        val listResults = manager.executeQuery<Any>(fetchQuery)
        assertEquals(2, listResults.size, "Expected 2 results")
    }

    @Test
    fun testExecuteUpdateMultipleFieldsQuery() {
        val updated = manager.from(AllAttributeForFetch::class)
                             .where("stringValue" startsWith "Some")
                             .set(("stringValue" to "B"), ("intValue" to 4))
                             .first(2)
                             .limit(2)
                             .update()

        assertEquals(2, updated, "Expected 2 updated records")

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B").and("intValue", QueryCriteriaOperator.EQUAL, 4))
        val listResults = manager.executeQuery<Any>(fetchQuery)
        assertEquals(2, listResults.size, "Expected 2 results")
    }

}
