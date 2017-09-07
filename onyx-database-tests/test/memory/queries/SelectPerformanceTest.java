package memory.queries;

import category.InMemoryDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import entities.PerformanceEntity;
import junit.framework.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by timothy.osborn on 1/14/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class SelectPerformanceTest extends memory.base.PrePopulatedForSelectPerformanceTest
{

    /**
     * Scans 1 table, searching by 1 field. No results found
     * Last result: 151(win), 141(mac)
     * @throws OnyxException
     */
    @Test
    public void aTestNoResultsSingleFullTableScanFor100kRecords() throws OnyxException
    {
        manager.list(PerformanceEntity.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"));
        long time = System.currentTimeMillis();
        manager.list(PerformanceEntity.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "HAHAHAHABOOGER"));
        long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds to Query 100k records");

        org.junit.Assert.assertTrue((after - time) < 175);

    }

    /**
     * Scans 1 table, searching by 1 field. Ordering by 2 fields. 200k recs sorted, 20 recs return because of maxResults.
     * Last result: 1081(win), 1122(mac)
     * @throws OnyxException
     */
    @Test
    public void bTestSortingResultsFullTableScanFor200kRecords() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "A"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("booleanPrimitive", false), new QueryOrder("stringValue", true)));
        query.setFirstRow(100);
        query.setMaxResults(20);
        List<PerformanceEntity> results = manager.executeQuery(query);

        long time = System.currentTimeMillis();

        query = new Query(PerformanceEntity.class, new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "A"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("stringValue", true)));
        query.setFirstRow(100);
        query.setMaxResults(20);
        results = manager.executeQuery(query);
        long after = System.currentTimeMillis();


        System.out.println("Took " + (after - time) + " milliseconds to Query 200k records");

        Assert.assertTrue(results.size() == 20);
        org.junit.Assert.assertTrue(query.getResultsCount() == 100000);

        org.junit.Assert.assertTrue((after - time) < 1000);

    }

    /**
     * Scans 1 index returning 20 keys
     * then joins with one associated table
     * returns a total of 20 recs
     * Last result: 59(win), 36(mac)
     * @throws OnyxException
     */
    @Test
    public void cTestJoiningResultsFor300kRecords() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "A"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("id", true)));
        query.setFirstRow(100);
        query.setMaxResults(20);
        List<PerformanceEntity> results = manager.executeQuery(query);

        List ids = new ArrayList<>();
        for(PerformanceEntity tmpEntity : results)
        {
            ids.add(tmpEntity.id);
        }

        long time = System.currentTimeMillis();
        query = new Query(PerformanceEntity.class, new QueryCriteria("id", QueryCriteriaOperator.IN, ids).and("child.someOtherField", QueryCriteriaOperator.NOT_EQUAL, "A"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("booleanValue", false), new QueryOrder("stringValue", true)));
        results = manager.executeQuery(query);
        long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds to Query 300k indexed records");

        org.junit.Assert.assertTrue((after - time) < 60);
        org.junit.Assert.assertTrue(results.size() == 20);
    }

    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void dTestGreaterThanOnIdValue() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000l));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);


        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");

//        org.junit.Assert.assertTrue((after - time) < 200);
        org.junit.Assert.assertTrue(results.size() == 20);
        org.junit.Assert.assertTrue(query.getResultsCount() == 1001);

    }

    /**
     * Scans entities with an identifier less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void eTestLessThanOnIdValue() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000l));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);


        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");

        org.junit.Assert.assertTrue(results.size() == 20);
        org.junit.Assert.assertTrue(query.getResultsCount() == 5000);
        org.junit.Assert.assertTrue((after - time) < 200);

    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void fTestLessThanOnIndexValue() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000l));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);
        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");

//        org.junit.Assert.assertTrue((after - time) < 300);
        org.junit.Assert.assertTrue(results.size() == 20);
        org.junit.Assert.assertTrue(query.getResultsCount() == 5000);

    }


    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void gTestGreaterThanOnIdValue() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 99000l));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);
        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");

//        org.junit.Assert.assertTrue((after - time) < 200);
        org.junit.Assert.assertTrue(results.size() == 20);
        org.junit.Assert.assertTrue(query.getResultsCount() == 1001);
    }

    /**
     * Scans entities with an index less than or equal to 5k.  This should of course return 5k records.  Also,
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void hTestLessThanOnIndexValueCompound() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(new QueryCriteria("idValue", QueryCriteriaOperator.LESS_THAN, 5000l)));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);
        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");
        org.junit.Assert.assertTrue(query.getResultsCount() == 2499);

    }


    /**
     * The purpose of this test is to see if the index on an identifier is working and efficient for greater than equal.
     * This should run under 20 milliseconds for a loadFactor of 1 and 200 ms for loadFactor of 5.
     *
     * @throws OnyxException Something bad happened
     */
    @Test
    public void iTestGreaterThanOnIdValueCompound() throws OnyxException
    {
        Query query = new Query(PerformanceEntity.class, new QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true).and(new QueryCriteria("idValue", QueryCriteriaOperator.GREATER_THAN, 99000l)));
        query.setFirstRow(100);
        query.setMaxResults(20);

        long time = System.currentTimeMillis();
        List<PerformanceEntity> results = manager.executeQuery(query);
        long after = System.currentTimeMillis();

        System.out.println("Query took " + (after - time) + " seconds");

        org.junit.Assert.assertTrue(query.getResultsCount() == 500);
    }
}
