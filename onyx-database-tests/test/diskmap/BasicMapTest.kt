@file:Suppress("LoopToCallChain")

package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import database.base.DatabaseBaseTest
import entities.EntityYo
import org.junit.Test

import java.util.Date
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Created by Tim Osborn on 3/21/15.
 */
class BasicMapTest : AbstractTest() {

    @Test
    fun putTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Any>>("first")
        myMap.put("MY NEW STRING", "Hi1ya1")
        assertEquals(myMap["MY NEW STRING"], "Hi1ya1", "Failed to put string into map")
        store.close()
    }

    @Test
    fun getTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Any>>("first")
        myMap.put("MY NEW STRING", "Hi1ya1")
        val value = myMap["MY NEW STRING"] as String
        assertEquals("Hi1ya1", value)
        store.close()
    }

    @Test
    fun deleteTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any>>("second")

        for (i in 0..9999) {
            myMap.put(i, "Hiya")
        }

        for (i in 5000..9999) {
            myMap.remove(i)
        }

        for (i in 0..4999) {
            val value = myMap[i] as String
            assertEquals("Hiya", value)
        }

        for (i in 5000..9999) {
            val value = myMap[i]
            assertNull(value)
        }

        store.close()
    }

    @Test
    fun deleteRoot() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any?>>("second")

        for (i in 0..9999) {
            myMap.put(i, "Hi1ya1")
        }

        myMap.remove(0)

        for (i in 1..9999) {
            val value = myMap[i] as String
            assertEquals("Hi1ya1", value)
        }

        val value = myMap[0]
        assertNull(value)

        store.close()
    }

    @Test
    fun largeDataSetTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any>>("seconds")

        for (i in 0..499999) {
            myMap.put(i, "Hiya")
        }

        store.commit()

        for (i in 0..499999) {
            val value = myMap[i] as String
            assertEquals("Hiya", value)
        }

        val it = myMap.entries.iterator()
        var i = 0
        while (it.hasNext()) {
            val entry = it.next() as Map.Entry<*, *>
            entry.value
            i++
        }

        assertEquals(500000, i)
        store.close()
    }

    @Test
    fun updateTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any?>>("second")

        for (i in 0..99999) {
            myMap.put(i, "Hiya")
        }

        for (i in 5000..9999) {
            myMap.remove(i)
        }

        for (i in 0..4999) {
            assertEquals("Hiya", myMap[i])
        }

        for (i in 3000..5999) {
            myMap.put(i, "Wheee woooooo haaaa" + i)
        }

        for (i in 6000..9999) {
            val value = myMap[i]
            assertNull(value)
        }

        for (i in 3000..5999) {
            val value = myMap[i] as String
            assertEquals("Wheee woooooo haaaa" + i, value)
        }

        store.close()
    }

    @Test
    fun testPushMultipleObjects() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)

        val service = Executors.newFixedThreadPool(3)

        val f1 = DatabaseBaseTest.async(service) { testPushObjects(store, 1) }
        val f2 = DatabaseBaseTest.async(service) { testPushObjects(store, 2) }
        val f3 = DatabaseBaseTest.async(service) { testPushObjects(store, 3) }

        f1.get()
        f2.get()
        f3.get()

        store.close()
    }

    @Test
    fun testPushSingleObjects() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val service = Executors.newFixedThreadPool(3)

        val f1 = DatabaseBaseTest.async(service) { testPushObjects(store, 1) }
        f1.get()

        store.close()
    }

    private fun testPushObjects(store: DiskMapFactory, hashMapId: Int) {
        val myMap = store.getHashMap<MutableMap<String, EntityYo>>("objectos" + hashMapId)

        var entityYo: EntityYo? = null

        for (i in 0..99999) {
            entityYo = EntityYo()
            entityYo.id = "OOO, this is an id" + i
            entityYo.longValue = 23L
            entityYo.dateValue = Date(1433233222)
            entityYo.longStringValue = "This is a really long string key wooo, long string textThis is a really long string key wooo, long string key wooo, long string textThis is a really long string key wooo, long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text"
            entityYo.otherStringValue = "Normal text but still has some kind of content"
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
        }

        store.commit()

        var another: EntityYo?

        for (i in 0..99999) {
            another = myMap["OOO, this is an id" + i]
            assertEquals(entityYo!!.longValue, another!!.longValue)
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

        val it = myMap.entries.iterator()
        var i = 0
        while (it.hasNext()) {
            val entry = it.next() as Map.Entry<*, *>
            entry.value
            i++
        }

        assertEquals(100000, i)
    }
}
