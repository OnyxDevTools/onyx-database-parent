package memory.queries

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.SelectIdentifierTestEntity
import memory.base.BaseTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException

/**
 * Created by Tim Osborn on 3/22/17.
 */
@Category(InMemoryDatabaseTests::class)
class SelectIndexTest : BaseTest() {

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
        seedData()
    }

    @Throws(OnyxException::class)
    private fun seedData() {
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
    @Throws(OnyxException::class)
    fun testIdentifierAndCritieria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second)

        assert(manager.executeQuery<Any>(query).size == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierOrCritieria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second)

        assert(manager.executeQuery<Any>(query).size == 6)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierCompoundCritieria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth)))

        assert(manager.executeQuery<Any>(query).size == 6)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierAndCritieriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second.not())

        assert(manager.executeQuery<Any>(query).size == 3)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierAndCritieriaWithNotGroup() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second).not()

        assert(manager.executeQuery<Any>(query).size == 8)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierOrCritieriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.not())

        assert(manager.executeQuery<Any>(query).size == 5)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierOrCritieriaWithNotGroup() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L)
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second).not()

        assert(manager.executeQuery<Any>(query).size == 0)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierCompoundCritieriaWithNot() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        /*

          where index > 5
                or (
                    index < 3  && !(index = 3 | index = 2)
                   )

         */
        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth).not()))

        assert(manager.executeQuery<Any>(query).size == 6)
    }

    @Test
    @Throws(OnyxException::class)
    fun testIdentifierCompoundCritieriaWithNotFullScan() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth))).not()


        assert(manager.executeQuery<Any>(query).size == 4)
    }

    @Test
    @Throws(OnyxException::class)
    fun testMixMatchOfCritieria() {
        val first = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val second = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 3L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.or(second.and(third.or(fourth).not()))

        /*
         index > 5  ||
         (id < 3  && !(index = 3 || id 2))
         */
        assert(manager.executeQuery<Any>(query).size == 6)
    }

    @Test
    @Throws(OnyxException::class)
    fun testMixMatchOfCritieria2() {
        val first = QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 5L)
        val second = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 0L)
        val third = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val fourth = QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L)

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria = first.and(second.and(third.or(fourth).not()))

        /* select * from da
            where
              index < 5
              and id > 0
              and !(index == 2 or index == 3)
              */

        assert(manager.executeQuery<Any>(query).size == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun testMixMatchOfCritieriaIncludeFull() {
        val indexGreaterThan5 = QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L)
        val idLessThan3 = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 3L)
        val indexEqualTo3 = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L)
        val idEqualTo2 = QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L)
        val attributeEq3 = QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "3")
        val attributeEq2 = QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "2")

        val query = Query()
        query.entityType = SelectIdentifierTestEntity::class.java
        query.criteria =
                // Index Gt 5 6,7,8,9,10
                indexGreaterThan5.or(idLessThan3 // Index Gt 5 (6,7,8,9,10) or id less than 3 (1,2)
                        .and(
                                indexEqualTo3.or(idEqualTo2).not() // index eq 3 (3) or id equal 2 (2) // NOT!
                        ) //  Index Gt 5 (6,7,8,9,10) or id less than 3 (1,!2) = (6,7,8,9,10,1)
                        .and(
                                attributeEq3.or(attributeEq2)
                        )
                )

        assert(manager.executeQuery<Any>(query).size == 5)
    }
}
