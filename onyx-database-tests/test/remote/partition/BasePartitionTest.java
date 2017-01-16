package remote.partition;

import category.RemoteServerTests;
import com.onyx.exception.InitializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category({ RemoteServerTests.class })
public class BasePartitionTest extends RemoteBaseTest
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
    }

}
