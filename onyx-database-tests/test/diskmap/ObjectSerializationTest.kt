package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import entities.EntityYo
import org.junit.Assert
import org.junit.Test

import java.util.*

/**
 * Created by timothy.osborn on 4/2/15.
 */
class ObjectSerializationTest : AbstractTest() {
    @Test
    fun testPushObject() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, EntityYo>>("objectos")

        val entityYo = EntityYo()
        entityYo.id = "OOO, this is an id"
        entityYo.longValue = 23L
        entityYo.dateValue = Date(1433233222)
        entityYo.longStringValue = "This is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text"
        entityYo.otherStringValue = "Normal text"
        entityYo.mutableInteger = 23
        entityYo.mutableLong = 42L
        entityYo.mutableBoolean = false
        entityYo.mutableFloat = 23.2f
        entityYo.mutableDouble = 23.1
        entityYo.immutableInteger = 77
        entityYo.immutableLong = 653356L
        entityYo.immutableBoolean = true
        entityYo.immutableFloat = 23.45f
        entityYo.immutableDouble = 232.232

        myMap.put(entityYo.id!!, entityYo)

        val another = myMap[entityYo.id!!]

        Assert.assertTrue(entityYo.id == another!!.id)
        Assert.assertTrue(entityYo.longValue == another.longValue)
        Assert.assertTrue(entityYo.dateValue == another.dateValue)
        Assert.assertTrue(entityYo.longStringValue == another.longStringValue)
        Assert.assertTrue(entityYo.otherStringValue == another.otherStringValue)
        Assert.assertTrue(entityYo.mutableInteger == another.mutableInteger)
        Assert.assertTrue(entityYo.mutableLong == another.mutableLong)
        Assert.assertTrue(entityYo.mutableBoolean == another.mutableBoolean)
        Assert.assertTrue(entityYo.mutableFloat == another.mutableFloat)
        Assert.assertTrue(entityYo.mutableDouble == another.mutableDouble)
        Assert.assertTrue(entityYo.immutableInteger == another.immutableInteger)
        Assert.assertTrue(entityYo.immutableLong == another.immutableLong)
        Assert.assertTrue(entityYo.immutableBoolean == another.immutableBoolean)
        Assert.assertTrue(entityYo.immutableFloat == another.immutableFloat)
        Assert.assertTrue(entityYo.immutableDouble == another.immutableDouble)

    }

    @Test
    fun testNullObject() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, EntityYo>>("objectos")

        val entityYo = EntityYo()
        entityYo.id = "OOO, this is an id"


        myMap.put(entityYo.id!!, entityYo)

        val another = myMap[entityYo.id!!]

        Assert.assertTrue(entityYo.id == another!!.id)
        Assert.assertTrue(entityYo.dateValue == null)
        Assert.assertTrue(entityYo.longStringValue == null)
        Assert.assertTrue(entityYo.otherStringValue == null)
        Assert.assertTrue(entityYo.mutableInteger == null)
        Assert.assertTrue(entityYo.mutableLong == null)
        Assert.assertTrue(entityYo.mutableBoolean == null)
        Assert.assertTrue(entityYo.mutableFloat == null)
        Assert.assertTrue(entityYo.mutableDouble == null)
        Assert.assertTrue(entityYo.immutableInteger == 0)
        Assert.assertTrue(entityYo.immutableLong == 0L)
        Assert.assertTrue(entityYo.immutableBoolean == false)
        Assert.assertTrue(entityYo.immutableFloat == 0.0f)
        Assert.assertTrue(entityYo.immutableDouble == 0.0)

    }


    @Test
    fun testSet() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, List<*>>>("objectos")

        val list = ArrayList<String>()
        list.add("HIYA1")
        list.add("HIYA2")
        list.add("HIYA3")
        list.add("HIYA4")
        list.add("HIYA5")

        myMap.put("FIRST", list)

        val list2 = myMap["FIRST"]
        Assert.assertTrue(list2!!.size == list!!.size)
        Assert.assertTrue(list2.get(0) == "HIYA1")
        Assert.assertTrue(list2.get(4) == "HIYA5")
        Assert.assertTrue(list2 is ArrayList<*>)
    }

    @Test
    fun testHashSet() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Set<*>>>("objectos")

        val list = HashSet<String>()
        list.add("HIYA1")
        list.add("HIYA2")
        list.add("HIYA3")
        list.add("HIYA4")
        list.add("HIYA5")
        list.add("HIYA2")

        myMap.put("FIRST", list)

        val list2 = myMap["FIRST"]
        Assert.assertTrue(list2!!.size == list!!.size)
        Assert.assertTrue(list2 is HashSet<*>)
    }

    @Test
    fun testMap() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Map<*, *>>>("objectos")


        val list = HashMap<String, Int>()
        list.put("HIYA1", 1)
        list.put("HIYA2", 2)
        list.put("HIYA3", 3)
        list.put("HIYA4", 4)
        list.put("HIYA5", 5)
        list.put("HIYA2", 6)

        myMap.put("FIRST", list)

        val list2 = myMap["FIRST"]
        Assert.assertTrue(list2!!.size == list.size)
        Assert.assertTrue(list2 is HashMap<*, *>)

        Assert.assertTrue((list2 as HashMap<*, *>)["HIYA2"] == 6)
    }
}
