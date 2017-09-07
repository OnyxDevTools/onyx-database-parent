package embedded.save;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import com.onyx.persistence.IManagedEntity;
import embedded.base.BaseTest;
import entities.AllAttributeEntity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.Assert.assertEquals;


/**
 Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ EmbeddedDatabaseTests.class })
public class HashIndexConcurrencyTest extends BaseTest
{
    @Before public void before() throws InitializationException
    {
        initialize();
    }

    @After public void after() throws IOException
    {
        shutdown();
    }

    int z = 0;

    public HashIndexConcurrencyTest()
    {
    }

    protected synchronized void increment()
    {
        z++;
    }

    protected synchronized int getZ()
    {
        return z;
    }

    /**
     * Tests Batch inserting 100,000 record with a String identifier last test took: 1741(win) 2231(mac)
     *
     * @throws OnyxException
     * @throws  InterruptedException
     */
    @Test public void aConcurrencyHashPerformanceTest() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();
        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(1);

        final List<AllAttributeEntity> entities = new ArrayList<>();

        for (int i = 0; i <= 100000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % 5000) == 0)
            {
                final List<IManagedEntity> tmpList = new ArrayList<IManagedEntity>(entities);
                entities.removeAll(entities);

                final Runnable runnable = () -> {
                    try
                    {
                        manager.saveEntities(tmpList);
                    }
                    catch (OnyxException e)
                    {
                        e.printStackTrace();
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        final long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        Assert.assertTrue((after - time) < 5000);

        pool.shutdownNow();
    }

    /**
     * Runs 10 threads that insert 10k entities with a String identifier. After insertion, this test validates the data integrity. last test
     * took: 698(win) 2231(mac)
     *
     * @throws OnyxException
     * @throws  InterruptedException
     */
    @Test public void concurrencyHashSaveIntegrityTest() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4L;
        entity2.longPrimitive = 3L;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743L);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(10);

        final List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        final List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();

        for (int i = 0; i <= 5000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {

                                for (final IManagedEntity entity1 : tmpList)
                                {
                                    manager.saveEntity(entity1);
                                }
                                // manager.saveEntities(tmpList);
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        final long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        for (final AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity) manager.find(newEntity);
            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test public void concurrencyHashSaveIntegrityTestWithBatching() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4L;
        entity2.longPrimitive = 3L;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743L);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(15);

        final List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        final List<AllAttributeEntity> entitiesToValidate = new ArrayList<>();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {
                                manager.saveEntities(tmpList);
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                threads.add(pool.submit(runnable));
            }
        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        final long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        pool.shutdownNow();

        int i = 0;

        for (final AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity) manager.find(newEntity);

            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test public void concurrencyHashDeleteIntegrityTest() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4L;
        entity2.longPrimitive = 3L;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743L);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(10);

        final List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {

                                for (final IManagedEntity entity1 : tmpList)
                                {
                                    manager.saveEntity(entity1);
                                }
                                // manager.saveEntities(tmpList);
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        threads.removeAll(threads);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final int indx = i;
                final int delIdx = deleteCount;

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {

                                for (final IManagedEntity entity1 : tmpList)
                                {
                                    manager.saveEntity(entity1);
                                }

                                for (int t = delIdx; (t < (delIdx + 5)) && (t < entitiesToValidateDeleted.size()); t++)
                                {
                                    manager.deleteEntity(entitiesToValidateDeleted.get(t));
                                }
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        final long after = System.currentTimeMillis();

        for (final AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity) manager.find(newEntity);
            Assert.assertTrue(newEntity.id.equals(entity.id));
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }

        for (final AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            final AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;

            boolean pass = false;

            try
            {
                manager.find(newEntity);
            }
            catch (NoResultsException e)
            {
                pass = true;
            }

            Assert.assertTrue(pass);
        }
    }

    @Test public void concurrencyHashDeleteBatchIntegrityTest() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();
        final AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = new BigInteger(130, random).toString(32);
        entity2.longValue = 4L;
        entity2.longPrimitive = 3L;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743L);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(10);

        final List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();

        final List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();

        final Map<String, AllAttributeEntity> ignore = new HashMap();

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
                ignore.put(entity.id, entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {
                                manager.saveEntities(tmpList);
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final int indx = i;
                final int delIdx = deleteCount;

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {
                                manager.saveEntities(tmpList);

                                for (int t = delIdx; (t < (delIdx + 5)) && (t < entitiesToValidateDeleted.size()); t++)
                                {
                                    manager.deleteEntity(entitiesToValidateDeleted.get(t));
                                }
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        final long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        for (final AllAttributeEntity entity : entitiesToValidate)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;

            if (!ignore.containsKey(newEntity.id))
            {
                newEntity = (AllAttributeEntity) manager.find(newEntity);
                Assert.assertTrue(newEntity.id.equals(entity.id));
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
            }
        }

        int p = 0;

        for (final AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;

            boolean pass = false;

            try
            {
                newEntity = (AllAttributeEntity) manager.find(newEntity);
            }
            catch (NoResultsException e)
            {
                pass = true;
            }

            Assert.assertTrue(pass);
            p++;
        }
    }

    /**
     * Executes 10 threads that insert 30k entities with string id, then 10k are updated and 10k are deleted. Then it validates the
     * integrity of those actions last test took: 3995(win) 2231(mac)
     *
     * @throws OnyxException
     * @throws  InterruptedException
     */
    @Test public void concurrencyHashAllIntegrityTest() throws OnyxException, InterruptedException
    {
        final SecureRandom random = new SecureRandom();

        final long time = System.currentTimeMillis();

        final List<Future> threads = new ArrayList<>();

        final ExecutorService pool = Executors.newFixedThreadPool(10);

        final List<AllAttributeEntity> entities = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidate = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidateDeleted = new ArrayList<AllAttributeEntity>();
        final List<AllAttributeEntity> entitiesToValidateUpdated = new ArrayList<AllAttributeEntity>();

        final Map<String, AllAttributeEntity> ignore = new HashMap();

        /**
         * Save A whole bunch of records and keep track of some to update and delete
         */
        for (int i = 0; i <= 30000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            // Delete Even ones
            if ((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
                ignore.put(entity.id, entity);
            }

            // Update every third one
            else if (((i % 3) == 0) && ((i % 2) != 0))
            {
                entitiesToValidateUpdated.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }

            if ((i % 1000) == 0)
            {

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {
                                manager.saveEntities(tmpList);
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                threads.add(pool.submit(runnable));
            }

        }

        // Make Sure we Are done
        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        Thread.sleep(1000);

        // Update an attribute
        for (final AllAttributeEntity entity : entitiesToValidateUpdated)
        {
            entity.longPrimitive = 45645;
        }

        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;
        int updateCount = 0;

        for (int i = 0; i <= 30000; i++)
        {
            final AllAttributeEntity entity = new AllAttributeEntity();
            entity.id = new BigInteger(130, random).toString(32);
            entity.longValue = 4L;
            entity.longPrimitive = 3L;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743L);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % 20) == 0)
            {

                entitiesToValidate.add(entity);

                final List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);

                final int indx = i;
                final int delIdx = deleteCount;
                final int updtIdx = updateCount;

                final Runnable runnable = new Runnable()
                    {
                        @Override public void run()
                        {
                            try
                            {
                                manager.saveEntities(tmpList);

                                for (int t = updtIdx; (t < (updtIdx + 13)) && (t < entitiesToValidateUpdated.size()); t++)
                                {
                                    manager.saveEntity(entitiesToValidateUpdated.get(t));
                                }

                                for (int t = delIdx; (t < (delIdx + 30)) && (t < entitiesToValidateDeleted.size()); t++)
                                {
                                    manager.deleteEntity(entitiesToValidateDeleted.get(t));
                                }
                            }
                            catch (OnyxException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                deleteCount += 30;
                updateCount += 13;
                threads.add(pool.submit(runnable));
            }

        }

        for (final Future future : threads)
        {

            try
            {
                future.get();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();
        Thread.sleep(1000);

        final long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        int i = 0;

        for (final AllAttributeEntity entity : entitiesToValidate)
        {
            final AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;

            if (!ignore.containsKey(newEntity.id))
            {

                try
                {
                    manager.find(newEntity);
                }
                catch (Exception e)
                {
                    i++;
                }
            }
        }

        assertEquals(0, i);

        for (final AllAttributeEntity entity : entitiesToValidateDeleted)
        {
            final AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;

            boolean pass = false;

            try
            {
                manager.find(newEntity);
            }
            catch (NoResultsException e)
            {
                pass = true;
            }

            if (!pass)
            {
                i++;
            }
        }

        assertEquals(i, 0);

        for (final AllAttributeEntity entity : entitiesToValidateUpdated)
        {
            AllAttributeEntity newEntity = new AllAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (AllAttributeEntity) manager.find(newEntity);
            Assert.assertTrue(newEntity.longPrimitive == 45645);
        }
    }

}
