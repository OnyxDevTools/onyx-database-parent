package embedded.save;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import com.onyx.persistence.IManagedEntity;
import embedded.base.BaseTest;
import entities.InheritedLongAttributeEntity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ EmbeddedDatabaseTests.class })
public class SequenceIndexConcurrencyTest extends BaseTest {

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    int z = 0;

    protected synchronized void increment()
    {
        z++;
    }

    protected synchronized int getZ()
    {
        return z;
    }

    /**
     * Tests Batch inserting 100,000 records with a Sequence identifier
     * This test is executed using 10 concurrent threads
     * last test took: 1670(win) 2200(mac)
     * @throws OnyxException
     * @throws InterruptedException
     */
    @Test
    public void aConcurrencySequencePerformanceTest() throws OnyxException, InterruptedException
    {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<InheritedLongAttributeEntity> entities = new ArrayList<>();

        long time = System.currentTimeMillis();

        int recordsToInsert = 100000;
        int batch = 5000;
        CountDownLatch recordsToGet = new CountDownLatch((recordsToInsert / batch) + 1);
        for (int i = 0; i <= recordsToInsert; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if ((i % batch) == 0)
            {
                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = () -> {
                    try
                    {
                        manager.saveEntities(tmpList);
                    } catch (OnyxException e)
                    {
                        e.printStackTrace();
                    }
                    recordsToGet.countDown();
                };
                pool.execute(runnable);
            }

        }

        recordsToGet.await();

        long after = System.currentTimeMillis();
        System.out.println("Took " + (after - time) + " milliseconds");
        Assert.assertTrue((after - time) < 3500);

        pool.shutdownNow();
    }

    @Test
    public void concurrencySequenceSaveIntegrityTest() throws OnyxException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final InheritedLongAttributeEntity entity2 = new InheritedLongAttributeEntity();
        
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<InheritedLongAttributeEntity> entities = new ArrayList<InheritedLongAttributeEntity>();

        List<InheritedLongAttributeEntity> entitiesToValidate = new ArrayList<InheritedLongAttributeEntity>();

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
                            //manager.saveEntities(tmpList);
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        pool.shutdownNow();

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(InheritedLongAttributeEntity entity : entitiesToValidate)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.id == entity.id);
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test
    public void concurrencySequenceSaveIntegrityTestWithBatching() throws OnyxException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final InheritedLongAttributeEntity entity2 = new InheritedLongAttributeEntity();
        
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(15);

        List<InheritedLongAttributeEntity> entities = new ArrayList<InheritedLongAttributeEntity>();

        List<InheritedLongAttributeEntity> entitiesToValidate = new ArrayList<>();

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }
        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        pool.shutdownNow();

        int i = 0;
        for(InheritedLongAttributeEntity entity : entitiesToValidate)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.id == entity.id);
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }
    }

    @Test
    public void concurrencySequenceDeleteIntegrityTest() throws OnyxException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        final InheritedLongAttributeEntity entity2 = new InheritedLongAttributeEntity();
        
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<InheritedLongAttributeEntity> entities = new ArrayList<InheritedLongAttributeEntity>();

        List<InheritedLongAttributeEntity> entitiesToValidate = new ArrayList<>();
        List<InheritedLongAttributeEntity> entitiesToValidateDeleted = new ArrayList<>();

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }
            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
                            //manager.saveEntities(tmpList);
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }
        threads.removeAll(threads);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;


            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(IManagedEntity entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }

                            for(int t = delIdx; t < delIdx+5 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }


        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }


        pool.shutdownNow();

        long after = System.currentTimeMillis();

        for(InheritedLongAttributeEntity entity : entitiesToValidate)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.id == entity.id);
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
        }

        for(InheritedLongAttributeEntity entity : entitiesToValidateDeleted)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }
            Assert.assertTrue(pass);
        }
    }

    @Test
    public void concurrencySequenceDeleteBatchIntegrityTest() throws OnyxException, InterruptedException
    {
        final InheritedLongAttributeEntity entity2 = new InheritedLongAttributeEntity();
        
        entity2.longValue = 4l;
        entity2.longPrimitive = 3l;
        entity2.stringValue = "STring key";
        entity2.dateValue = new Date(1483736263743l);
        entity2.doublePrimitive = 342.23;
        entity2.doubleValue = 232.2;
        entity2.booleanPrimitive = true;
        entity2.booleanValue = false;

        manager.saveEntity(entity2);

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<InheritedLongAttributeEntity> entities = new ArrayList<InheritedLongAttributeEntity>();

        List<InheritedLongAttributeEntity> entitiesToValidate = new ArrayList<InheritedLongAttributeEntity>();
        List<InheritedLongAttributeEntity> entitiesToValidateDeleted = new ArrayList<InheritedLongAttributeEntity>();

        Map<Long, InheritedLongAttributeEntity> ignore = new HashMap<>();

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            if((i % 2) == 0)
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

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = () -> {
                    try
                    {
                        manager.saveEntities(tmpList);
                    } catch (OnyxException e)
                    {
                        e.printStackTrace();
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }
        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;

        for (int i = 0; i <= 10000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);

                            for(int t = delIdx; t < delIdx+5 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                deleteCount += 5;
                threads.add(pool.submit(runnable));
            }

        }


        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }


        pool.shutdownNow();

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(InheritedLongAttributeEntity entity : entitiesToValidate)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            if(!ignore.containsKey(newEntity.id))
            {
                newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
                Assert.assertTrue(newEntity.id == entity.id);
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
            }
        }

        int p = 0;

        for(InheritedLongAttributeEntity entity : entitiesToValidateDeleted)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }

            Assert.assertTrue(pass);
            p++;
        }
    }

    /**
     * Executes 10 threads that insert 30k entities with sequence id, then 10k are updated and 10k are deleted.
     * Then it validates the integrity of those actions
     * last test took: 1661(win) 2100(mac)
     * @throws OnyxException
     * @throws InterruptedException
     */
    @Test
    public void concurrencySequenceAllIntegrityTest() throws OnyxException, InterruptedException
    {
        SecureRandom random = new SecureRandom();

        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<InheritedLongAttributeEntity> entities = new ArrayList<InheritedLongAttributeEntity>();
        List<InheritedLongAttributeEntity> entitiesToValidate = new ArrayList<InheritedLongAttributeEntity>();
        List<InheritedLongAttributeEntity> entitiesToValidateDeleted = new ArrayList<InheritedLongAttributeEntity>();
        List<InheritedLongAttributeEntity> entitiesToValidateUpdated = new ArrayList<InheritedLongAttributeEntity>();

        Map<Long, InheritedLongAttributeEntity> ignore = new HashMap<>();

        /**
         * Save A whole bunch of records and keep track of some to update and delete
         */
        for (int i = 0; i <= 30000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);

            // Delete Even ones
            if((i % 2) == 0)
            {
                entitiesToValidateDeleted.add(entity);
                ignore.put(entity.id, entity);
            }
            // Update every third one
            else if((i % 3) == 0 && (i %2) != 0)
            {
                entitiesToValidateUpdated.add(entity);
            }
            else
            {
                entitiesToValidate.add(entity);
            }
            if ((i % 1000) == 0)
            {

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };
                threads.add(pool.submit(runnable));
            }

        }

        // Make Sure we Are done
        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }

        // Update an attribute
        for(InheritedLongAttributeEntity entity : entitiesToValidateUpdated)
        {
            entity.longPrimitive = 45645;
        }

        threads.removeAll(threads);
        entities.removeAll(entities);

        int deleteCount = 0;
        int updateCount = 0;

        for (int i = 0; i <= 30000; i++)
        {
            final InheritedLongAttributeEntity entity = new InheritedLongAttributeEntity();
            
            entity.longValue = 4l;
            entity.longPrimitive = 3l;
            entity.stringValue = "STring key";
            entity.dateValue = new Date(1483736263743l);
            entity.doublePrimitive = 342.23;
            entity.doubleValue = 232.2;
            entity.booleanPrimitive = true;
            entity.booleanValue = false;

            entities.add(entity);


            if ((i % 20) == 0)
            {

                entitiesToValidate.add(entity);

                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                final int indx = i;
                final int delIdx = deleteCount;
                final int updtIdx = updateCount;

                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            manager.saveEntities(tmpList);

                            for(int t = updtIdx; t < updtIdx+13 && t < entitiesToValidateUpdated.size(); t++)
                            {
                                manager.saveEntity(entitiesToValidateUpdated.get(t));
                            }

                            for(int t = delIdx; t < delIdx+30 && t < entitiesToValidateDeleted.size(); t++)
                            {
                                manager.deleteEntity(entitiesToValidateDeleted.get(t));
                            }
                        } catch (OnyxException e)
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

        for (Future future : threads)
        {
            try
            {
                future.get();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            } catch (ExecutionException e)
            {
                e.printStackTrace();
            }
        }


        pool.shutdownNow();

        long after = System.currentTimeMillis();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(InheritedLongAttributeEntity entity : entitiesToValidate)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            if(!ignore.containsKey(newEntity.id))
            {
                newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
                Assert.assertTrue(newEntity.id == entity.id);
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive);
            }
        }

        int i = 0;
        for(InheritedLongAttributeEntity entity : entitiesToValidateDeleted)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            boolean pass = false;
            try
            {
                newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
            }catch (NoResultsException e)
            {
                pass = true;
            }

            if(!pass)
            {
                i++;
            }
        }

        Assert.assertSame(0,i);

        for(InheritedLongAttributeEntity entity : entitiesToValidateUpdated)
        {
            InheritedLongAttributeEntity newEntity = new InheritedLongAttributeEntity();
            newEntity.id = entity.id;
            newEntity = (InheritedLongAttributeEntity)manager.find(newEntity);
            Assert.assertTrue(newEntity.longPrimitive == 45645);
        }
    }

}
