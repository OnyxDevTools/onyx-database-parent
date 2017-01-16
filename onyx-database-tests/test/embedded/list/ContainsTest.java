package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.PrePopulatedDatabaseTest;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ EmbeddedDatabaseTests.class })
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

