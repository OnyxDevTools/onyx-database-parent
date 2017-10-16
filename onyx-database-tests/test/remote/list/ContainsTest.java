package remote.list;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;
import org.junit.*;
import org.junit.experimental.categories.Category;
import remote.base.RemotePrePopulatedBaseTest;

import java.io.IOException;
import java.util.List;

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
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testStringContains() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.CONTAINS, "Some test strin");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

    @Test
    public void testContainsStringId() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id",QueryCriteriaOperator.STARTS_WITH, "FIRST ONE");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(9, results.size());
    }

    @Test
    public void testStringStartsWith() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.CONTAINS, "ome test strin");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

    @Test
    public void testContainsStartsWith() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id",QueryCriteriaOperator.CONTAINS, "IRST ONE");
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(9, results.size());
    }
}

