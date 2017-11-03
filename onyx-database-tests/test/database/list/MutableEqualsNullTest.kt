package database.list

import com.onyx.exception.OnyxException
import com.onyx.persistence.query.from
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Created by timothy.osborn on 11/6/14.
 */
@RunWith(Parameterized::class)
class MutableEqualsNullTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        manager.from(AllAttributeForFetch::class).delete()

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
        entity.intPrimitive = 4
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
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testStringEqualsNull() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIntEqualsNull() {
        val criteriaList = QueryCriteria("intValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testLongEqualsNull() {
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDateEqualsNull() {
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDoubleEqualsNull() {
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBooleanEqualsNull() {
        val criteriaList = QueryCriteria("booleanValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }
}