package com.onyxdevtools.benchmark;

import com.onyxdevtools.entities.Player;
import com.onyxdevtools.entities.Stats;
import com.onyxdevtools.provider.manager.ProviderPersistenceManager;
import com.onyxdevtools.benchmark.base.BenchmarkTest;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tosborn1 on 8/26/16.
 *
 * This test illustrates how fast the database can delete items within a database.
 */
@SuppressWarnings("unused")
public class DeleteBenchmarkTest extends BenchmarkTest {

    protected static AtomicInteger playerIdCounter = new AtomicInteger(0);
    protected static AtomicInteger statIdCounter = new AtomicInteger(0);

    protected int NUMBER_OF_DELETIONS = 20000;
    protected int NUMBER_OF_WARM_UP_INSERTIONS = 20000;

    /**
     * Default Constructor
     *
     * @param providerPersistenceManager The underlying persistence manager
     */
    public DeleteBenchmarkTest(ProviderPersistenceManager providerPersistenceManager) {
        super(providerPersistenceManager);
    }

    /**
     * Do a little warm up.  In this case, we insert a bunch of test data that we are then going to use to update.
     */
    @Override
    public void before() {
        // Execute a bunch of insertions
        // Note, the Warm up insertions are the same as number of updates
        completionLatch = new CountDownLatch(getNumberOfWarmupExecutions());
        for (int i = 0; i < getNumberOfWarmupExecutions(); i++)
            this.testThreadPool.execute(getWarmUpTestingUnitRunnable());

        try {
            completionLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        completionLatch = new CountDownLatch(getNumberOfExecutions());

        // Reset the IDs so that we can reuse the existing records
        playerIdCounter.set(0);
        statIdCounter.set(0);
    }

    /**
     * Number of executions that count towards the test
     * @return Number of iterations we run through the database
     */
    @Override
    public int getNumberOfExecutions() {
        return NUMBER_OF_DELETIONS;
    }

    /**
     * Number of iterations we do as a warm up.  It is an invalid assumption to think a database will start with 0 records.
     * Therefore, we let it cycle through a warm up and seed the database and get down to its post cache or initial
     * index construction period.
     *
     * @return The number of iterations to warm up on.
     */
    @Override
    public int getNumberOfWarmupExecutions() {
        return NUMBER_OF_WARM_UP_INSERTIONS;
    }

    /**
     * Get the unit of work to run during the test.  In this case, we are inserting 3 entities.  One of which is a related entity.
     *
     * @return A runnable thread
     */
    public Runnable getTestingUnitRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                // For JPA, we have to "attach" the object.  For Onyx this is not needed but, for comparison sake, we
                // are going to follow suit
                providerPersistenceManager.delete(Player.class, playerIdCounter.addAndGet(1));
                completionLatch.countDown();
            }
        };

        return runnable;
    }

    /**
     * Get the unit of work to run during the test.  In this case, we are inserting 3 entities.  One of which is a related entity.
     *
     * @return A runnable thread
     */
    public Runnable getWarmUpTestingUnitRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                // I had to generate the id myself for JPA databases.  Apparently they don't like if you try to build an entire
                // graph and save it.  That is a limitation that Onyx does not need.
                Player player = new Player(playerIdCounter.addAndGet(1));
                player.setFirstName(generateRandomString());
                player.setLastName(generateRandomString());
                player.setActive(true);
                player.setPosition(generateRandomString());

                Stats stats = new Stats();
                stats.setFantasyPoints(generateRandomInt());
                stats.setPassAttempts(generateRandomInt());
                stats.setPassingYards(generateRandomInt());
                stats.setReceptions(generateRandomInt());
                stats.setRushingTouchdowns(generateRandomInt());
                stats.setRushingAttempts(generateRandomInt());

                player.setStats(Arrays.asList(stats));

                providerPersistenceManager.insert(player);

                completionLatch.countDown();
            }
        };

        return runnable;
    }
}
