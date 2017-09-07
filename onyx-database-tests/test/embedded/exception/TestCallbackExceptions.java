package embedded.exception;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import embedded.base.BaseTest;
import entities.exception.EntityCallbackExceptionEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;


/**
 * Created by timothy.osborn on 2/10/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class TestCallbackExceptions extends BaseTest
{
    /**
     * Created by timothy.osborn on 12/14/14.
     */

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

    int z = 0;

    protected synchronized void increment()
    {
        z++;
    }

    protected synchronized int getZ()
    {
        return z;
    }

    @Test(expected = EntityCallbackException.class)
    public void testPersistCallbackException() throws OnyxException
    {
        EntityCallbackExceptionEntity entity = new EntityCallbackExceptionEntity();
        manager.saveEntity(entity);
    }

}
