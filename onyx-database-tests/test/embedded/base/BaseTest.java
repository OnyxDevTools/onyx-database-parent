package embedded.base;

import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.Contexts;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import org.junit.AfterClass;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class BaseTest {
    protected PersistenceManager manager;
    protected SchemaContext context;

    protected SecureRandom random = new SecureRandom();
    protected PersistenceManagerFactory factory;

    protected static final String DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/onyx.oxd";

    /**
     * Initialize Database
     * @throws InitializationException
     */
    protected void initialize() throws InitializationException {
        if (context == null) {
            Contexts.clear();
            factory = new EmbeddedPersistenceManagerFactory(DATABASE_LOCATION);
            factory.initialize();

            context = factory.getSchemaContext();

            manager = factory.getPersistenceManager();
            manager.setContext(factory.getSchemaContext());
        }
    }

    protected void shutdown() throws IOException {
        if (factory != null)
            factory.close();
    }

    public static void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }

    public static void deleteDatabase() {
        File database = new File(DATABASE_LOCATION);
        if (database != null && database.exists()) {
            delete(database);
        }
        database.delete();
        Contexts.clear();
    }

    public void save(IManagedEntity entity) {
        try {
            manager.saveEntity(entity);
        } catch (OnyxException e) {
            e.printStackTrace();
            fail("Error saving entity");
        }
    }

    public IManagedEntity find(IManagedEntity entity) {
        try {
            return manager.find(entity);
        } catch (OnyxException e) {
            e.printStackTrace();
            fail("Error finding entity");
        }
        return null;
    }

    public void delete(IManagedEntity entity) {
        try {
            manager.deleteEntity(entity);
        } catch (OnyxException e) {
            e.printStackTrace();
            fail("Error deleting entity");
        }
    }

    public void initialize(IManagedEntity entity, String attribute) {
        try {
            manager.initialize(entity, attribute);
        } catch (OnyxException e) {
            e.printStackTrace();
            fail("Error saving entity");
        }
    }

    @AfterClass
    public static void afterClass() {

    }

    protected String getRandomString()
    {
        return new BigInteger(130, random).toString(32);
    }

    protected int getRandomInteger()
    {
        return new BigInteger(10, random).intValue();
    }
}
