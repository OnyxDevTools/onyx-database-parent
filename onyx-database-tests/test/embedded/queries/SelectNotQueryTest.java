package embedded.queries;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.PrePopulatedDatabaseTest;
import entities.AllAttributeForFetch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * Created by Tim Osborn on 3/17/17.
 * <p>
 * This test was created to support the new operators in V1.3.0
 */
@Category({EmbeddedDatabaseTests.class})
public class SelectNotQueryTest extends PrePopulatedDatabaseTest {

    /**
     * This test simply checks the NOT_EQUAL operator agains a full table scan
     */
    @Test
    public void testNotEqualsOperator() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "Some test strin1");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Not Equals Operator should render 7 records", results.size() == 7);
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    public void testNotEqualsInverseCriteria() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1").not();
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Equals Operator with not modifier should render 7 records", results.size() == 7);
    }

    /**
     * This test simply checks the NOT_STARTS_WITH operator agains a full table scan
     */
    @Test
    public void testNotStartsWithOperator() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Not Starts With Operator should render 4 records", results.size() == 4);
    }

    /**
     * This test does the same as the one above.  The only difference is that it uses the .not() syntax on the Query Criteria
     */
    @Test
    public void testNotStartsWithInverseCriteria() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some").not();
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Starts With Operator with not modifier should render 4 records", results.size() == 4);
    }

    @Test
    public void testNotNullOperator() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_NULL);
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Not Null Operator should render 7 records", results.size() == 7);
    }

    @Test
    public void testNotNullOperatorInverseCriteria() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.IS_NULL).not();
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 7);

        Assert.assertTrue("Is Null Operator with not modifier should render 7 records", results.size() == 7);
    }

    @Test
    public void testNotMatchesOperator() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_MATCHES, "Some.*");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Not Matches Operator should render 4 records", results.size() == 4);
    }

    @Test
    public void testNotMatchesOperatorInverseCriteria() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.MATCHES, "Some.*").not();
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Matches Operator with not modifier should render 4 records", results.size() == 4);
    }

    @Test
    public void testNotLikeOperator() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "some test strin1");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Not Like Operator should render 7 records", results.size() == 7);
    }

    @Test
    public void testNotLikeOperatorInverseCriteria() throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "some test strin1").not();
        Query query = new Query(AllAttributeForFetch.class, criteria);
        List results = manager.executeQuery(query);

        Assert.assertTrue("Like Operator with not modifier should render 7 records", results.size() == 7);
    }


    @Test
    public void testFullTableScanAndCriteriaWithInverse() throws OnyxException {
        QueryCriteria criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        QueryCriteria criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2));
        List results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 2);

        criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2).not());
        results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 7);
    }

    @Test
    public void testFullTableScanAndCriteriaWithInverseAndNonMatching() throws OnyxException {
        QueryCriteria criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        QueryCriteria criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2));
        List results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 2);

        criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2).not());
        results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 7);
    }

    @Test
    public void testFullTableScanWithCompundQueryInverse() throws OnyxException {

        QueryCriteria criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        QueryCriteria criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        QueryCriteria orCriteria = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s");

        Query query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2).or(orCriteria.not()));
        List results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 9);

    }

    @Test
    public void testComplexQuery() throws OnyxException {

        QueryCriteria criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        QueryCriteria criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_LIKE, "Some");
        QueryCriteria orCriteria = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s");

        Query query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2).or(orCriteria));
        List results = manager.executeQuery(query);

        assert results.size() == 5;

        criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some");
        orCriteria = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s");

        query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2.not()).or(orCriteria));
        results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 5);
    }

    @Test
    public void testComplexQueryMultipleLevel() throws OnyxException {
        QueryCriteria criteria1 = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        QueryCriteria criteria2 = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "Some");
        QueryCriteria criteria3 = new QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 3);
        QueryCriteria criteria4 = new QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 3);
        QueryCriteria orCriteria = new QueryCriteria("stringValue", QueryCriteriaOperator.LIKE, "s");

        Query query = new Query(AllAttributeForFetch.class, criteria1.and(criteria2.not()).or(orCriteria).and((criteria3.and(criteria4.not()))));
        List results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 2);
    }
}