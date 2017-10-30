package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class SelectNotQueryTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    /**
     * This test simply checks the NOT_EQUAL operator against a full table scan
     */
    @Test
    fun testNotEqualsOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "Some test strin1")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<IManagedEntity>(query)

        assertEquals(4, results.size, "Not Equals Operator should render 4 records")
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    fun testNotEqualsInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Equals Operator with not modifier should render 4 records")
    }

    /**
     * This test simply checks the NOT_STARTS_WITH operator against a full table scan
     */
    @Test
    fun testNotStartsWithOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_STARTS_WITH, "Some")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Not Starts With Operator should render 2 records")
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    fun testNotStartsWithInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Starts With Operator with not modifier should render 2 records")
    }

    @Test
    fun testNotNullOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_NULL)
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Not Null Operator should render 4 records")
    }

    @Test
    fun testNotNullOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.IS_NULL).not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Is Null Operator with not modifier should render 4 records")
    }

    @Test
    fun testNotMatchesOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_MATCHES, "Some.*")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Not Matches Operator should render 2 records")
    }

    @Test
    fun testNotMatchesOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.MATCHES, "Some.*").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Matches Operator with not modifier should render 2 records")
    }

    @Test
    fun testNotLikeOperator() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "some test strin1")
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Not Like Operator should render 4 records")
    }

    @Test
    fun testNotLikeOperatorInverseCriteria() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "some test strin1").not()
        val query = Query(AllAttributeForFetch::class.java, criteria)
        val results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Like Operator with not modifier should render 4 records")
    }


    @Test
    fun testFullTableScanAndCriteriaWithInverse() {
        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2))
        var results: List<*> = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Expected 2 matching criteria")

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).not())
        results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Expected 4 matching criteria")
    }

    @Test
    fun testFullTableScanAndCriteriaWithInverseAndNonMatching() {
        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2))
        var results: List<*> = manager.executeQuery<Any>(query)

        assertEquals(2, results.size, "Expected 2 matching criteria")

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).not())
        results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Expected 4 matching criteria")
    }

    @Test
    fun testFullTableScanWithCompoundQueryInverse() {
        val criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
        val criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        val query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).or(orCriteria.not()))
        val results = manager.executeQuery<Any>(query)

        assertEquals(6, results.size, "Expected 6 compound inverse query results")

    }

    @Test
    fun testComplexQuery() {

        var criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        var criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "Some")
        var orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        var query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2).or(orCriteria))
        var results: List<*> = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Expected 4 results for complex query")

        criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some")
        orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2.not()).or(orCriteria))
        results = manager.executeQuery<Any>(query)

        assertEquals(4, results.size, "Expected 4 results for complex query")
    }

    @Test
    fun testComplexQueryMultipleLevel() {
        val criteria1 = QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
        val criteria2 = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some")
        val criteria3 = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 3)
        val criteria4 = QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 3)
        val orCriteria = QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s")

        val query = Query(AllAttributeForFetch::class.java, criteria1.and(criteria2.not()).or(orCriteria).and(criteria3.and(criteria4.not())))
        val results = manager.executeQuery<Any>(query)

        assertEquals(1, results.size, "Expected 1 complex query results")
    }
}