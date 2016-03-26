package embedded.performance.framework;

/**
 * @desc this is the interface that describes how the BenchmarkComparisonTest will interact with a database for comparison test purposes.
 * To test a database, you must implement this interface as well as IBenchmarkComparisonTest
 * @see embedded.performance.framework.IBenchmarkComparisonTest
 * @author cosbor11
 * @data 1/6/2015.
 */
public interface IBenchmarkAdapter<T> {

    /**
     * implement this method to create the database and, in some cases,  open a connection to it
     */
    public void initialize();

    /**
     * implement this method to close the connection to a database
     */
    public void close();

    /**
     * implement this method to close a connection to the database and then completely remove it from disk
     */
    public void clean();

    /**
     * implement this method to set all of the values  of a record for testing. i is the iteration used for populating multiple records and can be used to derive a primary key index
     * @param record
     * @param i
     */
    public void populateRecord(T record, int i);

    /**
     * implement this method to return the amount of time it takes to create x records
     * @param x
     * @return
     */
    public long timeToCreateXRecords(int x);

    /**
     * implement this method to return the amount of time it takes to retrieve x records
     * @param x
     * @return
     */
    public long timeToFetchXRecords(int x);

}
