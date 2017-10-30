package database.base

import com.onyx.persistence.IManagedEntity
import entities.PerformanceEntity
import entities.PerformanceEntityChild
import org.junit.Before
import java.util.*
import kotlin.reflect.KClass

open class BulkPrePopulatedDatabaseTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {

        val entityList = ArrayList<IManagedEntity>()
        for (i in 0..99999) {
            val entity = PerformanceEntity()
            entity.stringValue = randomString
            entity.dateValue = Date()
            entity.idValue = (i + 1).toLong()

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
            entity.child?.someOtherField = randomString

            entityList.add(entity)
        }

        manager.saveEntities(entityList)
    }
}
