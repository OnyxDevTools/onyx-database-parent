package embedded.diskmap;

import category.EmbeddedDatabaseTests;
import com.onyx.diskmap.DefaultMapBuilder;
import com.onyx.diskmap.MapBuilder;
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
        entityYo.setId("OOO, this is an id");
        entityYo.setLongValue(23l);
        entityYo.setDateValue(new Date(1433233222));
        entityYo.setLongStringValue("This is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text");
        entityYo.setOtherStringValue("Normal text");
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

        EntityYo another = myMap.get(entityYo.getId());

        Assert.assertTrue(entityYo.getId().equals(another.getId()));
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

    @Test
    public void testNullObject()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, EntityYo> myMap = store.getHashMap("objectos");

        EntityYo entityYo = new EntityYo();
        entityYo.setId("OOO, this is an id");


        myMap.put(entityYo.getId(), entityYo);

        EntityYo another = myMap.get(entityYo.getId());

        Assert.assertTrue(entityYo.getId().equals(another.getId()));
        Assert.assertTrue(entityYo.getDateValue() == null);
        Assert.assertTrue(entityYo.getLongStringValue() == null);
        Assert.assertTrue(entityYo.getOtherStringValue() == null);
        Assert.assertTrue(entityYo.getMutableInteger() == null);
        Assert.assertTrue(entityYo.getMutableLong() == null);
        Assert.assertTrue(entityYo.getMutableBoolean() == null);
        Assert.assertTrue(entityYo.getMutableFloat() == null);
        Assert.assertTrue(entityYo.getMutableDouble() == null);
        Assert.assertTrue(entityYo.getImmutableInteger() == 0);
        Assert.assertTrue(entityYo.getImmutableLong() == 0l);
        Assert.assertTrue(entityYo.getImmutableBoolean() == false);
        Assert.assertTrue(entityYo.getImmutableFloat() == 0.0f);
        Assert.assertTrue(entityYo.getImmutableDouble() == 0.0);

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
