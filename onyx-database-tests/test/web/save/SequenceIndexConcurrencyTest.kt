package web.save

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import entities.InheritedLongAttributeEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import web.base.BaseTest

import java.io.IOException
import java.security.SecureRandom
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(WebServerTests::class)
class SequenceIndexConcurrencyTest : BaseTest() {

    @Before
    @Throws(InitializationException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    /**
     * Tests Batch inserting 100,000 records with a Sequence identifier
     * This test is executed using 10 concurrent threads
     * last test took: 1670(win) 2200(mac)
     * @throws OnyxException
     * @throws InterruptedException
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun aConcurrencySequencePerformanceTest() {
        val random = SecureRandom()
        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(10)

        val entities = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..100000) {
            val entity = InheritedLongAttributeEntity()

            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "STring key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            if (i % 5000 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        manager.saveEntities(tmpList)
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }
                }
                threads.add(pool.submit(runnable))
            }

        }

        for (future in threads) {
            try {
                future.get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }

        }

        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds")

        Assert.assertTrue(after - time < 4000)

        pool.shutdownNow()
    }


}
