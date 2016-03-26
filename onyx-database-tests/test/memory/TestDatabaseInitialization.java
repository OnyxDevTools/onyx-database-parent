package memory;

import category.InMemoryDatabaseTests;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@Category({ InMemoryDatabaseTests.class })
public class TestDatabaseInitialization extends memory.base.BaseTest
{
    public static final String INVALID_DATABASE_LOCATION = "/Users/ashley.hampshire";
    public static final String TMP_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/tmpdatbase";

    /**
     * Positive test
     *
     * @throws Exception
     */
    @Test
    public void testInitializeDatabase() throws Exception
    {
        PersistenceManagerFactory fac = new EmbeddedPersistenceManagerFactory();
        fac.setDatabaseLocation(TMP_DATABASE_LOCATION);
        fac.setCredentials("tim", "osborn");
        fac.initialize();

        manager = new EmbeddedPersistenceManager();
        manager.setContext(fac.getSchemaContext());
        fac.close();

        fac = new EmbeddedPersistenceManagerFactory();
        fac.setDatabaseLocation(TMP_DATABASE_LOCATION);
        fac.setCredentials("tim", "osborn");
        fac.initialize();

        Assert.assertTrue(fac.getDatabaseLocation() == TMP_DATABASE_LOCATION);
        Assert.assertTrue(new File(TMP_DATABASE_LOCATION).exists());

        fac.close();
    }

    @AfterClass
    public static void after()
    {
        new File(TMP_DATABASE_LOCATION + "/tmp").delete();
        new File(TMP_DATABASE_LOCATION + "/lock").delete();
        new File(TMP_DATABASE_LOCATION).delete();
    }
}
