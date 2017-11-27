package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import entities.EntityYo
import org.junit.Test

import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 4/2/15.
 */
class NamedObjectMapTest : AbstractTest() {

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

        assertEquals(entityYo.id, another!!.id)
        assertEquals(entityYo.longValue, another.longValue)
        assertEquals(entityYo.dateValue, another.dateValue)
        assertEquals(entityYo.longStringValue, another.longStringValue)
        assertEquals(entityYo.otherStringValue, another.otherStringValue)
        assertEquals(entityYo.mutableInteger, another.mutableInteger)
        assertEquals(entityYo.mutableLong, another.mutableLong)
        assertEquals(entityYo.mutableBoolean, another.mutableBoolean)
        assertEquals(entityYo.mutableFloat, another.mutableFloat)
        assertEquals(entityYo.mutableDouble, another.mutableDouble)
        assertEquals(entityYo.immutableInteger, another.immutableInteger)
        assertEquals(entityYo.immutableLong, another.immutableLong)
        assertEquals(entityYo.immutableBoolean, another.immutableBoolean)
        assertEquals(entityYo.immutableFloat, another.immutableFloat)
        assertEquals(entityYo.immutableDouble, another.immutableDouble)

    }

    @Test
    fun testNullObject() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, EntityYo>>("objectos")

        val entityYo = EntityYo()
        entityYo.id = "OOO, this is an id"


        myMap.put(entityYo.id!!, entityYo)

        val another = myMap[entityYo.id!!]

        assertEquals(entityYo.id, another!!.id)
        assertNull(entityYo.dateValue)
        assertNull(entityYo.longStringValue)
        assertNull(entityYo.otherStringValue)
        assertNull(entityYo.mutableInteger)
        assertNull(entityYo.mutableLong)
        assertNull(entityYo.mutableBoolean)
        assertNull(entityYo.mutableFloat)
        assertNull(entityYo.mutableDouble)
        assertEquals(0, entityYo.immutableInteger)
        assertEquals(0L, entityYo.immutableLong)
        assertEquals(false, entityYo.immutableBoolean)
        assertEquals(0.0f, entityYo.immutableFloat)
        assertEquals(0.0, entityYo.immutableDouble)

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
        assertEquals(list2!!.size, list.size)
        assertEquals(list2[0], "HIYA1")
        assertEquals(list2[4], "HIYA5")
        assertTrue(list2 is ArrayList<*>)
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
        assertEquals(list2!!.size, list.size)
        assertTrue(list2 is HashSet<*>)
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
        assertEquals(list2!!.size, list.size)
        assertTrue(list2 is HashMap<*, *>)

        assertEquals(6, (list2 as HashMap<*, *>)["HIYA2"])
    }
}
