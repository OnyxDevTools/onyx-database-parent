package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import database.base.DatabaseBaseTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import java.util.HashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Created by Tim Osborn on 1/6/17.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SkipListTest {

    @Test
    fun testInsert() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("first")
        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = DatabaseBaseTest.randomInteger
            val randomValue = DatabaseBaseTest.randomInteger

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        keyValues.forEach({ o, o2 -> assertEquals(skipList[o], o2) })
    }

    @Test
    fun testDelete() {

        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("second")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)

        }
        val newKeyValues = HashMap<Int, Int>()
        val deletedKeyValues = HashMap<Int, Int>()

        val value = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assertEquals(skipList[o], o2)

            if (value.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
                deletedKeyValues.put(o, o2)
            } else {
                newKeyValues.put(o, o2)
            }
        })

        newKeyValues.forEach({ o, o2 -> assertEquals(skipList[o], o2) })
        deletedKeyValues.forEach({ o, _ -> assertNull(skipList[o]) })
    }

    @Test
    fun testUpdate() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("third")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..9999) {
            val randomNum = DatabaseBaseTest.randomInteger
            val randomValue = DatabaseBaseTest.randomInteger

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        keyValues.forEach({ o, o2 -> skipList.put(o, o2) })
    }

    @Test
    fun testForEach() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("third")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = DatabaseBaseTest.randomInteger
            val randomValue = DatabaseBaseTest.randomInteger

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        val value = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assertEquals(skipList[o], o2)

            if (value.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })

        val numberOfValues = AtomicInteger(0)
        skipList.forEach { integer, integer2 ->
            assertNotNull(integer)
            assertNotNull(integer2)
            numberOfValues.addAndGet(1)
        }

        assertEquals(numberOfValues.get(), skipList.size)
    }

    @Test
    fun testKeyIterator() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("fourth")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = DatabaseBaseTest.randomInteger
            val randomValue = DatabaseBaseTest.randomInteger

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        val values = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->
            assertEquals(skipList[o], o2)

            if (values.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })

        val numberOfValues = AtomicInteger(0)

        val iterator = skipList.keys.iterator()
        while (iterator.hasNext()) {
            assertNotNull(iterator.next())
            numberOfValues.addAndGet(1)
        }

        assertEquals(numberOfValues.get(), skipList.size)
    }

    @Test
    fun testValueIterator() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("fifth")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        val value = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->
            assertEquals(skipList[o], o2)

            if (value.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })

        val numberOfValues = AtomicInteger(0)

        val iterator = skipList.values.iterator()
        while (iterator.hasNext()) {
            assertNotNull(iterator.next())
            numberOfValues.addAndGet(1)
        }

        assertEquals(numberOfValues.get(), skipList.size)
    }

    companion object {
        val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/skipTest.db"
    }
}
