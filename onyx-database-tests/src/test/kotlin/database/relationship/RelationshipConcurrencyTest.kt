package database.relationship

import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.OneToOneParent
import entities.relationship.OneToOneChild
import entities.relationship.ManyToManyParent
import entities.relationship.ManyToManyChild
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.ArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class RelationshipConcurrencyTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    companion object {
        private val threadPool = Executors.newFixedThreadPool(10)
    }

    @Test
    fun testOneToOneCascadeConcurrency() {

        val threads = ArrayList<Future<*>>()

        val entities = ArrayList<OneToOneParent>()
        val entitiesToValidate = ArrayList<OneToOneParent>()

        for (i in 0..10000) {
            val entity = OneToOneParent()
            entity.identifier = randomString + i
            entity.correlation = 4
            entity.cascadeChild = OneToOneChild()
            entity.cascadeChild!!.identifier = randomString + i
            entity.child = OneToOneChild()
            entity.child!!.identifier = randomString
            entities.add(entity)

            entitiesToValidate.add(entity)

            if (i % 10 == 0) {
                val tmpList = ArrayList(entities)
                entities.removeAll(entities)
                threads += async(threadPool) {
                    for (entity1 in tmpList) {
                        manager.saveEntity<IManagedEntity>(entity1)
                    }
                }
            }
        }

        threads.forEach { it.get() }

        entitiesToValidate.parallelStream().forEach {
            val newEntity = OneToOneParent()
            newEntity.identifier = it.identifier
            manager.find<IManagedEntity>(newEntity)
            assertEquals(it.identifier, newEntity.identifier, "Entity identifier is invalid")
            assertNotNull(newEntity.cascadeChild, "Child cascade relationship should not be null")
            assertNotNull(newEntity.child, "Child relationship should not be null")
        }
    }

    @Test
    fun testManyToManyCascadeConcurrency() {
        val threads = ArrayList<Future<*>>()
        val entities = ArrayList<ManyToManyParent>()
        val entitiesToValidate = ArrayList<ManyToManyParent>()

        for (i in 0..10000) {
            val entity = ManyToManyParent()
            entity.identifier = randomString + i
            entity.correlation = 4
            entity.childCascade = ArrayList()

            val child = ManyToManyChild()
            child.identifier = randomString + i
            entity.childCascade!!.add(child)

            entities.add(entity)
            entitiesToValidate.add(entity)

            if (i % 100 == 0) {

                val tmpList = ArrayList(entities)
                entities.removeAll(entities)
                threads += async(threadPool) {
                    for (entity1 in tmpList) {
                        manager.saveEntity<IManagedEntity>(entity1)
                    }
                }
            }
        }

        threads.forEach { it.get() }

        entitiesToValidate.parallelStream().forEach {
            val newEntity = ManyToManyParent()
            newEntity.identifier = it.identifier
            manager.find<IManagedEntity>(newEntity)
            assertEquals(it.identifier, newEntity.identifier, "Entity identifier does not match")
            assertEquals(it.childCascade!![0].identifier, newEntity.childCascade!![0].identifier, "Relationship identifiers do not match")
        }
    }

    @Test
    fun testManyToManyCascadeConcurrencyMultiple() {
        val threads = ArrayList<Future<*>>()

        val entities = ArrayList<ManyToManyParent>()
        val entities2 = ArrayList<ManyToManyParent>()
        val entitiesToValidate = ArrayList<ManyToManyParent>()

        for (i in 0..10000) {
            val entity = ManyToManyParent()
            entity.identifier = randomString + i
            entity.correlation = 4
            entity.childCascadeSave = ArrayList()

            val child = ManyToManyChild()
            child.identifier = randomString + i
            entity.childCascadeSave!!.add(child)
            entities.add(entity)

            val entity2 = ManyToManyParent()
            entity2.identifier = entity.identifier
            entity2.correlation = 4
            entity2.childCascadeSave = ArrayList()
            val child2 = ManyToManyChild()
            child2.identifier = randomString + i

            entity2.childCascadeSave!!.add(child2)
            entity2.childCascadeSave!!.add(child)
            entities2.add(entity2)

            entitiesToValidate.add(entity)

            if (i % 100 == 0) {
                val tmpList = ArrayList(entities)
                entities.clear()
                val tmpList2 = ArrayList(entities2)
                entities2.clear()

                threads += async(threadPool) {
                    tmpList.forEach { entity1 -> manager.saveEntity<IManagedEntity>(entity1) }
                    tmpList2.forEach { entity1 -> manager.saveEntity<IManagedEntity>(entity1) }
                }
            }
        }

        threads.forEach { it.get() }

        var failures = 0

        entitiesToValidate.parallelStream().forEach {
            val newEntity = ManyToManyParent()
            newEntity.identifier = it.identifier
            manager.find<IManagedEntity>(newEntity)
            assertEquals(it.identifier, newEntity.identifier, "Entity does not have the correct identifier value")
            if (newEntity.childCascadeSave!!.size != 2) {
                failures++
            }
        }

        assertEquals(0, failures, "Entities did not have the correct amount of relationship items")
    }
}
