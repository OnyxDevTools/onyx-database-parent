package database.query

import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class SelectQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {


    @Test
    fun testExecuteSelectFields() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, Arrays.asList("longValue", "intPrimitive"), criteria)
        val results = manager.executeQuery<IManagedEntity>(query)
        assertNotNull(results)
        assertEquals(4, results.size, "Expected 5 matching criteria")
        assertTrue(results[0] is Map<*, *>, "Result is not a map")
        assertTrue(((results[0] as Map<*, *>)["longValue"] as Number).toInt() > 0, "longValue was not assigned")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testSelectOnlyOne() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, arrayListOf("longValue", "intPrimitive"), criteria)
        query.firstRow = 0
        query.maxResults = 1
        val results = manager.executeQuery<Any>(query)

        assertNotNull(results, "Results were null")
        assertEquals(1, results.size, "Expected 1 result")
        assertTrue(results[0] is Map<*, *>, "Results are not in map format")

        if (results[0] is Long) {
            assertTrue((results[0] as Map<*, *>)["longValue"] as Long > 0, "longValue was not assigned")
        } else if (results[0] is Int) {
            assertTrue((results[0] as Map<*, *>)["longValue"] as Int > 0, "Long value was not assigned")
        }
    }

    @Test
    fun testSelectTwoOrderBy() {
        val results = manager.select("intPrimitive", "stringValue", "longPrimitive")
                             .from(AllAttributeForFetch::class)
                             .where("stringValue" startsWith "Some")
                             .orderBy("intPrimitive", "stringValue")
                             .first(2)
                             .limit(2)
                             .list<Map<String, Any?>>()

        assertNotNull(results, "Results should not be null")
        assertEquals(3, results[0]["intPrimitive"] as Int, "intPrimitive has incorrect value")
        assertEquals(4, results[1]["intPrimitive"] as Int, "intPrimitive has incorrect value")
    }

    @Test
    fun testNoSelect() {
        val results = manager.from(AllAttributeForFetch::class).where("stringValue" startsWith "Some")
                .first(0)
                .limit(2)
                .orderBy("stringValue", "intPrimitive")
                .list<AllAttributeForFetch>()

        assertNotNull(results, "Results should not be null")
        assertEquals(2, results.size, "Expected 2 results")
        assertEquals("FIRST ONE", results[0].id, "Query order is incorrect")
        assertEquals("FIRST ONE1", results[1].id, "Query order is incorrect")
    }

    @Test
    fun testSelectRelationship() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
                .and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        query.firstRow = 0
        query.maxResults = 2
        query.queryOrders = Arrays.asList(QueryOrder("child.someOtherField"), QueryOrder("intPrimitive"))
        query.selections = Arrays.asList("longValue", "intPrimitive", "child.someOtherField")

        val results = manager.executeQuery<Any>(query)

        assertNotNull(results)
        assertTrue(results.size == 1)
        assertTrue(results[0] is Map<*, *>)
    }

    @Test
    fun testSelectRelationshipMultiResult() {
        val results = manager.select("id", "longValue", "intPrimitive", "child.someOtherField")
                             .from(AllAttributeForFetch::class)
                             .where(("id" startsWith "FIRST ONE") and ("child.someOtherField" startsWith "HIYA"))
                             .first(0)
                             .limit(2)
                             .orderBy("id", "longValue", "intPrimitive", "child.someOtherField")
                             .list<Map<String, Any?>>()

        assertNotNull(results)
        assertEquals(2, results.size, "Results should have 2 records")
        assertEquals("FIRST ONE", results[0]["id"], "Invalid query order")
        assertEquals("FIRST ONE4", results[1]["id"], "Invalid query order")
    }
}