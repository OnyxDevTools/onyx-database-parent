package remote.list;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemotePrePopulatedBaseTest;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;

import java.io.IOException;
import java.util.List;
import category.RemoteServerTests;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ RemoteServerTests.class })
public class ContainsTest extends RemotePrePopulatedBaseTest
{

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

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

