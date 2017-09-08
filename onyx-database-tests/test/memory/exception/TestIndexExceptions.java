package memory.exception;

import category.InMemoryDatabaseTests;
import com.onyx.exception.InitializationException;
import entities.exception.InvalidIndexTypeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ InMemoryDatabaseTests.class })
public class TestIndexExceptions extends memory.base.BaseTest
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
