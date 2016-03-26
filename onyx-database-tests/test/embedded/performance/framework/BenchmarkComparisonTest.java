package embedded.performance.framework;

import org.junit.Assert;
import org.junit.Test;
import embedded.performance.adapters.OnyxBenchmarkAdapter;

/**
 * @desc This abstract test class is extended by a subclass that is used to compare the performance of a given database to OnyxDB.
 * If the test fails the given database is faster. We assert that OnyxDB is the fastest Database on the planet
 * @author cosbor11
 * @date 1/6/2015.
 */
public abstract class BenchmarkComparisonTest implements IBenchmarkComparisonTest {

    /**
     * returns the time it takes to insert 100,000 records into the the database configured on the adaptor
     * @param adapter
     * @return long
     */
    @Override
    public long getTimeToCreate100000SimpleRecords(IBenchmarkAdapter adapter)
    {
        adapter.clean();
        adapter.initialize();
        long result = adapter.timeToCreateXRecords(100000);
        adapter.clean();
        return result;
    }

    /**
     * runs a test that compares OnyxDB performance metrics vs. any given NOSQL database.
     * The comparison stats will be outputted to the console.
     * If a test occurs where the other database is faster, then the test will fail.
     * @see embedded.performance.MongoBenchmarkComparisonTest
     */
    @Test
    public void compare(){
        final double onyxTimeToCreate10000Recs = (double) getTimeToCreate100000SimpleRecords(new OnyxBenchmarkAdapter());
        final double timeToCreate100000Recs    = (double) getTimeToCreate100000SimpleRecords(this.getAdapter());

        final double x = ((timeToCreate100000Recs - onyxTimeToCreate10000Recs) / onyxTimeToCreate10000Recs) * 100;

        System.out.println("OnyxDB is " + Math.round(x) + "% faster at inserting 100,000 records!");
        Assert.assertTrue(onyxTimeToCreate10000Recs < timeToCreate100000Recs);

    }



}
