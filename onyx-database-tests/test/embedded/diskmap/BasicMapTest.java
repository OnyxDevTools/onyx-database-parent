package embedded.diskmap;

import category.EmbeddedDatabaseTests;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
import entities.EntityYo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by tosborn on 3/21/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class BasicMapTest extends AbstractTest
{

    @Test
    public void putTest()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, Object> myMap = store.getHashMap("first");

        myMap.put("MY NEW STRING", "Hi1ya1");
        store.close();
    }

    @Test
    public void getTest()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, Object> myMap = store.getHashMap("first");

        myMap.put("MY NEW STRING", "Hi1ya1");

        String value = (String)myMap.get("MY NEW STRING");

        Assert.assertTrue(value.equals("Hi1ya1"));
        store.close();
    }

    @Test
    public void deleteTest()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Object> myMap = store.getHashMap("second");

        long time = System.currentTimeMillis();
        for(int i = 0; i < 10000; i++)
        {
            myMap.put(new Integer(i), "Hiya");
        }

        System.out.println("Took " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        for(int i = 5000; i < 10000; i++)
        {
            myMap.remove(new Integer(i));
        }

        for(int i = 0; i < 5000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value.equals("Hiya"));
        }

        for(int i = 5000; i < 10000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value == null);
        }

        System.out.println("Took " + (System.currentTimeMillis() - time));
        store.close();
    }

    @Test
    public void deleteRoot()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Object> myMap = store.getHashMap("second");

        long time = System.currentTimeMillis();
        for(int i = 0; i < 10000; i++)
        {
            myMap.put(new Integer(i), "Hi1ya1");
        }

        System.out.println("Took " + (System.currentTimeMillis() - time));

        myMap.remove(new Integer(0));

        time = System.currentTimeMillis();
        for(int i = 1; i < 10000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value.equals("Hi1ya1"));
        }

        String value = (String)myMap.get(new Integer(0));
        Assert.assertTrue(value == null);

        System.out.println("Took " + (System.currentTimeMillis() - time));
        store.close();
    }

    @Test
    public void jumboTest()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Object> myMap = store.getHashMap("seconds");

        System.out.println("Starting Jumbo");
        long time = System.currentTimeMillis();
        for(int i = 0; i < 500000; i++)
        {
            myMap.put(new Integer(i), "Hiya");
        }

        store.commit();

        System.out.println("Took " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        for(int i = 0; i < 500000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value.equals("Hiya"));
        }


        System.out.println("Took " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        Iterator it = myMap.entrySet().iterator();
        int i = 0;
        while(it.hasNext())
        {
            Map.Entry entry = (Map.Entry )it.next();
            entry.getValue();
            i++;
        }
        System.out.println("Took " + (System.currentTimeMillis() - time) + " for this many " + i);
        store.close();

        System.out.println("Done with Jumbo");

    }

    @Test
    public void updateTest()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<Integer, Object> myMap = store.getHashMap("second");

        long time = System.currentTimeMillis();
        for(int i = 0; i < 100000; i++)
        {
            myMap.put(new Integer(i), "Hiya");
        }

        System.out.println("Took " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        for(int i = 5000; i < 10000; i++)
        {
            myMap.remove(new Integer(i));
        }

        for(int i = 0; i < 5000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value.equals("Hiya"));
        }

        for(int i = 3000; i < 6000; i++)
        {
            myMap.put(new Integer(i), "Wheee woooooo haaaa" + i);
        }

        for(int i = 6000; i < 10000; i++)
        {
            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value == null);
        }

        for(int i = 3000; i < 6000; i++)
        {

            String value = (String)myMap.get(new Integer(i));
            Assert.assertTrue(value.equals("Wheee woooooo haaaa" + i));
        }

        System.out.println("Took " + (System.currentTimeMillis() - time));
        store.close();
    }

    @Test
    public void testPushMultipleObjects()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);

        ExecutorService service = Executors.newFixedThreadPool(3);

        Runnable t = new Runnable() {
            @Override
            public void run()
            {
                testPushObjects(store, 1);
            }
        };

        Future f1 = service.submit(t);

        Runnable t2 = new Runnable() {
            @Override
            public void run()
            {
                testPushObjects(store, 2);
            }
        };

        Future f2 = service.submit(t2);

        Runnable t3 = new Runnable() {
            @Override
            public void run()
            {
                testPushObjects(store, 3);
            }
        };

        Future f3 = service.submit(t3);

        try
        {
            f1.get();
            f2.get();
            f3.get();
        }catch (Exception e){
            e.printStackTrace();
        }

        store.close();


    }

    @Test
    public void testPushSingleObjects()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);

        ExecutorService service = Executors.newFixedThreadPool(3);

        Runnable t = new Runnable() {
            @Override
            public void run()
            {
                testPushObjects(store, 1);
            }
        };

        Future f1 = service.submit(t);

        try
        {
            f1.get();
        }catch (Exception e){
            e.printStackTrace();
        }

        store.close();


    }

    public void testPushObjects(MapBuilder store, int hashMapId)
    {
        Map<String, EntityYo> myMap = store.getHashMap("objectos" + hashMapId);

        long time = System.currentTimeMillis();

        EntityYo entityYo = null;

        for(int i = 0; i < 100000; i++)
        {
            entityYo = new EntityYo();
            entityYo.setId("OOO, this is an id" + i);
            entityYo.setLongValue(23l);
            entityYo.setDateValue(new Date(1433233222));
            entityYo.setLongStringValue("This is a really long string key wooo, long string textThis is a really long string key wooo, long striring key wooo, long string textThis is a really long string key wooo, long striring key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text");
            entityYo.setOtherStringValue("Normal text but still has some kind of content");
            entityYo.setMutableInteger(23);
            entityYo.setMutableLong(42l);
            entityYo.setMutableBoolean(false);
            entityYo.setMutableFloat(23.2f);
            entityYo.setMutableDouble(23.1);
            entityYo.setImmutableInteger(77);
            entityYo.setImmutableLong(653356l);
            entityYo.setImmutableBoolean(true);
            entityYo.setImmutableFloat(23.45f);
            entityYo.setImmutableDouble(232.232);

            myMap.put(entityYo.getId(), entityYo);
        }

        store.commit();

        System.out.println("Took " + (System.currentTimeMillis() - time));

        EntityYo another = null;

        time = System.currentTimeMillis();
        for(int i = 0; i < 100000; i++)
        {
            another = myMap.get("OOO, this is an id" + i);
            Assert.assertTrue(entityYo.getLongValue().equals(another.getLongValue()));
            Assert.assertTrue(entityYo.getDateValue().equals(another.getDateValue()));
            Assert.assertTrue(entityYo.getLongStringValue().equals(another.getLongStringValue()));
            Assert.assertTrue(entityYo.getOtherStringValue().equals(another.getOtherStringValue()));
            Assert.assertTrue(entityYo.getMutableInteger().equals(another.getMutableInteger()));
            Assert.assertTrue(entityYo.getMutableLong().equals(another.getMutableLong()));
            Assert.assertTrue(entityYo.getMutableBoolean().equals(another.getMutableBoolean()));
            Assert.assertTrue(entityYo.getMutableFloat().equals(another.getMutableFloat()));
            Assert.assertTrue(entityYo.getMutableDouble().equals(another.getMutableDouble()));
            Assert.assertTrue(entityYo.getImmutableInteger() == another.getImmutableInteger());
            Assert.assertTrue(entityYo.getImmutableLong() == another.getImmutableLong());
            Assert.assertTrue(entityYo.getImmutableBoolean() == another.getImmutableBoolean());
            Assert.assertTrue(entityYo.getImmutableFloat() == another.getImmutableFloat());
            Assert.assertTrue(entityYo.getImmutableDouble() == another.getImmutableDouble());
        }


        System.out.println("Took " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        Iterator it = myMap.entrySet().iterator();
        int i = 0;
        while(it.hasNext())
        {
            Map.Entry entry = (Map.Entry )it.next();
            entry.getValue();
            i++;
        }
        System.out.println("Took " + (System.currentTimeMillis() - time) + " for this many " + i);

        System.out.println("Done with Jumbo Named");

    }

}
