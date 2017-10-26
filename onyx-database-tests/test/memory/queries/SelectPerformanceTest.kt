package memory.queries

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import entities.PerformanceEntity
import junit.framework.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.util.ArrayList
import java.util.Arrays

/**
 * Created by timothy.osborn on 1/14/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(InMemoryDatabaseTests::class)
class SelectPerformanceTest : memory.base.PrePopulatedForSelectPerformanceTest() {

    /**
     * Scans 1 table, searching by 1 field. No results found
     * Last result: 151(win), 141(mac)
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class)
    fun aTestNoResultsSingleFullTableScanFor100kRecords() {
        manager.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"))
        val time = System.currentTimeMillis()
        manager.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"))
        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds to Query 100k records")

        org.junit.Assert.assertTrue(after - time < 175)

    }

    /**
     * Scans 1 table, searching by 1 field. Ordering by 2 fields. 200k recs sorted, 20 recs return because of maxResults.
     * Last result: 1081(win), 1122(mac)
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class)
    fun bTestSortingResultsFullTableScanFor200kRecords() {
        var query = Query(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("booleanPrimitive", false), QueryOrder("stringValue", true))
        query.firstRow = 100
        query.maxResults = 20
        var results = manager.executeQuery<PerformanceEntity>(query)

        val time = System.currentTimeMillis()

        query = Query(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("stringValue", true))
        query.firstRow = 100
        query.maxResults = 20
        results = manager.executeQuery(query)
        val after = System.currentTimeMillis()


        println("Took " + (after - time) + " milliseconds to Query 200k records")

        Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 100000)

        org.junit.Assert.assertTrue(after - time < 1000)

    }

    /**
     * Scans 1 index returning 20 keys
     * then joins with one associated table
     * returns a total of 20 recs
     * Last result: 59(win), 36(mac)
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class)
    fun cTestJoiningResultsFor300kRecords() {
        var query = Query(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("id", true))
        query.firstRow = 100
        query.maxResults = 20
        var results = manager.executeQuery<PerformanceEntity>(query)

        val ids = ArrayList<Any>()
        for (tmpEntity in results) {
            ids.add(tmpEntity.id!!)
        }

        val time = System.currentTimeMillis()
        query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.IN, ids).and("child.someOtherField", QueryCriteriaOperator.NOT_EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("booleanValue", false), QueryOrder("stringValue", true))
        results = manager.executeQuery(query)
        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds to Query 300k indexed records")

        org.junit.Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(after - time < 60)
    }

    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun dTestGreaterThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000L))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)


        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")

        //        org.junit.Assert.assertTrue((after - time) < 200);
        org.junit.Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 1001)

    }

    /**
     * Scans entities with an identifier less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun eTestLessThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000L))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)


        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")

        org.junit.Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 5000)
        org.junit.Assert.assertTrue(after - time < 200)

    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun fTestLessThanOnIndexValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000L))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)
        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")

        //        org.junit.Assert.assertTrue((after - time) < 300);
        org.junit.Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 5000)

    }


    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun gTestGreaterThanOnIdValue() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000L))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)
        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")

        //        org.junit.Assert.assertTrue((after - time) < 200);
        org.junit.Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 1001)
    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun hTestLessThanOnIndexValueCompound() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN, 5000L)))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)
        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")
        org.junit.Assert.assertTrue(query.resultsCount == 2499)

    }


    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    @Throws(OnyxException::class)
    fun iTestGreaterThanOnIdValueCompound() {
        val query = Query(PerformanceEntity::class.java, QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN, 99000L)))
        query.firstRow = 100
        query.maxResults = 20

        val time = System.currentTimeMillis()
        val results = manager.executeQuery<PerformanceEntity>(query)
        val after = System.currentTimeMillis()

        println("Query took " + (after - time) + " seconds")

        org.junit.Assert.assertTrue(query.resultsCount == 500)
    }
}
