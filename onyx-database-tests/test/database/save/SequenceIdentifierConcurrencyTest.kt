package database.save

import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import entities.InheritedLongAttributeEntity

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)

class SequenceIdentifierConcurrencyTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    companion object {
        private val threadPool = Executors.newFixedThreadPool(10)
    }

    /**
     * Tests Batch inserting 10,000 record with a String identifier
     * last test took: 759(mac)
     */
    @Test
    fun aConcurrencySequencePerformanceTest() {
        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<InheritedLongAttributeEntity>()

        val before = System.currentTimeMillis()

        for (i in 0..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            if (i % 500 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)
                })
            }

        }

        threads.forEach { it.get() }

        val after = System.currentTimeMillis()

        assertTrue(after - before < 1500, "Should not take more than 1.5 seconds to complete")
    }

    /**
     * Runs 10 threads that insert 10k entities with a String identifier.
     * After insertion, this test validates the data integrity.
     * last test took: 698(win) 2231(mac)
     */
    @Test
    fun concurrencySequenceSaveIntegrityTest() {

        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..5000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)
            entitiesToValidate.add(entity)

            if (i % 10 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                tmpList.forEach {
                    manager.saveEntity<IManagedEntity>(it)
                }
            }
        }

        threads.forEach { it.get() }

        // Validate entities to ensure it was persisted correctly
        entitiesToValidate.forEach {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            assertEquals(it.id, newEntity.id, "ID was not as expected ${it.id}")
            assertEquals(it.longPrimitive, newEntity.longPrimitive, "longPrimitive was not as expected ${it.longPrimitive}")
        }
    }

    @Test
    fun concurrencySequenceSaveIntegrityTestWithBatching() {

        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()

        for (i in 1..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.id = i.toLong()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)
            entitiesToValidate.add(entity)

            if (i % 1000 == 0) {
                val tmpList = ArrayList<InheritedLongAttributeEntity>(entities)
                entities.clear()
                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)
                })
            }
        }

        threads.forEach { it.get() }

        // Validate entities to ensure it was persisted correctly
        entitiesToValidate.forEach {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            assertEquals(it.id, newEntity.id, "ID was not as expected ${it.id}")
            assertEquals(it.longPrimitive, newEntity.longPrimitive, "longPrimitive was not as expected ${it.longPrimitive}")
        }
    }

    @Test
    fun concurrencySequenceDeleteIntegrityTest() {

        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            if (i % 2 == 0) {
                entitiesToValidateDeleted.add(entity)
            } else {
                entitiesToValidate.add(entity)
            }

            if (i % 10 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                threads.add(async(threadPool) {
                    tmpList.forEach {
                        manager.saveEntity<IManagedEntity>(it)
                    }
                })
            }
        }

        threads.forEach { it.get() }
        threads.clear()
        entities.clear()

        var deleteCount = 0

        for (i in 0..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)
            entitiesToValidate.add(entity)

            if (i % 10 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                val deletedIndex = deleteCount

                threads.add(async(threadPool) {
                    tmpList.forEach { manager.saveEntity<IManagedEntity>(it) }

                    var t = deletedIndex
                    while (t < deletedIndex + 5 && t < entitiesToValidateDeleted.size) {
                        manager.deleteEntity(entitiesToValidateDeleted[t])
                        t++
                    }
                })
                deleteCount += 5
            }
        }

        threads.forEach { it.get() }

        entitiesToValidate.forEach {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            newEntity = manager.find(newEntity)
            assertEquals(it.longPrimitive, newEntity.longPrimitive, "longPrimitive was not persisted correctly")
        }

        entitiesToValidateDeleted.forEach {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            var pass = false
            try {
                manager.find<IManagedEntity>(newEntity)
            } catch (e: NoResultsException) {
                pass = true
            }
            assertTrue(pass, "Entity ${newEntity.id} was not deleted")
        }
    }

    @Test
    fun concurrencySequenceDeleteBatchIntegrityTest() {

        val threads = ArrayList<Future<*>>()

        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()
        val ignore = HashMap<Long, InheritedLongAttributeEntity>()

        for (i in 0..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            if (i % 2 == 0) {
                entitiesToValidateDeleted.add(entity)
                ignore.put(entity.id, entity)
            } else {
                entitiesToValidate.add(entity)
            }

            if (i % 10 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)
                })
            }
        }

        threads.forEach { it.get() }
        threads.clear()
        entities.clear()

        for (i in 0..10000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)
            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()

                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)

                    for(k in 0 .. 6) {
                        var entityToDelete: IManagedEntity? = null
                        synchronized(entitiesToValidateDeleted) {
                            if (entitiesToValidateDeleted.size > 0) {
                                entityToDelete = entitiesToValidateDeleted.removeAt(0)
                            }
                        }

                        if(entityToDelete != null)
                            manager.deleteEntity(entityToDelete!!)
                    }
                })
            }

        }

        threads.forEach { it.get() }

        entitiesToValidate.forEach {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            if (!ignore.containsKey(newEntity.id)) {
                newEntity = manager.find(newEntity)
                assertEquals(it.longPrimitive, newEntity.longPrimitive, "Entity did not hydrate correctly")
            }
        }

        entitiesToValidateDeleted.forEach {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            var pass = false
            try {
                manager.find<IManagedEntity>(newEntity)
            } catch (e: NoResultsException) {
                pass = true
            }
            assertTrue(pass, "Entity ${newEntity.id} was not deleted")
        }
    }

    /**
     * Executes 10 threads that insert 30k entities with string id, then 10k are updated and 10k are deleted.
     * Then it validates the integrity of those actions
     */
    @Test
    fun concurrencySequenceAllIntegrityTest() {

        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateUpdated = ArrayList<InheritedLongAttributeEntity>()
        val ignore = HashMap<Long, InheritedLongAttributeEntity>()

        for (i in 0..30000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            // Delete Even ones
            when {
                i % 2 == 0 -> {
                    entitiesToValidateDeleted.add(entity)
                    ignore.put(entity.id, entity)
                }
                i % 3 == 0 && i % 2 != 0 -> entitiesToValidateUpdated.add(entity)
                else -> entitiesToValidate.add(entity)
            }

            if (i % 1000 == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)
                })
            }

        }

        threads.forEach { it.get() }
        threads.clear()
        entities.clear()

        entitiesToValidateDeleted -= entitiesToValidateUpdated
        entitiesToValidateUpdated -= entitiesToValidateDeleted

        val entitiesToBeDeleted = ArrayList<InheritedLongAttributeEntity>(entitiesToValidateDeleted)
        entitiesToValidateUpdated.forEach { it.longPrimitive = 45645 }

        var updateCount = 0

        for (i in 0..30000) {
            val entity = InheritedLongAttributeEntity()
            entity.longValue = 4L
            entity.longPrimitive = 3L
            entity.stringValue = "String key"
            entity.dateValue = Date(1483736263743L)
            entity.doublePrimitive = 342.23
            entity.doubleValue = 232.2
            entity.booleanPrimitive = true
            entity.booleanValue = false

            entities.add(entity)

            if (i % 20 == 0) {

                entitiesToValidate.add(entity)

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.clear()
                val updatedIndex = updateCount

                threads.add(async(threadPool) {
                    manager.saveEntities(tmpList)

                    var t = updatedIndex
                    while (t < updatedIndex + 13 && t < entitiesToValidateUpdated.size) {
                        manager.saveEntity<IManagedEntity>(entitiesToValidateUpdated[t])
                        t++
                    }

                    for (k in 0..31) {
                        var entityToDelete: InheritedLongAttributeEntity? = null
                        synchronized(entitiesToBeDeleted) {
                            if (!entitiesToBeDeleted.isEmpty())
                                entityToDelete = entitiesToBeDeleted.removeAt(0)
                        }

                        if (entityToDelete != null)
                            manager.deleteEntity(entityToDelete!!)
                    }
                })
                updateCount += 13
            }

        }

        entitiesToValidateDeleted -= entitiesToValidateUpdated

        threads.forEach { it.get() }

        var failedEntities = 0
        entitiesToValidate.forEach {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            if (!ignore.containsKey(newEntity.id)) {
                try {
                    manager.find<IManagedEntity>(newEntity)
                } catch (e: Exception) {
                    failedEntities++
                }
            }
        }

        assertEquals(0, failedEntities, "There were several entities that failed to be found")

        entitiesToValidateDeleted.forEach {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            var pass = false
            try {
                manager.find<IManagedEntity>(newEntity)
            } catch (e: NoResultsException) {
                pass = true
            }

            if (!pass) {
                failedEntities++
            }
        }

        assertEquals(0, failedEntities, "There were several entities that failed to be deleted")

        entitiesToValidateUpdated.forEach {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = it.id
            newEntity = manager.find(newEntity)
            assertEquals(45645L, newEntity.longPrimitive, "Entity failed to update")
        }
    }
}
