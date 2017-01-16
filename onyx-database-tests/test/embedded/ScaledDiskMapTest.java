package embedded;

import com.onyx.structure.DefaultMapBuilder;
import com.onyx.structure.MapBuilder;
import com.onyx.structure.store.StoreType;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tosborn1 on 1/6/17.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ScaledDiskMapTest
{

    public static final String TEST_DATABASE = "C:/Sandbox/Onyx/Tests/load.db";

    @Test
    public void testInsert()
    {
        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE, StoreType.MEMORY_MAPPED_FILE);
        Map<Integer, Integer> skipList = builder.getScalableMap("first", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 50000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);
        }

        final AtomicInteger i = new AtomicInteger(0);
        keyValues.forEach((o, o2) -> {
            Integer inte = skipList.get(o);
            i.addAndGet(1);
            if(inte == null)
                assert skipList.get(o).equals(o2);
        });

    }

    @Test
    public void testDelete()
    {

        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Integer> skipList = builder.getScalableMap("second", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 50000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);

        }
        Map newKeyValues = new HashMap();
        Map deletedKeyValues = new HashMap();

        final AtomicInteger val = new AtomicInteger(0);
        keyValues.forEach((o, o2) -> {

            assert skipList.get(o).equals(o2);

            if((val.addAndGet(1) % 1000) == 0)
            {
                skipList.remove(o);
                deletedKeyValues.put(o, o2);
            }
            else
            {
                newKeyValues.put(o, o2);
            }
        });

        newKeyValues.forEach((o, o2) -> {
            assert skipList.get(o).equals(o2);
        });

        deletedKeyValues.forEach((o, o2) -> {
            assert skipList.get(o) == null;
        });

    }

    @Test
    public void testUpdate()
    {
        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Integer> skipList = builder.getScalableMap("third", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 10000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);

        }

        keyValues.forEach((o, o2) -> skipList.put((Integer)o, (Integer)o2));
    }
    @Test
    public void testForEach()
    {
        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Integer> skipList = builder.getScalableMap("third", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 50000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);

        }

        final AtomicInteger val = new AtomicInteger(0);
        keyValues.forEach((o, o2) -> {

            assert skipList.get(o).equals(o2);

            if((val.addAndGet(1) % 1000) == 0)
            {
                skipList.remove(o);
            }
        });


        final AtomicInteger numberOfValues = new AtomicInteger(0);
        skipList.forEach((integer, integer2) -> {
            assert integer != null;
            assert integer2 != null;
            numberOfValues.addAndGet(1);
        });


        assert numberOfValues.get() == skipList.size();
    }

    @Test
    public void testKeyIterator()
    {
        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Integer> skipList = builder.getScalableMap("fourth", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 50000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);
        }

        final AtomicInteger val = new AtomicInteger(0);
        keyValues.forEach((o, o2) -> {

            assert skipList.get(o).equals(o2);

            if((val.addAndGet(1) % 1000) == 0)
            {
                skipList.remove(o);
            }
        });


        final AtomicInteger numberOfValues = new AtomicInteger(0);

        Iterator<Integer> iterator = skipList.keySet().iterator();
        while(iterator.hasNext())
        {
            assert iterator.next() instanceof Integer;
            numberOfValues.addAndGet(1);
        }


        assert numberOfValues.get() == skipList.size();
    }

    @Test
    public void testValueIterator()
    {
        MapBuilder builder = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Integer> skipList = builder.getScalableMap("fifth", 2);

        Map keyValues = new HashMap();
        for(int i = 0; i < 50000; i++)
        {
            int randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);

            skipList.put(randomNum, randomValue);
            keyValues.put(randomNum, randomValue);

        }

        final AtomicInteger val = new AtomicInteger(0);
        keyValues.forEach((o, o2) -> {

            assert skipList.get(o).equals(o2);

            if((val.addAndGet(1) % 1000) == 0)
            {
                skipList.remove(o);
            }
        });


        final AtomicInteger numberOfValues = new AtomicInteger(0);

        Iterator<Integer> iterator = skipList.values().iterator();
        while(iterator.hasNext())
        {
            assert iterator.next() instanceof Integer;
            numberOfValues.addAndGet(1);
        }


        assert numberOfValues.get() == skipList.size();
    }
}
