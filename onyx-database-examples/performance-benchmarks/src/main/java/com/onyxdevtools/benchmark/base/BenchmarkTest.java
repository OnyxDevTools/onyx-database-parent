package com.onyxdevtools.benchmark.base;

import com.onyxdevtools.provider.manager.ProviderPersistenceManager;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by Tim Osborn on 8/26/16.
 *
 * This is the base level benchmark test
 */
public abstract class BenchmarkTest {

    // Thread Pool used to execute the test body of work
    protected ThreadPoolExecutor testThreadPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(20);

    // Marks the start time of the test
    private long startTime;

    // Marks the end time of the test
    private long endTime;

    // The underlying persistence manager
    protected final ProviderPersistenceManager providerPersistenceManager;

    // Random generator
    private static final SecureRandom random = new SecureRandom();

    // Count down to completion of the test
    protected CountDownLatch completionLatch;

    /**
     * Default Constructor
     *
     * @param providerPersistenceManager The underlying persistence manager
     */
    protected BenchmarkTest(ProviderPersistenceManager providerPersistenceManager)
    {
        this.providerPersistenceManager = providerPersistenceManager;
    }

    /**
     * Indicates the start of the test
     */
    public void markBeginningOfTest()
    {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Indicates the end of the test
     */
    public void markEndOfTest()
    {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * Get the total execution runtime of the test
     * @return end time - start time in milliseconds
     */
    private long getExecutionTime()
    {
        return this.endTime - this.startTime;
    }

    /**
     * Do a little warm up.
     */
    public void before() {
        System.out.println("Starting test " + this.getClass().getName());
        System.out.println("Please be patient this may take a while to run");
        completionLatch = new CountDownLatch(getNumberOfWarmUpExecutions());
        execute(getNumberOfWarmUpExecutions());
        completionLatch = new CountDownLatch(getNumberOfExecutions());
    }

    /**
     * Default Execute method to get the number of executions and wait for completion
     */
    public void execute(int iterations) {
        for (int i = 0; i < iterations; i++)
            this.testThreadPool.execute(getTestingUnitRunnable());

        try {
            completionLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Execute After test.  Default is to print out the results
     */
    public void after() {
        System.out.println("Finished executing " + this.getClass().getName() + " in " + this.getExecutionTime() + " milliseconds for database " + providerPersistenceManager.getDatabaseProvider().getPersistenceProviderName());
    }

    /**
     * Number of executions to be ran
     */
    public abstract int getNumberOfExecutions();

    /**
     * Number of executions to be ran
     */
    protected abstract int getNumberOfWarmUpExecutions();

    /**
     * Get Thread to be executed
     */
    protected abstract Runnable getTestingUnitRunnable();

    /**
     * Cleanup after test
     */
    @SuppressWarnings("EmptyMethod")
    public void cleanup()
    {

    }

    /**
     * Generate a random string
     * @return random string
     */
    protected static String generateRandomString() {
        return new BigInteger(130, random).toString(32);
    }

    /**
     * Generate a random integer
     * @return Random integer
     */
    protected static int generateRandomInt() {
        return new BigInteger(130, random).intValue();
    }
}
