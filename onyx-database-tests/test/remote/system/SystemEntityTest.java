package remote.system;

import category.RemoteServerTests;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.util.List;

/**
 * Created by timothy.osborn on 3/7/15.
 */
@Category({ RemoteServerTests.class })
public class SystemEntityTest extends RemoteBaseTest
{

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
        databaseServer.stop();
    }

    @Test
    public void testQuerySystemEntities() throws OnyxException
    {
        Query query = new Query();
        query.setEntityType(SystemEntity.class);
        query.setCriteria(new QueryCriteria("name", QueryCriteriaOperator.NOT_NULL));

        List<SystemEntity> results = manager.executeQuery(query);
        Assert.assertTrue(results.size() > 0);
    }
}
