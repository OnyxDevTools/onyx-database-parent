package diskmap;

import category.EmbeddedDatabaseTests;
import com.onyx.map.DefaultMapBuilder;
import com.onyx.map.MapBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.Externalizable;
import java.io.File;
import java.util.Map;

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
        Map<String, Externalizable> myMap = store.getHashMap("first");
        store.close();
    }

    @Test
    public void testInitMultiple()
    {
        MapBuilder store = new DefaultMapBuilder(TEST_DATABASE);
        Map<String, Externalizable> myMap = store.getHashMap("first");
        Map<String, Externalizable> myMap2 = store.getHashMap("second");
        store.close();
    }


}
