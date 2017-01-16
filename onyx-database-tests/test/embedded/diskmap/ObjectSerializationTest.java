package embedded.diskmap;

import category.EmbeddedDatabaseTests;
import com.onyx.structure.DefaultMapBuilder;
import com.onyx.structure.MapBuilder;
import entities.EntityYo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.*;

/**
 * Created by timothy.osborn on 4/2/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class ObjectSerializationTest extends AbstractTest
{
    @Test
    public void testPushObject()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, EntityYo> myMap = store.getHashMap("objectos");

        EntityYo entityYo = new EntityYo();
        entityYo.id = "OOO, this is an id";
        entityYo.longValue = 23l;
        entityYo.dateValue = new Date(1433233222);
        entityYo.longStringValue = "This is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text";
        entityYo.otherStringValue = "Normal text";
        entityYo.mutableInteger = 23;
        entityYo.mutableLong = 42l;
        entityYo.mutableBoolean = false;
        entityYo.mutableFloat = 23.2f;
        entityYo.mutableDouble = 23.1;
        entityYo.immutableInteger = 77;
        entityYo.immutableLong = 653356l;
        entityYo.immutableBoolean = true;
        entityYo.immutableFloat = 23.45f;
        entityYo.immutableDouble = 232.232;

        myMap.put(entityYo.id, entityYo);

        EntityYo another = myMap.get(entityYo.id);

        Assert.assertTrue(entityYo.id.equals(another.id));
        Assert.assertTrue(entityYo.longValue.equals(another.longValue));
        Assert.assertTrue(entityYo.dateValue.equals(another.dateValue));
        Assert.assertTrue(entityYo.longStringValue.equals(another.longStringValue));
        Assert.assertTrue(entityYo.otherStringValue.equals(another.otherStringValue));
        Assert.assertTrue(entityYo.mutableInteger.equals(another.mutableInteger));
        Assert.assertTrue(entityYo.mutableLong.equals(another.mutableLong));
        Assert.assertTrue(entityYo.mutableBoolean.equals(another.mutableBoolean));
        Assert.assertTrue(entityYo.mutableFloat.equals(another.mutableFloat));
        Assert.assertTrue(entityYo.mutableDouble.equals(another.mutableDouble));
        Assert.assertTrue(entityYo.immutableInteger == another.immutableInteger);
        Assert.assertTrue(entityYo.immutableLong == another.immutableLong);
        Assert.assertTrue(entityYo.immutableBoolean == another.immutableBoolean);
        Assert.assertTrue(entityYo.immutableFloat == another.immutableFloat);
        Assert.assertTrue(entityYo.immutableDouble == another.immutableDouble);

    }

    @Test
    public void testNullObject()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, EntityYo> myMap = store.getHashMap("objectos");

        EntityYo entityYo = new EntityYo();
        entityYo.id = "OOO, this is an id";


        myMap.put(entityYo.id, entityYo);

        EntityYo another = myMap.get(entityYo.id);

        Assert.assertTrue(entityYo.id.equals(another.id));
        Assert.assertTrue(entityYo.dateValue == null);
        Assert.assertTrue(entityYo.longStringValue == null);
        Assert.assertTrue(entityYo.otherStringValue == null);
        Assert.assertTrue(entityYo.mutableInteger == null);
        Assert.assertTrue(entityYo.mutableLong == null);
        Assert.assertTrue(entityYo.mutableBoolean == null);
        Assert.assertTrue(entityYo.mutableFloat == null);
        Assert.assertTrue(entityYo.mutableDouble == null);
        Assert.assertTrue(entityYo.immutableInteger == 0);
        Assert.assertTrue(entityYo.immutableLong == 0l);
        Assert.assertTrue(entityYo.immutableBoolean == false);
        Assert.assertTrue(entityYo.immutableFloat == 0.0f);
        Assert.assertTrue(entityYo.immutableDouble == 0.0);

    }


    @Test
    public void testSet()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, List> myMap = store.getHashMap("objectos");

        List<String> list = new ArrayList<>();
        list.add("HIYA1");
        list.add("HIYA2");
        list.add("HIYA3");
        list.add("HIYA4");
        list.add("HIYA5");

        myMap.put("FIRST", list);

        List<String> list2 = myMap.get("FIRST");
        Assert.assertTrue(list2.size() == list.size());
        Assert.assertTrue(list2.get(0).equals("HIYA1"));
        Assert.assertTrue(list2.get(4).equals("HIYA5"));
        Assert.assertTrue(list2 instanceof ArrayList);
    }

    @Test
    public void testHashSet()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, Set> myMap = store.getHashMap("objectos");

        Set<String> list = new HashSet<>();
        list.add("HIYA1");
        list.add("HIYA2");
        list.add("HIYA3");
        list.add("HIYA4");
        list.add("HIYA5");
        list.add("HIYA2");

        myMap.put("FIRST", list);

        Set<String> list2 = myMap.get("FIRST");
        Assert.assertTrue(list2.size() == list.size());
        Assert.assertTrue(list2 instanceof HashSet);
    }

    @Test
    public void testMap()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, Map> myMap = store.getHashMap("objectos");


        Map<String, Integer> list = new HashMap();
        list.put("HIYA1", 1);
        list.put("HIYA2", 2);
        list.put("HIYA3", 3);
        list.put("HIYA4", 4);
        list.put("HIYA5", 5);
        list.put("HIYA2", 6);

        myMap.put("FIRST", list);

        Map<String, Integer> list2 = myMap.get("FIRST");
        Assert.assertTrue(list2.size() == list.size());
        Assert.assertTrue(list2 instanceof HashMap);

        Assert.assertTrue(((HashMap) list2).get("HIYA2").equals(6));
    }
}
