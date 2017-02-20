package embedded.diskmap;

import category.EmbeddedDatabaseTests;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by timothy.osborn on 3/27/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class MultiThreadTest extends AbstractTest {

    ExecutorService pool = Executors.newFixedThreadPool(9);

    static Random randomGenerator = new Random();
    @Test
    public void testMultiThread()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Object> myMap = store.getHashMap("second");

        long time = System.currentTimeMillis();
        List<Future> items = new ArrayList<>();
        for(int i = 1; i < 20; i++)
        {
            final int p = randomGenerator.nextInt((19 - 1) + 1) + 1;

            if((i % 5) == 0)
            {
                Runnable scanThread = new Runnable() {
                    @Override
                    public void run()
                    {
                        Iterator it = myMap.entrySet().iterator();

                        while (it.hasNext())
                        {
                            Map.Entry entry = (Map.Entry) it.next();
                            entry.getKey();
                        }
                    }
                };

                items.add(pool.submit(scanThread));
            }
            MyRunnable runnable = new MyRunnable(myMap, p);
            items.add(pool.submit(runnable));
        }

        items.stream().forEach(
                future ->
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
        );

        System.out.println("Done in " + (time - System.currentTimeMillis()));
        store.close();
    }

    class MyRunnable implements Runnable
    {
        protected Map myMap = null;
        int p;

        public MyRunnable(Map myMap, int p)
        {
            this.myMap = myMap;
            this.p = p;

        }

        @Override
        public void run()
        {
            final int it = p;
            for(int k = 1; k < 5000; k++)
            {
                Integer val = k * p;
                myMap.put(val, "HIYA" + val);
                String strVal = (String)myMap.get(val);
                myMap.remove(val);
                strVal = (String)myMap.get(val);
            }
        }
    }

}
