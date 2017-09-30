package embedded.diskmap;

import category.EmbeddedDatabaseTests;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

import java.io.File;

/**
 * Created by timothy.osborn on 3/21/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class AbstractTest
{

    public static final String TEST_DATABASE = "C:/Sandbox/Onyx/Tests/hiya.db";


    @BeforeClass
    public static void beforeTest()
    {
        File testDataBase = new File(TEST_DATABASE);
        if(testDataBase.exists())
        {
            testDataBase.delete();
        }
    }

}
