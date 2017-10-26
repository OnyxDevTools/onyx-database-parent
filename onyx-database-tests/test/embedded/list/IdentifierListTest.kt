package embedded.list

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.BaseTest
import entities.AllAttributeForFetchSequenceGen
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Date

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Category(EmbeddedDatabaseTests::class)
class IdentifierListTest : BaseTest() {

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()

        var entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        entity.intValue = 3
        entity.intPrimitive = 3
        entity.id = 1L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        entity.id = 2L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        entity.id = 3L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test strin2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        entity.intValue = 5
        entity.intPrimitive = 6
        entity.id = 4L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test strin3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        entity.id = 5L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.id = 6L
        save(entity)
        find(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.id = 7L
        save(entity)
        find(entity)
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRange() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 2)
        val criteriaList2 = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 4)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(1, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRangeLTEqual() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 2)
        val criteriaList2 = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 4)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRangeEqual() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN_EQUAL, 2)
        val criteriaList2 = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 4)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(3, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierGreaterThan() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 5)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierLessThanNoResults() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 1)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(0, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierGreaterThanNoResults() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 7)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(0, results.size.toLong())
    }
}
