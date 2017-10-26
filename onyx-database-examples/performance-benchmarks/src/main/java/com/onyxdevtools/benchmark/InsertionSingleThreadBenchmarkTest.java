package com.onyxdevtools.benchmark;

import com.onyxdevtools.provider.manager.ProviderPersistenceManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by Tim Osborn on 8/26/16.
 *
 * This test illustrates the databases' ability to insert records on a single thread.
 */
@SuppressWarnings("unused")
public class InsertionSingleThreadBenchmarkTest extends InsertionBenchmarkTest {

    @SuppressWarnings("FieldCanBeLocal")
    private int NUMBER_OF_INSERTIONS = 20000;
    @SuppressWarnings("FieldCanBeLocal")
    private int NUMBER_OF_WARM_UP_INSERTIONS = 5000;

    /**
     * Default Constructor
     *
     * @param providerPersistenceManager The underlying persistence manager
     */
    @SuppressWarnings("unused")
    public InsertionSingleThreadBenchmarkTest(ProviderPersistenceManager providerPersistenceManager) {
        super(providerPersistenceManager);
        testThreadPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(1);
    }

    /**
     * Number of executions that count towards the test
     * @return Number of iterations we run through the database
     */
    @Override
    public int getNumberOfExecutions() {
        return NUMBER_OF_INSERTIONS;
    }

    /**
     * Number of iterations we do as a warm up.  It is an invalid assumption to think a database will start with 0 records.
     * Therefore, we let it cycle through a warm up and seed the database and get down to its post cache or initial
     * index construction period.
     *
     * @return The number of iterations to warm up on.
     */
    @Override
    public int getNumberOfWarmUpExecutions() {
        return NUMBER_OF_WARM_UP_INSERTIONS;
    }

}
