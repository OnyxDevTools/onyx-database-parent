package remote.queries

import category.RemoteServerTests
import com.onyx.application.impl.DatabaseServer
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import entities.PerformanceEntity
import junit.framework.Assert
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import remote.base.RemotePrePopulatedForSelectPerformanceTest

import java.util.ArrayList
import java.util.Arrays

/**
 * Created by timothy.osborn on 1/14/15.
 */
@Category(RemoteServerTests::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SelectPerformanceTest : RemotePrePopulatedForSelectPerformanceTest() {

    /**
     * Scans 1 table, searching by 1 field. No results found
     * Last result: 151(win), 141(mac)
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class)
    fun aTestNoResultsSingleFullTableScanFor100kRecords() {
        manager!!.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"))
        val time = System.currentTimeMillis()
        manager!!.list<IManagedEntity>(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"))
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
        var results = manager!!.executeQuery<PerformanceEntity>(query)

        val time = System.currentTimeMillis()

        query = Query(PerformanceEntity::class.java, QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("stringValue", true))
        query.firstRow = 100
        query.maxResults = 20
        results = manager!!.executeQuery(query)
        val after = System.currentTimeMillis()


        println("Took " + (after - time) + " milliseconds to Query 200k records")

        Assert.assertTrue(results.size == 20)
        org.junit.Assert.assertTrue(query.resultsCount == 200000)

        org.junit.Assert.assertTrue(after - time < 2500)

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
        var results = manager!!.executeQuery<PerformanceEntity>(query)

        val ids = ArrayList<Any>()
        for (tmpEntity in results) {
            ids.add(tmpEntity.id!!)
        }

        val time = System.currentTimeMillis()
        query = Query(PerformanceEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.IN, ids).and("child.someOtherField", QueryCriteriaOperator.NOT_EQUAL, "A"))
        query.queryOrders = Arrays.asList(QueryOrder("booleanValue", false), QueryOrder("stringValue", true))
        results = manager!!.executeQuery(query)
        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds to Query 300k indexed records")

        org.junit.Assert.assertTrue(after - time < 60)
        org.junit.Assert.assertTrue(results.size == 20)
    }

}
