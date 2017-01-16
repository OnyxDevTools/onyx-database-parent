package remote.save;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.IManagedEntity;
import entities.InheritedLongAttributeEntity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ RemoteServerTests.class })
public class SequenceIndexConcurrencyTest extends RemoteBaseTest {

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

    /**
     * Tests Batch inserting 100,000 records with a Sequence identifier
     * This test is executed using 10 concurrent threads
     * last test took: 1670(win) 2200(mac)
     * @throws EntityException
     * @throws InterruptedException
     */
    @Test
    public void aConcurrencySequencePerformanceTest() throws EntityException, InterruptedException
    {
        SecureRandom random = new SecureRandom();
        long time = System.currentTimeMillis();

        List<Future> threads = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<InheritedLongAttributeEntity> entities = new ArrayList<>();

        for (int i = 0; i <= 100000; i++)
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

            if ((i % 5000) == 0)
            {
                List<IManagedEntity> tmpList = new ArrayList<>(entities);
                entities.removeAll(entities);
                Runnable runnable = () -> {
                    try
                    {
                        manager.saveEntities(tmpList);
                    } catch (EntityException e)
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

        long after = System.currentTimeMillis();

        System.out.println("Took " + (after - time) + " milliseconds");

        Assert.assertTrue((after - time) < 5000);

        pool.shutdownNow();
    }


}
