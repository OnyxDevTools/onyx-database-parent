package database.base

import com.onyx.extension.from
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeForFetch
import entities.AllAttributeForFetchChild
import entities.AllAttributeV2Entity
import org.junit.Before
import java.util.*
import kotlin.reflect.KClass

open class PrePopulatedDatabaseTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        manager.from(AllAttributeForFetch::class).delete()
        manager.from(AllAttributeV2Entity::class).delete()

        var entity = AllAttributeForFetch()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test string"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        entity.intValue = 3
        entity.intPrimitive = 3
        entity.mutableFloat = 34.3f
        entity.floatValue = 55.3f
        entity.mutableByte = 43.toByte()
        entity.byteValue = 99.toByte()
        entity.mutableShort = 828
        entity.shortValue = 882
        entity.mutableChar = 'A'
        entity.charValue = 'C'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDF"
        entity.operator = QueryCriteriaOperator.CONTAINS
        manager.saveEntity<IManagedEntity>(entity)

        entity.child = AllAttributeForFetchChild()
        entity.child!!.someOtherField = "HIYA"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE1"
        entity.stringValue = "Some test string1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 3
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE2"
        entity.stringValue = "Some test string1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test string2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        entity.intValue = 5
        entity.intPrimitive = 6
        entity.mutableFloat = 31.3f
        entity.floatValue = 51.3f
        entity.mutableByte = 13.toByte()
        entity.byteValue = 19.toByte()
        entity.mutableShort = 818
        entity.shortValue = 812
        entity.mutableChar = '9'
        entity.charValue = 'O'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASD_AAF"
        entity.operator = QueryCriteriaOperator.CONTAINS
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        entity.stringValue = "A"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        entity.stringValue = "A"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test string3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        entity.mutableFloat = 34.3f
        entity.floatValue = 55.3f
        entity.mutableByte = 43.toByte()
        entity.byteValue = 99.toByte()
        entity.mutableShort = 828
        entity.shortValue = 882
        entity.mutableChar = 'A'
        entity.charValue = 'C'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDF"
        entity.operator = QueryCriteriaOperator.CONTAINS
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity.child = AllAttributeForFetchChild()
        entity.child!!.someOtherField = "HIYA DOS"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
    }
}