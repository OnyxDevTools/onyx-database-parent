package memory.relationship

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import entities.relationship.ManyToManyChild
import entities.relationship.ManyToManyParent
import entities.relationship.OneToOneChild
import entities.relationship.OneToOneParent
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.util.ArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(InMemoryDatabaseTests::class)
class RelationshipConcurrencyTest : memory.base.BaseTest() {

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class)
    fun testOneToOneCascadeConcurrency() {
        val random = SecureRandom()
        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(20)

        val entities = ArrayList<OneToOneParent>()

        val entitiesToValidate = ArrayList<OneToOneParent>()

        for (i in 0..10000) {
            val entity = OneToOneParent()
            entity.identifier = BigInteger(130, random).toString(32)
            entity.correlation = 4
            entity.cascadeChild = OneToOneChild()
            entity.cascadeChild!!.identifier = BigInteger(130, random).toString(32)
            entity.child = OneToOneChild()
            entity.child!!.identifier = BigInteger(130, random).toString(32)
            entities.add(entity)

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {

                val tmpList = ArrayList(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
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

        pool.shutdownNow()

        println("Took " + (after - time) + " milliseconds")

        for (entity in entitiesToValidate) {
            val newEntity = OneToOneParent()
            newEntity.identifier = entity.identifier
            manager.find<IManagedEntity>(newEntity)
            Assert.assertTrue(newEntity.identifier == entity.identifier)
            Assert.assertTrue(newEntity.cascadeChild != null)
            Assert.assertTrue(newEntity.child != null)
        }
    }


    @Test
    @Throws(OnyxException::class)
    fun testManyToManyCascadeConcurrency() {
        val random = SecureRandom()
        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(20)

        val entities = ArrayList<ManyToManyParent>()

        val entitiesToValidate = ArrayList<ManyToManyParent>()

        for (i in 0..10000) {
            val entity = ManyToManyParent()
            entity.identifier = BigInteger(130, random).toString(32)
            entity.correlation = 4
            entity.childCascade = ArrayList()
            val child = ManyToManyChild()
            child.identifier = BigInteger(130, random).toString(32)

            entity.childCascade!!.add(child)

            entities.add(entity)

            entitiesToValidate.add(entity)

            if (i % 100 == 0) {

                val tmpList = ArrayList(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
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

        pool.shutdownNow()

        println("Took " + (after - time) + " milliseconds")

        for (entity in entitiesToValidate) {
            val newEntity = ManyToManyParent()
            newEntity.identifier = entity.identifier
            manager.find<IManagedEntity>(newEntity)
            Assert.assertTrue(newEntity.identifier == entity.identifier)
            Assert.assertTrue(newEntity.childCascade!![0] != null)
            Assert.assertTrue(newEntity.childCascade!![0].identifier == entity.childCascade!![0].identifier)
        }
    }

    @Test
    @Throws(OnyxException::class)
    fun testManyToManyCascadeConcurrencyMultiple() {
        val random = SecureRandom()
        val time = System.currentTimeMillis()

        val threads = ArrayList<Future<*>>()

        val pool = Executors.newFixedThreadPool(20)

        val entities = ArrayList<ManyToManyParent>()
        val entities2 = ArrayList<ManyToManyParent>()

        val entitiesToValidate = ArrayList<ManyToManyParent>()

        for (i in 0..10000) {
            val entity = ManyToManyParent()
            entity.identifier = BigInteger(130, random).toString(32)
            entity.correlation = 4
            entity.childCascadeSave = ArrayList()
            val child = ManyToManyChild()
            child.identifier = BigInteger(130, random).toString(32)
            entity.childCascadeSave!!.add(child)
            entities.add(entity)

            val entity2 = ManyToManyParent()
            entity2.identifier = entity.identifier
            entity2.correlation = 4
            entity2.childCascadeSave = ArrayList()
            val child2 = ManyToManyChild()
            child2.identifier = BigInteger(130, random).toString(32)

            entity2.childCascadeSave!!.add(child2)
            entity2.childCascadeSave!!.add(child)
            entities2.add(entity2)

            entitiesToValidate.add(entity)

            if (i % 100 == 0) {

                val tmpList = ArrayList(entities)
                entities.removeAll(entities)
                val runnable = Runnable {
                    try {
                        for (entity1 in tmpList) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }
                }

                val tmpList2 = ArrayList(entities2)
                entities2.removeAll(entities2)
                val runnable2 = Runnable {
                    try {
                        for (entity1 in tmpList2) {
                            manager.saveEntity<IManagedEntity>(entity1)
                        }
                    } catch (e: OnyxException) {
                        e.printStackTrace()
                    }
                }

                threads.add(pool.submit(runnable))
                threads.add(pool.submit(runnable2))
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

        pool.shutdownNow()

        println("Took " + (after - time) + " milliseconds")

        var failures = 0

        for (entity in entitiesToValidate) {
            val newEntity = ManyToManyParent()
            newEntity.identifier = entity.identifier
            manager.find<IManagedEntity>(newEntity)
            Assert.assertTrue(newEntity.identifier == entity.identifier)
            if (newEntity.childCascadeSave!!.size != 2) {
                failures++
            }
        }

        Assert.assertTrue(failures == 0)

    }

}
