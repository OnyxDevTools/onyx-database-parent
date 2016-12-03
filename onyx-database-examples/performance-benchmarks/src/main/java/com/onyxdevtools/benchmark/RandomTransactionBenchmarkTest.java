package com.onyxdevtools.benchmark;

import com.onyxdevtools.entities.Player;
import com.onyxdevtools.entities.Stats;
import com.onyxdevtools.entities.Team;
import com.onyxdevtools.provider.manager.ProviderPersistenceManager;
import com.onyxdevtools.benchmark.base.BenchmarkTest;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by tosborn1 on 8/26/16.
 *
 * This test simulates a realistic load on a database including insertions, updates, deletions, finds, queries including an index,
 * and queries including a full scan.
 */
public class RandomTransactionBenchmarkTest extends BenchmarkTest {

    protected static AtomicInteger playerIdCounter = new AtomicInteger(0);
    protected static AtomicInteger teamIdCounter = new AtomicInteger(0);
    protected static AtomicInteger statIdCounter = new AtomicInteger(0);

    final BlockingQueue<Integer> cachedPlayerIds = new LinkedBlockingQueue();
    final BlockingQueue<String> cachedTeamIds = new LinkedBlockingQueue();
    final BlockingQueue<Long> cachedStatIds = new LinkedBlockingQueue();
    final BlockingQueue<Integer> cachedStatRushingYards = new LinkedBlockingQueue();
    final BlockingQueue<String> cachedPlayerFirstNames = new LinkedBlockingQueue();

    protected int NUMBER_OF_UPDATES = 30000;
    protected int NUMBER_OF_WARM_UP_INSERTIONS = 5000;

    /**
     * Default Constructor
     *
     * @param providerPersistenceManager The underlying persistence manager
     */
    public RandomTransactionBenchmarkTest(ProviderPersistenceManager providerPersistenceManager) {
        super(providerPersistenceManager);
    }

    /**
     * Number of executions that count towards the test
     *
     * @return Number of iterations we run through the database
     */
    @Override
    public int getNumberOfExecutions() {
        return NUMBER_OF_UPDATES;
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

    public void execute(int iterations)
    {
        while (completionLatch.getCount() > 0)
        {
            if(this.testThreadPool.getActiveCount() >= this.testThreadPool.getCorePoolSize())
            {
                LockSupport.parkNanos(1000);
            }
            else {
                this.testThreadPool.execute(getTestingUnitRunnable());
            }
        }

        this.testThreadPool.purge();
    }

    public Runnable getInsertDataRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {


                Team team = new Team();
                team.setTeamName(generateRandomString() + teamIdCounter.addAndGet(1));
                cachedTeamIds.add(team.getTeamName());

                Player player = new Player(playerIdCounter.addAndGet(1));
                player.setPosition(generateRandomString());
                player.setFirstName(generateRandomString());
                player.setLastName(generateRandomString());
                player.setActive(true);
                player.setTeam(team);

                if(cachedPlayerFirstNames.size() < 40) {
                    cachedPlayerFirstNames.add(player.getFirstName());
                }
                team.setPlayers(Arrays.asList(player));
                providerPersistenceManager.update(team);

                Stats stats = new Stats(statIdCounter.addAndGet(1));
                stats.setFantasyPoints(generateRandomInt());
                stats.setPassAttempts(generateRandomInt());
                stats.setPassingYards(generateRandomInt());
                stats.setReceptions(generateRandomInt());
                stats.setRushingTouchdowns(generateRandomInt());
                stats.setRushingAttempts(generateRandomInt());
                stats.setRushingYards(generateRandomInt());
                stats.setPlayer(player);

                Stats stats2 = new Stats(statIdCounter.addAndGet(1));
                stats2.setFantasyPoints(generateRandomInt());
                stats2.setPassAttempts(generateRandomInt());
                stats2.setPassingYards(generateRandomInt());
                stats2.setReceptions(generateRandomInt());
                stats2.setRushingTouchdowns(generateRandomInt());
                stats2.setRushingYards(generateRandomInt());
                stats2.setRushingAttempts(generateRandomInt());
                stats2.setPlayer(player);

                if(cachedStatRushingYards.size() < 40) {
                    cachedStatRushingYards.add(stats.getRushingYards());
                    cachedStatRushingYards.add(stats2.getRushingYards());
                }

                providerPersistenceManager.update(stats);
                providerPersistenceManager.update(stats2);

                cachedPlayerIds.add(player.getPlayerId());
                cachedStatIds.add(stats.getStatId());
                cachedStatIds.add(stats2.getStatId());

                completionLatch.countDown();
            }
        };
        return runnable;
    }


    public Runnable getDeleteStatRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                Long statId = cachedStatIds.poll();
                if(statId == null)
                {
                    return;
                }
                Stats stat = (Stats)providerPersistenceManager.find(Stats.class, statId);
                if(stat != null)
                    providerPersistenceManager.delete(Stats.class, statId);

                completionLatch.countDown();
            }
        };
        return runnable;
    }

    public Runnable getFindPlayerRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                Integer playerId = cachedPlayerIds.poll();
                if(playerId == null)
                {
                    return;
                }
                Player player = (Player)providerPersistenceManager.find(Player.class, playerId);

                cachedPlayerIds.add(playerId);
                completionLatch.countDown();
            }
        };
        return runnable;
    }

    public Runnable getFindStatRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                Long statId = cachedStatIds.poll();
                if(statId == null)
                {
                    return;
                }
                Stats stat = (Stats)providerPersistenceManager.find(Stats.class, statId);
                cachedStatIds.add(statId);

                completionLatch.countDown();
            }
        };
        return runnable;
    }

    public Runnable getUpdateStatRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {


                Long statId = cachedStatIds.poll();

                if(statId == null)
                {
                    return;
                }
                Stats stats = (Stats)providerPersistenceManager.find(Stats.class, statId);

                if(stats == null)
                {
                    return;
                }
                stats.setFantasyPoints(generateRandomInt());
                stats.setPassAttempts(generateRandomInt());
                stats.setPassingYards(generateRandomInt());
                stats.setReceptions(generateRandomInt());
                stats.setRushingTouchdowns(generateRandomInt());
                stats.setRushingAttempts(generateRandomInt());

                providerPersistenceManager.update(stats);

                cachedStatIds.add(statId);

                completionLatch.countDown();
            }
        };

        return runnable;
    }

    public Runnable getUpdatePlayerRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                Integer playerId = cachedPlayerIds.poll();
                if(playerId == null)
                    return;
                Player player = (Player)providerPersistenceManager.find(Player.class, playerId);

                if(player == null)
                    return;
                player.setFirstName(generateRandomString());
                player.setLastName(generateRandomString());
                player.setActive(true);
                player.setPosition(generateRandomString());

                providerPersistenceManager.update(player);

                cachedPlayerIds.add(playerId);
                completionLatch.countDown();
            }
        };

        return runnable;
    }

    public Runnable getQueryStatsRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {


                Integer rushingYards = cachedStatRushingYards.poll();
                if(rushingYards == null)
                    return;

                providerPersistenceManager.list(Stats.class, "rushingYards", rushingYards);
                completionLatch.countDown();
            }
        };
        return runnable;
    }

    public Runnable getQueryPlayerRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

                String playerFirstName = cachedPlayerFirstNames.poll();
                if(playerFirstName == null)
                    return;

                List results = providerPersistenceManager.list(Player.class, "firstName", playerFirstName);
                assert results.size() > 0;

                completionLatch.countDown();
            }
        };
        return runnable;
    }

    public Runnable getQueryTeamsRunnable() {
        final Runnable runnable = new Runnable() {
            public void run() {

            String teamId = cachedTeamIds.poll();
            if(teamId == null)
                return;

            providerPersistenceManager.list(Team.class, "teamName", teamId);
            completionLatch.countDown();
            }
        };
        return runnable;
    }

    /**
     * Generates a random unit of work to run.  This generates a random number and based on that returns a thread
     * to feed to the executor pool in order for the database to execute that unit of work.
     *
     * It is weighted with insertions to have a greater probablity of being ran
     *
     * @return Runnable thread to execute
     */
    public Runnable getTestingUnitRunnable() {
        int methodIndex = ThreadLocalRandom.current().nextInt(0, 17 + 1);

        switch (methodIndex)
        {
            // Weight of 4 for inserting data so we have stuff to play with
            case 0:
            case 1:
            case 2:
            case 3:
                return getInsertDataRunnable();
            // Weight of 6 for querying data to simulate regular use.
            case 4:
            case 5:
                return getQueryTeamsRunnable();
            case 6:
            case 7:
                return getQueryPlayerRunnable();
            case 8:
            case 9:
                return getQueryStatsRunnable();
            case 10:
                return getDeleteStatRunnable();
            case 11:
                return getDeleteStatRunnable();
            case 12:
            case 13:
                return getFindPlayerRunnable();
            case 14:
            case 15:
                return getFindStatRunnable();
            case 16:
                return getUpdateStatRunnable();
            case 17:
                return getUpdatePlayerRunnable();

        }

        return null;
    }
}