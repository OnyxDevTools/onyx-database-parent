package remote.exception;

import category.RemoteServerTests;
import com.onyx.exception.InitializationException;
import entities.exception.InvalidIndexTypeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ RemoteServerTests.class })
public class TestIndexExceptions extends RemoteBaseTest
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

}
