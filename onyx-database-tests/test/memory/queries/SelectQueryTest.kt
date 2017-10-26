package memory.queries

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import entities.AllAttributeForFetch
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.Arrays

/**
 * Created by timothy.osborn on 1/10/15.
 */
@Category(InMemoryDatabaseTests::class)
class SelectQueryTest : memory.base.PrePopulatedDatabaseTest() {

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testExecuteSelectFields() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, Arrays.asList("longValue", "intPrimitive"), criteria)
        val results = manager.executeQuery<Any>(query)
        Assert.assertNotNull(results)
        Assert.assertTrue(results.size == 4)
        Assert.assertTrue(results[0] is Map<*, *>)
        Assert.assertTrue((results[0] as Map<*, *>)["longValue"] as Long > 0)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testSelectOnlyOne() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, Arrays.asList("longValue", "intPrimitive"), criteria)
        query.firstRow = 0
        query.maxResults = 1
        val results = manager.executeQuery<Any>(query)

        Assert.assertNotNull(results)
        Assert.assertTrue(results.size == 1)
        Assert.assertTrue(results[0] is Map<*, *>)

        if (results[0] is Long) {
            Assert.assertTrue((results[0] as Map<*, *>)["longValue"] as Long > 0)
        } else if (results[0] is Int) {
            Assert.assertTrue((results[0] as Map<*, *>)["longValue"] as Int > 0)
        }
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testSelectTwoOrderBy() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, Arrays.asList("id", "stringValue", "longValue", "intPrimitive"), criteria)
        query.firstRow = 2
        query.maxResults = 2
        query.queryOrders = Arrays.asList(QueryOrder("stringValue"), QueryOrder("intPrimitive"))

        val results = manager.executeQuery<Any>(query)

        Assert.assertNotNull(results)
        //        Assert.assertTrue(results.size() == 2);
        Assert.assertTrue(results[0] is Map<*, *>)
        Assert.assertTrue((results[0] as Map<*, *>)["intPrimitive"] as Int == 4)
        Assert.assertTrue((results[1] as Map<*, *>)["intPrimitive"] as Int == 3)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testNoSelect() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        query.firstRow = 0
        query.maxResults = 2
        query.queryOrders = Arrays.asList(QueryOrder("stringValue"), QueryOrder("intPrimitive"))

        val results = manager.executeQuery<Any>(query)

        Assert.assertNotNull(results)
        Assert.assertTrue(results.size == 2)
        Assert.assertTrue(results[0] is AllAttributeForFetch)
        Assert.assertTrue((results[0] as AllAttributeForFetch).id == "FIRST ONE")
        Assert.assertTrue((results[1] as AllAttributeForFetch).id == "FIRST ONE2" || (results[1] as AllAttributeForFetch).id == "FIRST ONE1")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testSelectRelationship() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
                .and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        query.firstRow = 0
        query.maxResults = 2
        query.queryOrders = Arrays.asList(QueryOrder("child.someOtherField"), QueryOrder("intPrimitive"))
        query.selections = Arrays.asList("longValue", "intPrimitive", "child.someOtherField")

        val results = manager.executeQuery<Any>(query)

        Assert.assertNotNull(results)
        Assert.assertTrue(results.size == 1)
        Assert.assertTrue(results[0] is Map<*, *>)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testSelectRelationshipMultiResult() {
        val criteria = QueryCriteria("id", QueryCriteriaOperator.STARTS_WITH, "FIRST ONE").and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        query.firstRow = 0
        query.maxResults = 2
        query.queryOrders = Arrays.asList(QueryOrder("child.someOtherField"), QueryOrder("intPrimitive"))
        query.selections = Arrays.asList("id", "longValue", "intPrimitive", "child.someOtherField")

        val results = manager.executeQuery<Any>(query)

        Assert.assertNotNull(results)
        Assert.assertTrue(results.size == 2)
        Assert.assertTrue(results[0] is Map<*, *>)
        Assert.assertTrue((results[0] as Map<*, *>)["id"] == "FIRST ONE")
        Assert.assertTrue((results[1] as Map<*, *>)["id"] == "FIRST ONE4")
    }
}
