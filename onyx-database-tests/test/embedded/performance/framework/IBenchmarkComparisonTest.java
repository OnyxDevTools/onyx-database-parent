package embedded.performance.framework;

import org.junit.Test;

/**
 * @desc implement this interface to test any database against OnyxDB.
 * You must first implement an IBenchmarkAdapter to tell the test how to interact with the database
 * then implement the methods that are used to get performance metrics
 * @see embedded.performance.framework.IBenchmarkAdapter
 * @see embedded.performance.framework.BenchmarkComparisonTest
 * @author cosbor11 ChrisOsborn
 * @date 1/8/2015.
 */
public interface IBenchmarkComparisonTest {

    /**
     * implement this method to return an adapter that is used to tell the test how to initialize, open, clean, and interact with the database
     * @return IBenchmarkAdapter
     */
    public IBenchmarkAdapter getAdapter();

    /**
     * implement this method to return the time it takes to insert 100,000 record into a given database using the adapter for that datbase
     * @param adapter
     * @return
     */
    public long getTimeToCreate100000SimpleRecords(IBenchmarkAdapter adapter);

    @Test
    public void compare();
}
