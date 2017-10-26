package embedded.save

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import embedded.base.BaseTest
import entities.InheritedLongAttributeEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.io.IOException
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.*

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
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

    @get:Synchronized private var z = 0

    @Synchronized private fun increment() {
        z++
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
        val pool = Executors.newFixedThreadPool(10)
        val entities = ArrayList<InheritedLongAttributeEntity>()

        val time = System.currentTimeMillis()

        val recordsToInsert = 100000
        val batch = 5000
        val recordsToGet = CountDownLatch(recordsToInsert / batch + 1)
        for (i in 0..recordsToInsert) {
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

            if (i % batch == 0) {
                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val runnable = {
                    try {
                        manager.saveEntities(tmpList)
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }

                    recordsToGet.countDown()
                }
                pool.execute(runnable)
            }

        }

        recordsToGet.await()

        val after = System.currentTimeMillis()
        println("Took " + (after - time) + " milliseconds")
        Assert.assertTrue(after - time < 3500)

        pool.shutdownNow()
    }

    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun concurrencySequenceSaveIntegrityTest() {
        val random = SecureRandom()
        val entity2 = InheritedLongAttributeEntity()

        entity2.longValue = 4L
        entity2.longPrimitive = 3L
        entity2.stringValue = "STring key"
        entity2.dateValue = Date(1483736263743L)
        entity2.doublePrimitive = 342.23
        entity2.doubleValue = 232.2
        entity2.booleanPrimitive = true
        entity2.booleanValue = false

        manager.saveEntity<IManagedEntity>(entity2)

        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(10)

        val entities = ArrayList<InheritedLongAttributeEntity>()

        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..10000) {
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

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
                        //manager.saveEntities(tmpList);
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

        pool.shutdownNow()

        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds")

        for (entity in entitiesToValidate) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            Assert.assertTrue(newEntity.id == entity.id)
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive)
        }
    }

    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun concurrencySequenceSaveIntegrityTestWithBatching() {
        val random = SecureRandom()
        val entity2 = InheritedLongAttributeEntity()

        entity2.longValue = 4L
        entity2.longPrimitive = 3L
        entity2.stringValue = "STring key"
        entity2.dateValue = Date(1483736263743L)
        entity2.doublePrimitive = 342.23
        entity2.doubleValue = 232.2
        entity2.booleanPrimitive = true
        entity2.booleanValue = false

        manager.saveEntity<IManagedEntity>(entity2)

        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(15)

        val entities = ArrayList<InheritedLongAttributeEntity>()

        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..10000) {
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

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

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

        pool.shutdownNow()

        val i = 0
        for (entity in entitiesToValidate) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            Assert.assertTrue(newEntity.id == entity.id)
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive)
        }
    }

    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun concurrencySequenceDeleteIntegrityTest() {
        val random = SecureRandom()
        val entity2 = InheritedLongAttributeEntity()

        entity2.longValue = 4L
        entity2.longPrimitive = 3L
        entity2.stringValue = "STring key"
        entity2.dateValue = Date(1483736263743L)
        entity2.doublePrimitive = 342.23
        entity2.doubleValue = 232.2
        entity2.booleanPrimitive = true
        entity2.booleanValue = false

        manager.saveEntity<IManagedEntity>(entity2)

        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(10)

        val entities = ArrayList<InheritedLongAttributeEntity>()

        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()

        for (i in 0..10000) {
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

            if (i % 2 == 0) {
                entitiesToValidateDeleted.add(entity)
            } else {
                entitiesToValidate.add(entity)
            }
            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
                        //manager.saveEntities(tmpList);
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
        threads.removeAll(threads)

        var deleteCount = 0

        for (i in 0..10000) {
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

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val indx = i
                val delIdx = deleteCount

                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }

                        var t = delIdx
                        while (t < delIdx + 5 && t < entitiesToValidateDeleted.size) {
                            manager.deleteEntity(entitiesToValidateDeleted[t])
                            t++
                        }
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }
                }
                deleteCount += 5
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


        pool.shutdownNow()

        val after = System.currentTimeMillis()

        for (entity in entitiesToValidate) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            Assert.assertTrue(newEntity.id == entity.id)
            Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive)
        }

        for (entity in entitiesToValidateDeleted) {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            var pass = false
            try {
                manager.find<IManagedEntity>(newEntity)
            } catch (e: NoResultsException) {
                pass = true
            }

            Assert.assertTrue(pass)
        }
    }

    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun concurrencySequenceDeleteBatchIntegrityTest() {
        val entity2 = InheritedLongAttributeEntity()

        entity2.longValue = 4L
        entity2.longPrimitive = 3L
        entity2.stringValue = "STring key"
        entity2.dateValue = Date(1483736263743L)
        entity2.doublePrimitive = 342.23
        entity2.doubleValue = 232.2
        entity2.booleanPrimitive = true
        entity2.booleanValue = false

        manager.saveEntity<IManagedEntity>(entity2)

        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(10)

        val entities = ArrayList<InheritedLongAttributeEntity>()

        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()

        val ignore = HashMap<Long, InheritedLongAttributeEntity>()

        for (i in 0..10000) {
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

            if (i % 2 == 0) {
                entitiesToValidateDeleted.add(entity)
                ignore.put(entity.id, entity)
            } else {
                entitiesToValidate.add(entity)
            }
            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val runnable = {
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
        threads.removeAll(threads)
        entities.removeAll(entities)

        var deleteCount = 0

        for (i in 0..10000) {
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

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val indx = i
                val delIdx = deleteCount

                val runnable = Runnable {
                    try {
                        manager.saveEntities(tmpList)

                        var t = delIdx
                        while (t < delIdx + 5 && t < entitiesToValidateDeleted.size) {
                            manager.deleteEntity(entitiesToValidateDeleted[t])
                            t++
                        }
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }
                }
                deleteCount += 5
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


        pool.shutdownNow()

        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds")

        for (entity in entitiesToValidate) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            if (!ignore.containsKey(newEntity.id)) {
                newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
                Assert.assertTrue(newEntity.id == entity.id)
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive)
            }
        }

        var p = 0

        for (entity in entitiesToValidateDeleted) {
            val newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            var pass = false
            try {
                manager.find<IManagedEntity>(newEntity)
            } catch (e: NoResultsException) {
                pass = true
            }

            Assert.assertTrue(pass)
            p++
        }
    }

    /**
     * Executes 10 threads that insert 30k entities with sequence id, then 10k are updated and 10k are deleted.
     * Then it validates the integrity of those actions
     * last test took: 1661(win) 2100(mac)
     * @throws OnyxException
     * @throws InterruptedException
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun concurrencySequenceAllIntegrityTest() {
        val random = SecureRandom()

        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(10)

        val entities = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidate = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateDeleted = ArrayList<InheritedLongAttributeEntity>()
        val entitiesToValidateUpdated = ArrayList<InheritedLongAttributeEntity>()

        val ignore = HashMap<Long, InheritedLongAttributeEntity>()

        /**
         * Save A whole bunch of records and keep track of some to update and delete
         */
        for (i in 0..30000) {
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

            // Delete Even ones
            if (i % 2 == 0) {
                entitiesToValidateDeleted.add(entity)
                ignore.put(entity.id, entity)
            } else if (i % 3 == 0 && i % 2 != 0) {
                entitiesToValidateUpdated.add(entity)
            } else {
                entitiesToValidate.add(entity)
            }// Update every third one
            if (i % 1000 == 0) {

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

        // Make Sure we Are done
        for (future in threads) {
            try {
                future.get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }

        }

        // Update an attribute
        for (entity in entitiesToValidateUpdated) {
            entity.longPrimitive = 45645
        }

        threads.removeAll(threads)
        entities.removeAll(entities)

        var deleteCount = 0
        var updateCount = 0

        for (i in 0..30000) {
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


            if (i % 20 == 0) {

                entitiesToValidate.add(entity)

                val tmpList = ArrayList<IManagedEntity>(entities)
                entities.removeAll(entities)
                val indx = i
                val delIdx = deleteCount
                val updtIdx = updateCount

                val runnable = object : Runnable {
                    override fun run() {
                        try {
                            manager.saveEntities(tmpList)

                            run {
                                var t = updtIdx
                                while (t < updtIdx + 13 && t < entitiesToValidateUpdated.size) {
                                    manager.saveEntity<IManagedEntity>(entitiesToValidateUpdated[t])
                                    t++
                                }
                            }

                            var t = delIdx
                            while (t < delIdx + 30 && t < entitiesToValidateDeleted.size) {
                                manager.deleteEntity(entitiesToValidateDeleted[t])
                                t++
                            }
                        } catch (e: OnyxException) {
                            e.printStackTrace()
                        }

                    }
                }
                deleteCount += 30
                updateCount += 13
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


        pool.shutdownNow()

        val after = System.currentTimeMillis()

        println("Took " + (after - time) + " milliseconds")

        for (entity in entitiesToValidate) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            if (!ignore.containsKey(newEntity.id)) {
                newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
                Assert.assertTrue(newEntity.id == entity.id)
                Assert.assertTrue(newEntity.longPrimitive == entity.longPrimitive)
            }
        }

        var i = 0
        for (entity in entitiesToValidateDeleted) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            var pass = false
            try {
                newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            } catch (e: NoResultsException) {
                pass = true
            }

            if (!pass) {
                i++
            }
        }

        Assert.assertSame(0, i)

        for (entity in entitiesToValidateUpdated) {
            var newEntity = InheritedLongAttributeEntity()
            newEntity.id = entity.id
            newEntity = manager.find<IManagedEntity>(newEntity) as InheritedLongAttributeEntity
            Assert.assertTrue(newEntity.longPrimitive == 45645L)
        }
    }

}
