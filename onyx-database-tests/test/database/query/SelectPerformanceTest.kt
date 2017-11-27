package database.query

import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.*
import database.base.BulkPrePopulatedDatabaseTest
import entities.PerformanceEntity
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class SelectPerformanceTest(override var factoryClass: KClass<*>) : BulkPrePopulatedDatabaseTest(factoryClass) {

    /**
     * Scans 1 table, searching by 1 field. No results found
     */
    @Test
    fun aTestNoResultsSingleFullTableScanFor100kRecords() {
        manager.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAMBURGER"))
        val time = System.currentTimeMillis()
        manager.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAMBURGER"))
        val after = System.currentTimeMillis()

        val timeToComplete  = after - time
        assertTrue(timeToComplete < 100, "Full table scan with 100k records should take under 100 ms when cached")
    }

    /**
     * Scans 1 table, searching by 1 field. Ordering by 2 fields. 200k recs sorted, 20 recs return because of maxResults.
     * Last result: 1081(win), 1122(mac)
     * @throws OnyxException
     */
    @Test
    fun bTestSortingResultsFullTableScanForRecords() {

        val builder = manager.from(PerformanceEntity::class)
                             .where("stringValue" neq "A")
                             .orderBy("stringValue".asc())
                             .first(100)
                             .limit(20)

        val before = System.currentTimeMillis()
        val results = builder.list<PerformanceEntity>()
        val after = System.currentTimeMillis()

        assertEquals(20, results.size, "Only 20 records should be returned")
        assertEquals(100000, builder.query.resultsCount, "100k records total matching criteria")
        assertTrue(after - before < 6000, "Query took more than 6 seconds.  Something has gone wrong")
    }

    /**
     * Scans 1 index returning 20 keys
     * @throws OnyxException
     */
    @Test
    fun cTestJoiningResultsForRecords() {
        var results = manager.from(PerformanceEntity::class)
                             .where("stringValue" neq "A")
                             .orderBy("id".asc())
                             .first(100)
                             .limit(20)
                             .list<PerformanceEntity>()

        val ids = results.map { it.id!! }

        val before = System.currentTimeMillis()
        results = manager.from(PerformanceEntity::class)
                         .where(("id" IN ids) and ("child.someOtherField" neq "A"))
                         .orderBy("booleanValue".desc(), "stringValue".asc())
                         .list()
        val after = System.currentTimeMillis()

        val timeToComplete = after - before
        assertEquals(20, results.size, "Query should only return 20 records")
        assertTrue(timeToComplete < 300, "Query should not take that much time to complete")
    }

    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     */
    @Test
    fun dTestGreaterThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000L))
        query.firstRow = 100
        query.maxResults = 20

        val results = manager.executeQuery<PerformanceEntity>(query)

        assertEquals(20, results.size, "Query should only return 20 records")
        assertEquals(1001, query.resultsCount, "Query should have a total of 1001 records")
    }

    /**
     * Scans entities with an identifier less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     */
    @Test
    fun eTestLessThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000L))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)
        val after = System.currentTimeMillis()

        assertEquals(20, results.size, "Query should only return 20 records")
        assertEquals(5000, query.resultsCount, "Query should have 500 records")
        assertTrue(after - time < 500, "Query should only take a half a seconds since it is on an index")
    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     */
    @Test
    fun fTestLessThanOnIndexValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000L))
        query.firstRow = 100
        query.maxResults = 20

        val results = manager.executeQuery<PerformanceEntity>(query)

        assertEquals(20, results.size, "Query should return only 20 records")
        assertEquals(5000, query.resultsCount, "Query should have a total of 5000 records")

    }

    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     */
    @Test
    fun gTestGreaterThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000L))
        query.firstRow = 100
        query.maxResults = 20

        val results = manager.executeQuery<PerformanceEntity>(query)

        assertEquals(20, results.size, "Query should only return 20 records")
        assertEquals(1001, query.resultsCount, "Query should have 1001 total records")
    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     */
    @Test
    fun hTestLessThanOnIndexValueCompound() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN, 5000L)))
        query.firstRow = 100
        query.maxResults = 20
        manager.executeQuery<PerformanceEntity>(query)
        assertEquals(2499, query.resultsCount, "Query should have a total of 2499 records")
    }

    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     */
    @Test
    fun iTestGreaterThanOnIdValueCompound() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN, 99000L)))
        query.firstRow = 100
        query.maxResults = 20
        manager.executeQuery<PerformanceEntity>(query)
        assertEquals(500, query.resultsCount, "Query should have a total of 500 records")
    }
}