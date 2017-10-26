package remote.queries

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.AllAttributeForFetch
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemotePrePopulatedBaseTest

/**
 * Created by cosbor11 on 1/9/2015.
 */
@Category(RemoteServerTests::class)
class UpdateQueryTest : RemotePrePopulatedBaseTest() {

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteUpdateQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
        val query = Query(AllAttributeForFetch::class.java, criteria, AttributeUpdate("stringValue", "B"))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"))
        val listResults = manager!!.executeQuery<AllAttributeForFetch>(fetchQuery)

        val res = listResults[0]
        res.stringValue = "Some test strin3"
        manager!!.saveEntity<IManagedEntity>(res)

        Assert.assertTrue(listResults.size == 1)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteUpdateMultipleQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria, AttributeUpdate("stringValue", "B"))

        query.firstRow = 2
        query.maxResults = 2

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"))
        val listResults = manager!!.executeQuery<Any>(fetchQuery)
        Assert.assertTrue(listResults.size == 2)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteUpdateMultipleFieldsQuery() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria, AttributeUpdate("stringValue", "B"), AttributeUpdate("intValue", 4))

        query.firstRow = 2
        query.maxResults = 2

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)

        val fetchQuery = Query(AllAttributeForFetch::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B").and("intValue", QueryCriteriaOperator.EQUAL, 4))
        val listResults = manager!!.executeQuery<Any>(fetchQuery)
        Assert.assertTrue(listResults.size == 2)
    }

}
