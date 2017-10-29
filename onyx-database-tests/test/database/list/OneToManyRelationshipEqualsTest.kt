package database.list

import com.onyx.extension.IN
import com.onyx.extension.cont
import com.onyx.extension.eq
import com.onyx.extension.startsWith
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.OneToManyChildFetchEntity
import entities.OneToOneFetchEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class OneToManyRelationshipEqualsTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

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

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity = OneToOneFetchEntity()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = OneToManyChildFetchEntity()
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

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE1"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToManyChildFetchEntity()
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

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE2"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToManyChildFetchEntity()
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

        entity2 = OneToManyChildFetchEntity()
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

        entity2 = OneToManyChildFetchEntity()
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

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE2"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2.parents = OneToOneFetchEntity()
        entity2.parents!!.id = "FIRST ONE3"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity2)

        entity2 = OneToManyChildFetchEntity()
        entity2.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity2)
    }

    @Test
    fun testOneToOneHasRelationshipMeetsOne() {
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, ("stringValue" eq "Some test strin3") and ("children.id" eq "FIRST ONE3"))
        assertEquals(1, results.size, "Expected 1 result")
    }

    @Test
    fun testOneToOneHasRelationship() {
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java,  ("stringValue" cont "Some test strin") and ("children.id" eq "FIRST ONE3"))
        assertEquals(1, results.size, "Expected 1 result")
    }

    @Test
    fun testOneToOneNoMeetCriteriaRelationship() {
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, ("stringValue" eq "Some te1st strin3") and ("children.id" eq "FIRST ONE3"))
        assertEquals(0, results.size, "Expected no results")
    }

    @Test
    fun testOneToManyInCriteriaRelationship() {
        val results = manager.list<OneToOneFetchEntity>(OneToOneFetchEntity::class.java, ("stringValue" startsWith  "Some test strin1") and ("children.id" IN arrayListOf("FIRST ONE3", "FIRST ONE2")))
        assertEquals(0, results.size, "Expected no results")
    }
}