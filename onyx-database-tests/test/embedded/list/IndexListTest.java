package embedded.list;

/**
 * Created by tosborn1 on 3/14/17.
 */

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.AllAttributeForFetchSequenceGen;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.List;

/**
 * Created by tosborn1 on 3/13/17.
 */
@SuppressWarnings("unchecked")
@Category({EmbeddedDatabaseTests.class})
public class IndexListTest extends BaseTest {

    @After
    public void after() throws IOException {
        shutdown();
    }

    @Before
    public void seedData() throws InitializationException {
        initialize();

        AllAttributeForFetchSequenceGen entity;

        for (int i = 1; i <= 5000; i++) {
            entity = new AllAttributeForFetchSequenceGen();
            entity.id = (long) i;
            entity.indexVal = i;
            save(entity);
        }

    }

    @Test
    public void testIdentifierRange() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 2500);
        QueryCriteria criteriaList2 = new QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN, 3000);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(499, results.size());
    }

    @Test
    public void testIdentifierRangeLTEqual() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 2500);
        QueryCriteria criteriaList2 = new QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN_EQUAL, 3000);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(500, results.size());
    }

    @Test
    public void testIdentifierRangeEqual() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN_EQUAL, 1);
        QueryCriteria criteriaList2 = new QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(5000, results.size());
    }

    @Test
    public void testIdentifierGreaterThan() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 4000);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList);
        Assert.assertEquals(1000, results.size());
    }

    @Test
    public void testIdentifierLessThanNoResults() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.LESS_THAN, 1);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList);
        Assert.assertEquals(0, results.size());
    }

    @Test
    public void testIdentifierGreaterThanNoResults() throws EntityException, InstantiationException, IllegalAccessException {
        QueryCriteria criteriaList = new QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 5000);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList);
        Assert.assertEquals(0, results.size());
    }
}
