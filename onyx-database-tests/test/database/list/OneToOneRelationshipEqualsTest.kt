package database.list

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.OneToOneChildFetchEntity
import entities.OneToOneFetchEntity
import entities.relationship.OneToManyChild
import entities.relationship.OneToManyParent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(Parameterized::class)
class OneToOneRelationshipEqualsTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        var entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE1"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE2"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)


        var entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE"
        entity2.stringValue = "Some test strin"
        entity2.dateValue = Date(1000)
        entity2.doublePrimitive = 3.3
        entity2.doubleValue = 1.1
        entity2.booleanValue = false
        entity2.booleanPrimitive = true
        entity2.longPrimitive = 1000L
        entity2.longValue = 323L
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2.parent = OneToOneFetchEntity()
        entity2.parent!!.id = "FIRST ONE1"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE1"
        entity2.stringValue = "Some test strin1"
        entity2.dateValue = Date(1001)
        entity2.doublePrimitive = 3.31
        entity2.doubleValue = 1.11
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1002L
        entity2.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2.parent = OneToOneFetchEntity()
        entity2.parent!!.id = "FIRST ONE2"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE2"
        entity2.stringValue = "Some test strin1"
        entity2.dateValue = Date(1001)
        entity2.doublePrimitive = 3.31
        entity2.doubleValue = 1.11
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1002L
        entity2.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE3"
        entity2.stringValue = "Some test strin2"
        entity2.dateValue = Date(1002)
        entity2.doublePrimitive = 3.32
        entity2.doubleValue = 1.12
        entity2.booleanValue = true
        entity2.booleanPrimitive = false
        entity2.longPrimitive = 1001L
        entity2.longValue = 321L
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE3"
        entity2.stringValue = "Some test strin3"
        entity2.dateValue = Date(1022)
        entity2.doublePrimitive = 3.35
        entity2.doubleValue = 1.126
        entity2.booleanValue = false
        entity2.booleanPrimitive = true
        entity2.longPrimitive = 1301L
        entity2.longValue = 322L
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2.parent = OneToOneFetchEntity()
        entity2.parent!!.id = "FIRST ONE3"
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        manager.find<IManagedEntity>(entity2.parent!!)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

        entity2 = OneToOneChildFetchEntity()
        entity2.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity2)

    }

    @Test
    fun testOneToOneHasRelationship() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
                .and("child.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)
        assertEquals(1, results.size, "Expecting results")
    }

    @Test
    fun testOneToOneNoMeetCriteriaRelationship() {

        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some te1st strin3")
                .and("child.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, criteria)
        assertEquals(0, results.size, "Expecting no results")
    }

    @Test
    fun ftestOneToManyRemoveHasMultiple() {
        val parent = OneToManyParent()
        parent.identifier = "ZZ"
        parent.correlation = 30
        manager.saveEntity<IManagedEntity>(parent)

        var child = OneToManyChild()
        child.identifier = "YY"
        child.correlation = 31

        var child2 = OneToManyChild()
        child2.identifier = "XX"
        child2.correlation = 32

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        parent.childNoInverseCascade!!.add(child2)

        manager.saveEntity<IManagedEntity>(parent)

        child = OneToManyChild()
        child.identifier = "YY"
        manager.find<IManagedEntity>(child)

        child2 = OneToManyChild()
        child2.identifier = "XX"
        manager.find<IManagedEntity>(child2)

        // Validate the child still exists
        assertEquals(32, child2.correlation, "Expecting correlation to be updated to 32")

        val parent1 = OneToManyParent()
        parent1.identifier = "ZZ"
        manager.find<IManagedEntity>(parent1)

        // Verify the relationship is still there
        assertEquals(30, parent1.correlation, "Expecting correlation to still be there")
        assertNotNull(parent1.childNoInverseCascade, "Expecting relationship to still exist")
        assertEquals(2, parent1.childNoInverseCascade!!.size, "Expecting 2 relationship entities")

        manager.initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade?.removeAll(parent1.childNoInverseCascade!!)
        manager.saveEntity<IManagedEntity>(parent1)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "ZZ"
        manager.find<IManagedEntity>(parent2)

        // Ensure the relationship was not removed
        assertEquals(0, parent2.childNoInverseCascade!!.size, "Expecting relationships to have been deleted")
        assertEquals(30, parent2.correlation, "Expecting correlation to be modified")
    }
}