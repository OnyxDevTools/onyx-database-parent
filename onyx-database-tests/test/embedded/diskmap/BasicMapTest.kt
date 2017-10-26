package embedded.diskmap

import category.EmbeddedDatabaseTests
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import entities.EntityYo
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Created by Tim Osborn on 3/21/15.
 */
@Category(EmbeddedDatabaseTests::class)
class BasicMapTest : AbstractTest() {

    @Test
    fun putTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Any>>("first")

        myMap.put("MY NEW STRING", "Hi1ya1")
        store.close()
    }

    @Test
    fun getTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<String, Any>>("first")

        myMap.put("MY NEW STRING", "Hi1ya1")

        val value = myMap["MY NEW STRING"] as String

        Assert.assertTrue(value == "Hi1ya1")
        store.close()
    }

    @Test
    fun deleteTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any>>("second")

        var time = System.currentTimeMillis()
        for (i in 0..9999) {
            myMap.put(i, "Hiya")
        }

        println("Took " + (System.currentTimeMillis() - time))

        time = System.currentTimeMillis()
        for (i in 5000..9999) {
            myMap.remove(i)
        }

        for (i in 0..4999) {
            val value = myMap[i] as String
            Assert.assertTrue(value == "Hiya")
        }

        for (i in 5000..9999) {
            val value = myMap[i]
            Assert.assertTrue(value == null)
        }

        println("Took " + (System.currentTimeMillis() - time))
        store.close()
    }

    @Test
    fun deleteRoot() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any?>>("second")

        var time = System.currentTimeMillis()
        for (i in 0..9999) {
            myMap.put(i, "Hi1ya1")
        }

        println("Took " + (System.currentTimeMillis() - time))

        myMap.remove(0)

        time = System.currentTimeMillis()
        for (i in 1..9999) {
            val value = myMap[i] as String
            Assert.assertTrue(value == "Hi1ya1")
        }

        val value = myMap[0]
        Assert.assertTrue(value == null)

        println("Took " + (System.currentTimeMillis() - time))
        store.close()
    }

    @Test
    fun jumboTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any>>("seconds")

        println("Starting Jumbo")
        var time = System.currentTimeMillis()
        for (i in 0..499999) {
            myMap.put(i, "Hiya")
        }

        store.commit()

        println("Took " + (System.currentTimeMillis() - time))

        time = System.currentTimeMillis()
        for (i in 0..499999) {
            val value = myMap[i] as String
            Assert.assertTrue(value == "Hiya")
        }


        println("Took " + (System.currentTimeMillis() - time))

        time = System.currentTimeMillis()
        val it = myMap.entries.iterator()
        var i = 0
        while (it.hasNext()) {
            val entry = it.next() as Map.Entry<*, *>
            entry.value
            i++
        }
        println("Took " + (System.currentTimeMillis() - time) + " for this many " + i)
        store.close()

        println("Done with Jumbo")

    }

    @Test
    fun updateTest() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, Any?>>("second")

        var time = System.currentTimeMillis()
        for (i in 0..99999) {
            myMap.put(i, "Hiya")
        }

        println("Took " + (System.currentTimeMillis() - time))

        time = System.currentTimeMillis()
        for (i in 5000..9999) {
            myMap.remove(i)
        }

        for (i in 0..4999) {
            val value = myMap[i] as String
            Assert.assertTrue(value == "Hiya")
        }

        for (i in 3000..5999) {
            myMap.put(i, "Wheee woooooo haaaa" + i)
        }

        for (i in 6000..9999) {
            val value = myMap[i]
            Assert.assertTrue(value == null)
        }

        for (i in 3000..5999) {

            val value = myMap[i] as String
            Assert.assertTrue(value == "Wheee woooooo haaaa" + i)
        }

        println("Took " + (System.currentTimeMillis() - time))
        store.close()
    }

    @Test
    fun testPushMultipleObjects() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)

        val service = Executors.newFixedThreadPool(3)

        val t = Runnable { testPushObjects(store, 1) }

        val f1 = service.submit(t)

        val t2 = Runnable { testPushObjects(store, 2) }

        val f2 = service.submit(t2)

        val t3 = Runnable { testPushObjects(store, 3) }

        val f3 = service.submit(t3)

        try {
            f1.get()
            f2.get()
            f3.get()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        store.close()


    }

    @Test
    fun testPushSingleObjects() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)

        val service = Executors.newFixedThreadPool(3)

        val t = Runnable { testPushObjects(store, 1) }

        val f1 = service.submit(t)

        try {
            f1.get()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        store.close()


    }

    fun testPushObjects(store: DiskMapFactory, hashMapId: Int) {
        val myMap = store.getHashMap<MutableMap<String, EntityYo>>("objectos" + hashMapId)

        var time = System.currentTimeMillis()

        var entityYo: EntityYo? = null

        for (i in 0..99999) {
            entityYo = EntityYo()
            entityYo.id = "OOO, this is an id" + i
            entityYo.longValue = 23L
            entityYo.dateValue = Date(1433233222)
            entityYo.longStringValue = "This is a really long string key wooo, long string textThis is a really long string key wooo, long striring key wooo, long string textThis is a really long string key wooo, long striring key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string textThis is a really long string key wooo, long string text"
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

        println("Took " + (System.currentTimeMillis() - time))

        var another: EntityYo? = null

        time = System.currentTimeMillis()
        for (i in 0..99999) {
            another = myMap["OOO, this is an id" + i]
            Assert.assertTrue(entityYo!!.longValue == another!!.longValue)
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


        println("Took " + (System.currentTimeMillis() - time))

        time = System.currentTimeMillis()
        val it = myMap.entries.iterator()
        var i = 0
        while (it.hasNext()) {
            val entry = it.next() as Map.Entry<*, *>
            entry.value
            i++
        }
        println("Took " + (System.currentTimeMillis() - time) + " for this many " + i)

        println("Done with Jumbo Named")

    }

}
