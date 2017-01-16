package web.partition;

import category.WebServerTests;
import com.onyx.exception.InitializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category({ WebServerTests.class })
public class BasePartitionTest extends BaseTest
{
    @BeforeClass
    public static void beforeClass()
    {
        deleteDatabase();
    }

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
