package remote.exception;

import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;
import entities.exception.EntityCallbackExceptionEntity;

import java.io.IOException;
import category.RemoteServerTests;


/**
 * Created by timothy.osborn on 2/10/15.
 */
@Category({ RemoteServerTests.class })
public class TestCallbackExceptions extends RemoteBaseTest
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
    public void after() throws EntityException, IOException
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
    public void testPersistCallbackException() throws EntityException
    {
        EntityCallbackExceptionEntity entity = new EntityCallbackExceptionEntity();
        manager.saveEntity(entity);
    }

}
