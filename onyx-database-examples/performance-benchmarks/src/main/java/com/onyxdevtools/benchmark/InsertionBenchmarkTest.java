package com.onyxdevtools.benchmark;

import com.onyxdevtools.benchmark.base.BenchmarkTest;
import com.onyxdevtools.entities.League;
import com.onyxdevtools.entities.Player;
import com.onyxdevtools.entities.Stats;
import com.onyxdevtools.provider.manager.ProviderPersistenceManager;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Tim Osborn on 8/26/16.
 *
 * This test inserts several records.  To demonstrate a valid test, we insert a base level value, and an value with
 * a relationships.  This illustrates not just the speed of the database but the ORM as well.
 */
@SuppressWarnings("WeakerAccess")
public class InsertionBenchmarkTest extends BenchmarkTest {

    private static final AtomicInteger playerIdCounter = new AtomicInteger(0);

    @SuppressWarnings("FieldCanBeLocal")
    private final int NUMBER_OF_INSERTIONS = 50000;
    @SuppressWarnings("FieldCanBeLocal")
    private final int NUMBER_OF_WARM_UP_INSERTIONS = 5000;

    /**
     * Default Constructor
     *
     * @param providerPersistenceManager The underlying persistence manager
     */
    public InsertionBenchmarkTest(ProviderPersistenceManager providerPersistenceManager) {
        super(providerPersistenceManager);
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

    /**
     * Get the unit of work to run during the test.  In this case, we are inserting 3 entities.
     *
     * @return A runnable thread
     */
    @Override
    public Runnable getTestingUnitRunnable() {
        return () -> {
            League league = new League();
            league.setName(generateRandomString());

            providerPersistenceManager.insert(league);

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

            player.setStats(Collections.singletonList(stats));

            providerPersistenceManager.insert(player);

            completionLatch.countDown();
        };
    }
}
