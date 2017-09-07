package web.index;

import category.WebServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import entities.identifiers.StringIdentifierEntity;
import entities.index.StringIdentifierEntityIndex;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by timothy.osborn on 1/23/15.
 */
@Category({ WebServerTests.class })
public class SaveIndexTest extends BaseTest
{

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @Test
    public void saveStringIndexUpdateTest() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

        entity.indexValue = "BLA";
        save(entity);

        results = manager.executeQuery(findQuery);
        Assert.assertTrue(results.size() == 0);

        Query findQuery2 = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "BLA"));
        results = manager.executeQuery(findQuery2);

        Assert.assertTrue(results.size() == 1);

    }

    @Test
    public void saveStringIndexDeleteTest() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

        entity.indexValue = "BLA";
        delete(entity);

        results = manager.executeQuery(findQuery);
        Assert.assertTrue(results.size() == 0);

    }

    @Test
    public void saveStringIndexDeleteQueryTest() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

        Query deleteQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        manager.executeDelete(deleteQuery);

        results = manager.executeQuery(findQuery);
        Assert.assertTrue(results.size() == 0);

    }

    @Test
    public void saveStringIndexUpdateQueryTest() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

        Query updateQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"), new AttributeUpdate("indexValue", "HIYA"));
        manager.executeUpdate(updateQuery);

        results = manager.executeQuery(findQuery);
        Assert.assertTrue(results.size() == 0);


        Query updatedFindQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("indexValue", QueryCriteriaOperator.EQUAL, "HIYA"));

        results = manager.executeQuery(updatedFindQuery);
        Assert.assertTrue(results.size() == 1);

    }

    @Test
    public void testSaveWithExistingFullScanPrior() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

    }

    @Test
    public void testSaveWithExistingFullScanPriorWitIn() throws OnyxException
    {
        StringIdentifierEntityIndex entity = new StringIdentifierEntityIndex();
        entity.identifier = "A";
        entity.indexValue = "INDEX VALUE";
        save(entity);

        Query findQuery = new Query(StringIdentifierEntityIndex.class, new QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.IN, Arrays.asList("INDEX VALUE")));
        List<StringIdentifierEntity> results = manager.executeQuery(findQuery);

        Assert.assertTrue(results.size() == 1);

    }
}
