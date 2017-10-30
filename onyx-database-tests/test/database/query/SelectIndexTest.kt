package database.query

import com.onyx.extension.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.SelectIdentifierTestEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class SelectIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        manager.from(SelectIdentifierTestEntity::class).delete()

        var entity = SelectIdentifierTestEntity()
        entity.id = 1L
        entity.index = 1
        entity.attribute = "1"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 2L
        entity.index = 2
        entity.attribute = "2"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 3L
        entity.index = 3
        entity.attribute = "3"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 4L
        entity.index = 4
        entity.attribute = "4"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 5L
        entity.index = 5
        entity.attribute = "5"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 6L
        entity.index = 6
        entity.attribute = "6"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 7L
        entity.index = 7
        entity.attribute = "7"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 8L
        entity.index = 8
        entity.attribute = "8"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 9L
        entity.index = 9
        entity.attribute = "9"

        manager.saveEntity<IManagedEntity>(entity)

        entity = SelectIdentifierTestEntity()
        entity.id = 10L
        entity.index = 10
        entity.attribute = "10"

        manager.saveEntity<IManagedEntity>(entity)

    }

    @Test
    fun testIdentifierAndCriteria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second)

        assertEquals(2, manager.executeQuery<Any>(query).size, "Expected 2 results")
    }

    @Test
    fun testIdentifierOrCriteria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second)

        assertEquals(6, manager.executeQuery<Any>(query).size, "Expected 6 results")
    }

    @Test
    fun testIdentifierCompoundCriteria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth)))

        assertEquals(6, manager.executeQuery<Any>(query).size, "Expected 6 results")
    }

    @Test
    fun testIdentifierAndCriteriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second.not())

        assertEquals(3, manager.executeQuery<Any>(query).size, "Expected 3 results")
    }

    @Test
    fun testIdentifierAndCriteriaWithNotGroup() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second).not()

        assertEquals(8, manager.executeQuery<Any>(query).size, "Expected 8 results")
    }

    @Test
    fun testIdentifierOrCriteriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.not())

        assertEquals(5, manager.executeQuery<Any>(query).size, "Expected 5 results")
    }

    @Test
    fun testIdentifierOrCriteriaWithNotGroup() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second).not()

        assertEquals(0, manager.executeQuery<Any>(query).size, "Expected no results")
    }

    @Test
    fun testIdentifierCompoundCriteriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth).not()))

        assertEquals(6, manager.executeQuery<Any>(query).size, "Expected 6 results")
    }

    @Test
    fun testIdentifierCompoundCriteriaWithNotFullScan() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth))).not()


        assertEquals(4, manager.executeQuery<Any>(query).size, "Expected 4 results")
    }

    @Test
    fun testMixMatchOfCriteria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth).not()))

        assertEquals(6, manager.executeQuery<Any>(query).size, "Expected 6 results")
    }

    @Test
    fun testMixMatchOfCriteriaIncludeFull() {
        val count = manager.from(SelectIdentifierTestEntity::class).where(
                              ("index" gt 5L)
                              .or( ("id" lt 3)
                                  .and((("index" eq 3) or !("id" eq 2)))
                                  .and(("attribute" eq 3) or ("attribute" eq 2))
                                 )
                          ).count()

        assertEquals(5, count, "Expected 5 results from compound query")
    }
}