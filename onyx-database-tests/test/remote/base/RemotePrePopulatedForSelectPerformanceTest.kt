package remote.base

import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import entities.PerformanceEntity
import entities.PerformanceEntityChild
import org.junit.After
import org.junit.Before

import java.io.IOException
import java.util.ArrayList
import java.util.Date

/**
 * Created by timothy.osborn on 1/14/15.
 */
open class RemotePrePopulatedForSelectPerformanceTest : RemoteBaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun load100kRecords() {
        initialize()

        val entityList = ArrayList<IManagedEntity>()
        for (i in 0..99999) {
            val entity = PerformanceEntity()
            entity.stringValue = randomString
            entity.dateValue = Date()

            if (i % 2 == 0) {
                entity.booleanValue = true
                entity.booleanPrimitive = false
            } else {
                entity.booleanPrimitive = true
                entity.booleanValue = false
            }

            entity.intPrimitive = randomInteger
            entity.longPrimitive = randomInteger.toLong()
            entity.doublePrimitive = randomInteger * .001
            entity.longValue = java.lang.Long.valueOf(randomInteger.toLong())
            entity.longPrimitive = randomInteger.toLong()

            entity.child = PerformanceEntityChild()
            entity.child!!.someOtherField = randomString


            entityList.add(entity)
        }

        try {
            manager!!.saveEntities(entityList)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

    }
}
