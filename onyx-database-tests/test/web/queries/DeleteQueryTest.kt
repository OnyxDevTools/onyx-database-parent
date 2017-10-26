package web.queries

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeForFetch
import entities.AllAttributeForFetchChild
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.PrePopulatedDatabaseTest

/**
 * Created by timothy.osborn on 1/10/15.
 */
@Category(WebServerTests::class)
class DeleteQueryTest : PrePopulatedDatabaseTest() {
    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteDeleteQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
        val query = Query(AllAttributeForFetch::class.java, criteria)

        val results = manager.executeDelete(query)
        Assert.assertTrue(results == 1)

        val fetchQuery = Query(AllAttributeForFetch::class.java, criteria)
        val listResults = manager.executeQuery<AllAttributeForFetch>(fetchQuery)

        Assert.assertTrue(listResults.size == 0)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteDeleteRangeQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        query.firstRow = 2
        query.maxResults = 1


        val results = manager.executeDelete(query)
        Assert.assertTrue(results == 1)

        val fetchQuery = Query(AllAttributeForFetch::class.java, criteria)
        val listResults = manager.executeQuery<AllAttributeForFetch>(fetchQuery)

        Assert.assertTrue(listResults.size == 4)
    }

    @Test(expected = NoResultsException::class)
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testCascadeRelationship() {
        val criteria = QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA")

        val fetchQuery = Query(AllAttributeForFetch::class.java, criteria)
        var listResults = manager.executeQuery<AllAttributeForFetch>(fetchQuery)
        val childId = listResults[0].child!!.id!!

        val query = Query(AllAttributeForFetch::class.java, QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA"))

        val results = manager.executeDelete(query)
        Assert.assertTrue(results == 2)

        val fetchQuery2 = Query(AllAttributeForFetch::class.java, QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA"))
        listResults = manager.executeQuery(fetchQuery2)
        Assert.assertTrue(listResults.size == 0)
        val child = AllAttributeForFetchChild()
        child.id = childId
        manager.find<IManagedEntity>(child)
    }
}
