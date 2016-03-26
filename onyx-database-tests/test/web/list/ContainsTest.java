package web.list;

import category.WebServerTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import web.base.PrePopulatedDatabaseTest;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;

import java.util.List;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ WebServerTests.class })
public class ContainsTest extends PrePopulatedDatabaseTest
{

    @Test
    public void testStringContains() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.CONTAINS, "Some test strin");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(4, results.size());
    }

    @Test
    public void testContainsStringId() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id",QueryCriteriaOperator.STARTS_WITH, "FIRST ONE");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(6, results.size());
    }

    @Test
    public void testStringStartsWith() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.CONTAINS, "ome test strin");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(4, results.size());
    }

    @Test
    public void testContainsStartsWith() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id",QueryCriteriaOperator.CONTAINS, "IRST ONE");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(6, results.size());
    }
}

