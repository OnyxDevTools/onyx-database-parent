package diskmap

import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import org.junit.Test

import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Created by timothy.osborn on 3/27/15.
 */
class MultiThreadTest : AbstractTest() {

    internal var pool = Executors.newFixedThreadPool(9)
    @Test
    fun testMultiThread() {
        val store = DefaultDiskMapFactory(AbstractTest.TEST_DATABASE)
        val myMap = store.getHashMap<MutableMap<Int, String>>("second")

        val time = System.currentTimeMillis()
        val items = ArrayList<Future<*>>()
        for (i in 1..19) {
            val p = randomGenerator.nextInt(19 - 1 + 1) + 1

            if (i % 5 == 0) {
                val scanThread = Runnable {
                    val it = myMap.entries.iterator()

                    while (it.hasNext()) {
                        val entry = it.next() as Map.Entry<Int, String>
                        entry.key
                    }
                }

                items.add(pool.submit(scanThread))
            }
            val runnable = MyRunnable(myMap, p)
            items.add(pool.submit(runnable))
        }

        items.stream().forEach { future ->
            try {
                future.get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        }

        println("Done in " + (time - System.currentTimeMillis()))
        store.close()
    }

    internal inner class MyRunnable(myMap: MutableMap<Int, String>, var p: Int) : Runnable {
        protected var myMap: MutableMap<Int, String>? = null

        init {
            this.myMap = myMap

        }

        override fun run() {
            val it = p
            for (k in 1..4999) {
                val value:Int = (k * p).toInt()
                myMap!!.put(value, "HIYA" + value)
                var strVal = myMap!![value] as String
                myMap!!.remove(value)
                strVal = myMap!![value] as String
            }
        }
    }

    companion object {

        internal var randomGenerator = Random()
    }

}
