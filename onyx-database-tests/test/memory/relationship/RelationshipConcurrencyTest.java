package memory.relationship;

import category.InMemoryDatabaseTests;
import com.onyx.exception.OnyxException;
import entities.relationship.ManyToManyChild;
import entities.relationship.ManyToManyParent;
import entities.relationship.OneToOneChild;
import entities.relationship.OneToOneParent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ InMemoryDatabaseTests.class })
public class RelationshipConcurrencyTest extends memory.base.BaseTest
{

    @Before
    public void before() throws OnyxException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testOneToOneCascadeConcurrency() throws OnyxException
    {
        SecureRandom random = new SecureRandom();
        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(20);

        List<OneToOneParent> entities = new ArrayList<OneToOneParent>();

        List<OneToOneParent> entitiesToValidate = new ArrayList<OneToOneParent>();

        for (int i = 0; i <= 10000; i++)
        {
            final OneToOneParent entity = new OneToOneParent();
            entity.identifier = new BigInteger(130, random).toString(32);
            entity.correlation = 4;
            entity.cascadeChild = new OneToOneChild();
            entity.cascadeChild.identifier = new BigInteger(130, random).toString(32);
            entity.child = new OneToOneChild();
            entity.child.identifier = new BigInteger(130, random).toString(32);
            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 10) == 0)
            {

                List<OneToOneParent> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(OneToOneParent entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
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

        pool.shutdownNow();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(OneToOneParent entity : entitiesToValidate)
        {
            OneToOneParent newEntity = new OneToOneParent();
            newEntity.identifier = entity.identifier;
            manager.find(newEntity);
            Assert.assertTrue(newEntity.identifier.equals(entity.identifier));
            Assert.assertTrue(newEntity.cascadeChild != null);
            Assert.assertTrue(newEntity.child != null);
        }
    }


    @Test
    public void testManyToManyCascadeConcurrency() throws OnyxException
    {
        SecureRandom random = new SecureRandom();
        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(20);

        List<ManyToManyParent> entities = new ArrayList<ManyToManyParent>();

        List<ManyToManyParent> entitiesToValidate = new ArrayList<ManyToManyParent>();

        for (int i = 0; i <= 10000; i++)
        {
            final ManyToManyParent entity = new ManyToManyParent();
            entity.identifier = new BigInteger(130, random).toString(32);
            entity.correlation = 4;
            entity.childCascade = new ArrayList<>();
            ManyToManyChild child = new ManyToManyChild();
            child.identifier = new BigInteger(130, random).toString(32);

            entity.childCascade.add(child);

            entities.add(entity);

            entitiesToValidate.add(entity);

            if ((i % 100) == 0)
            {

                List<ManyToManyParent> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(ManyToManyParent entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
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

        pool.shutdownNow();

        System.out.println("Took "+(after-time)+" milliseconds");

        for(ManyToManyParent entity : entitiesToValidate)
        {
            ManyToManyParent newEntity = new ManyToManyParent();
            newEntity.identifier = entity.identifier;
            manager.find(newEntity);
            Assert.assertTrue(newEntity.identifier.equals(entity.identifier));
            Assert.assertTrue(newEntity.childCascade.get(0) != null);
            Assert.assertTrue(newEntity.childCascade.get(0).identifier.equals(entity.childCascade.get(0).identifier));
        }
    }

    @Test
    public void testManyToManyCascadeConcurrencyMultiple() throws OnyxException
    {
        SecureRandom random = new SecureRandom();
        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(20);

        List<ManyToManyParent> entities = new ArrayList<ManyToManyParent>();
        List<ManyToManyParent> entities2 = new ArrayList<ManyToManyParent>();

        List<ManyToManyParent> entitiesToValidate = new ArrayList<ManyToManyParent>();

        for (int i = 0; i <= 10000; i++)
        {
            final ManyToManyParent entity = new ManyToManyParent();
            entity.identifier = new BigInteger(130, random).toString(32);
            entity.correlation = 4;
            entity.childCascadeSave = new ArrayList<>();
            ManyToManyChild child = new ManyToManyChild();
            child.identifier = new BigInteger(130, random).toString(32);
            entity.childCascadeSave.add(child);
            entities.add(entity);

            final ManyToManyParent entity2 = new ManyToManyParent();
            entity2.identifier = entity.identifier;
            entity2.correlation = 4;
            entity2.childCascadeSave = new ArrayList<>();
            ManyToManyChild child2 = new ManyToManyChild();
            child2.identifier = new BigInteger(130, random).toString(32);

            entity2.childCascadeSave.add(child2);
            entity2.childCascadeSave.add(child);
            entities2.add(entity2);

            entitiesToValidate.add(entity);

            if ((i % 100) == 0)
            {

                List<ManyToManyParent> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(ManyToManyParent entity1 : tmpList)
                            {
                                manager.saveEntity(entity1);
                            }
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };

                List<ManyToManyParent> tmpList2 = new ArrayList<>(entities2);
                entities2.removeAll(entities2);
                Runnable runnable2 = new Runnable() {
                    @Override
                    public void run()
                    {
                        try
                        {
                            for(ManyToManyParent entity1 : tmpList2)
                            {
                                manager.saveEntity(entity1);
                            }
                        } catch (OnyxException e)
                        {
                            e.printStackTrace();
                        }
                    }
                };

                threads.add(pool.submit(runnable));
                threads.add(pool.submit(runnable2));
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

        pool.shutdownNow();

        System.out.println("Took "+(after-time)+" milliseconds");

        int failures = 0;

        for(ManyToManyParent entity : entitiesToValidate)
        {
            ManyToManyParent newEntity = new ManyToManyParent();
            newEntity.identifier = entity.identifier;
            manager.find(newEntity);
            Assert.assertTrue(newEntity.identifier.equals(entity.identifier));
            if(newEntity.childCascadeSave.size() != 2)
            {
                failures++;
            }
        }

        Assert.assertTrue(failures == 0);

    }

}
