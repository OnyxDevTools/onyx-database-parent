package embedded.list

/**
 * Created by Tim Osborn on 3/14/17.
 */

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

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Category(EmbeddedDatabaseTests::class)
class IndexListTest : BaseTest() {

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()

        var entity: AllAttributeForFetchSequenceGen

        for (i in 1..5000) {
            entity = AllAttributeForFetchSequenceGen()
            entity.id = i.toLong()
            entity.indexVal = i
            save(entity)
        }

    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRange() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 2500)
        val criteriaList2 = QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN, 3000)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(499, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRangeLTEqual() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 2500)
        val criteriaList2 = QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN_EQUAL, 3000)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(500, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierRangeEqual() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN_EQUAL, 1)
        val criteriaList2 = QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList.and(criteriaList2))
        Assert.assertEquals(5000, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierGreaterThan() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 4000)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(1000, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierLessThanNoResults() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN, 1)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(0, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIdentifierGreaterThanNoResults() {
        val criteriaList = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 5000)
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, criteriaList)
        Assert.assertEquals(0, results.size.toLong())
    }
}
