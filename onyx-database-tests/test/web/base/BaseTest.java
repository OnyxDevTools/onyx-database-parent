package web.base;

import com.onyx.application.DatabaseServer;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.factory.impl.WebPersistenceManagerFactory;
import com.onyx.persistence.context.impl.WebSchemaContext;
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
    protected WebSchemaContext context;

    protected SecureRandom random = new SecureRandom();
    protected WebPersistenceManagerFactory factory;

    protected static final String DATABASE_LOCATION = "http://localhost:8080";
    protected static final String LOCAL_DATABASE_LOCATION = "onyx.database.location=C:/Sandbox/Onyx/Tests/web.oxd";

    public static DatabaseServer testApplication = null;

    /**
     * Initialize Database
     */
    protected void initialize() throws InitializationException {
        if (context == null) {
            factory = new WebPersistenceManagerFactory();
            factory.setDatabaseLocation(DATABASE_LOCATION);
            factory.setCredentials("admin", "admin");
            factory.initialize();

            context = (WebSchemaContext)factory.getSchemaContext();

            manager = factory.getPersistenceManager();
        }
    }

    protected void shutdown() throws EntityException, IOException {
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
        File database = new File(LOCAL_DATABASE_LOCATION);
        if (database != null && database.exists()) {
            delete(database);
        }
        database.delete();
    }

    public void save(IManagedEntity entity) {
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            e.printStackTrace();
            fail("Error saving entity");
        }
    }

    public IManagedEntity find(IManagedEntity entity) {
        try {
            return manager.find(entity);
        } catch (EntityException e) {
            e.printStackTrace();
            fail("Error finding entity");
        }
        return null;
    }

    public void delete(IManagedEntity entity) {
        try {
            manager.deleteEntity(entity);
        } catch (EntityException e) {
            e.printStackTrace();
            fail("Error deleting entity");
        }
    }

    public void initialize(IManagedEntity entity, String attribute) {
        try {
            manager.initialize(entity, attribute);
        } catch (EntityException e) {
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
