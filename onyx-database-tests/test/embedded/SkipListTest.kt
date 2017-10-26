package embedded

import com.onyx.exception.InitializationException
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

import java.util.HashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Tim Osborn on 1/6/17.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SkipListTest {

    @Test
    @Throws(InitializationException::class)
    fun testInsert() {

        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("first")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }


        val time = System.currentTimeMillis()
        keyValues.forEach({ o, o2 -> assert(skipList[o] == o2) })
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

        val `val` = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assert(skipList[o] == o2)

            if (`val`.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
                deletedKeyValues.put(o, o2)
            } else {
                newKeyValues.put(o, o2)
            }
        })

        newKeyValues.forEach({ o, o2 -> assert(skipList[o] == o2) })

        deletedKeyValues.forEach({ o, o2 -> assert(skipList[o] == null) })

    }

    @Test
    fun testUpdate() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("third")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..9999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)

        }

        keyValues.forEach({ o, o2 -> skipList.put(o as Int, o2 as Int) })
    }

    @Test
    fun testForEach() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("third")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)

        }

        val `val` = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assert(skipList[o] == o2)

            if (`val`.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })


        val numberOfValues = AtomicInteger(0)
        skipList.forEach { integer, integer2 ->
            assert(integer != null)
            assert(integer2 != null)
            numberOfValues.addAndGet(1)
        }


        assert(numberOfValues.get() == skipList.size)
    }

    @Test
    fun testKeyIterator() {
        val builder = DefaultDiskMapFactory(TEST_DATABASE)
        val skipList = builder.getSkipListMap<MutableMap<Int, Int>>("fourth")

        val keyValues = HashMap<Int, Int>()
        for (i in 0..49999) {
            val randomNum = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)
            val randomValue = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)

            skipList.put(randomNum, randomValue)
            keyValues.put(randomNum, randomValue)
        }

        val `val` = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assert(skipList[o] == o2)

            if (`val`.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })


        val numberOfValues = AtomicInteger(0)

        val iterator = skipList.keys.iterator()
        while (iterator.hasNext()) {
            assert(iterator.next() is Int)
            numberOfValues.addAndGet(1)
        }


        assert(numberOfValues.get() == skipList.size)
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

        val `val` = AtomicInteger(0)
        keyValues.forEach({ o, o2 ->

            assert(skipList[o] == o2)

            if (`val`.addAndGet(1) % 1000 == 0) {
                skipList.remove(o)
            }
        })


        val numberOfValues = AtomicInteger(0)

        val iterator = skipList.values.iterator()
        while (iterator.hasNext()) {
            assert(iterator.next() is Int)
            numberOfValues.addAndGet(1)
        }


        assert(numberOfValues.get() == skipList.size)
    }

    companion object {

        val TEST_DATABASE = "C:/Sandbox/Onyx/Tests/skip.db"
    }
}
