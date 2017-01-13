package memory.partition;

import category.InMemoryDatabaseTests;
import com.onyx.exception.InitializationException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category({ InMemoryDatabaseTests.class })
public class BasePartitionTest extends memory.base.BaseTest
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
