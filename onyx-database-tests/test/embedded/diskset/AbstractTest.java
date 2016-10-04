package embedded.diskset;

import category.EmbeddedDatabaseTests;
import com.onyx.structure.DefaultMapBuilder;
import com.onyx.structure.LongDiskSet;
import com.onyx.structure.MapBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.Externalizable;
import java.io.File;
import java.util.Map;
import java.util.Set;

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

    @Test
    public void testInit()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Set longSet = store.newLongSet();
        Set otherSet = store.getLongSet(((LongDiskSet)longSet).getReference().position);
        assert longSet == otherSet;
        store.close();
    }

}
