package embedded.queries

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Created by Tim Osborn on 3/17/17.
 *
 *
 * This test was created to support the new operators in V1.3.0
 */
@Category(EmbeddedDatabaseTests::class)
class SelectNotQueryTest : PrePopulatedDatabaseTest() {

    /**
     * This test simply checks the NOT_EQUAL operator agains a full table scan
     */
    @Test
    @Throws(OnyxException::class)
    fun testNotEqualsOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "Some test strin1")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Not Equals Operator should render 7 records", results.size == 7)
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    @Throws(OnyxException::class)
    fun testNotEqualsInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Equals Operator with not modifier should render 7 records", results.size == 7)
    }

    /**
     * This test simply checks the NOT_STARTS_WITH operator agains a full table scan
     */
    @Test
    @Throws(OnyxException::class)
    fun testNotStartsWithOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Not Starts With Operator should render 4 records", results.size == 4)
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    @Throws(OnyxException::class)
    fun testNotStartsWithInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Starts With Operator with not modifier should render 4 records", results.size == 4)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotNullOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_NULL)
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Not Null Operator should render 7 records", results.size == 7)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotNullOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.IS_NULL).not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 7)

        Assert.assertTrue("Is Null Operator with not modifier should render 7 records", results.size == 7)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotMatchesOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_MATCHES, "Some.*")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Not Matches Operator should render 4 records", results.size == 4)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotMatchesOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.MATCHES, "Some.*").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Matches Operator with not modifier should render 4 records", results.size == 4)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotLikeOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "some test strin1")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Not Like Operator should render 7 records", results.size == 7)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotLikeOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "some test strin1").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue("Like Operator with not modifier should render 7 records", results.size == 7)
    }


    @Test
    @Throws(OnyxException::class)
    fun testFullTableScanAndCriteriaWithInverse() {
        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2))
        var results: List<*> = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 2)

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).not())
        results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 7)
    }

    @Test
    @Throws(OnyxException::class)
    fun testFullTableScanAndCriteriaWithInverseAndNonMatching() {
        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2))
        var results: List<*> = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 2)

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).not())
        results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 7)
    }

    @Test
    @Throws(OnyxException::class)
    fun testFullTableScanWithCompundQueryInverse() {

        val criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        val criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        val query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).or(orCriteria.not()))
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 9)

    }

    @Test
    @Throws(OnyxException::class)
    fun testComplexQuery() {

        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "Some")
        var orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).or(orCriteria))
        var results: List<*> = manager.executeQuery<Any>(query)

        assert(results.size == 5)

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some")
        orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2.not()).or(orCriteria))
        results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 5)
    }

    @Test
    @Throws(OnyxException::class)
    fun testComplexQueryMultipleLevel() {
        val criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some")
        val criteria3 = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 3)
        val criteria4 = QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 3)
        val orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        val query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2.not()).or(orCriteria).and(criteria3.and(criteria4.not())))
        val results = manager.executeQuery<Any>(query)

        Assert.assertTrue(results.size == 2)
    }
}