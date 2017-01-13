package remote.base;

import com.onyx.application.DatabaseServer;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.impl.RemoteSchemaContext;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import org.junit.AfterClass;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.registry.Registry;
import java.security.SecureRandom;

import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class RemoteBaseTest {

    static public DatabaseServer databaseServer = null;

    public static final boolean USE_SOCKET_PROTOCOL = true;
    protected PersistenceManager manager;
    protected RemoteSchemaContext context;

    protected SecureRandom random = new SecureRandom();
    protected RemotePersistenceManagerFactory factory;

    protected static final String DATABASE_LOCATION = "onx://localhost:8080";
    protected static final String LOCAL_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/server.oxd";

    /**
     * Initialize Database
     */
    protected void initialize() throws InitializationException {
        if (context == null) {
            factory = new RemotePersistenceManagerFactory();

            factory.setDatabaseLocation(DATABASE_LOCATION);
            factory.setCredentials("admin", "admin");
            factory.setSocketPort(Registry.REGISTRY_PORT);
            factory.initialize();

            context = (RemoteSchemaContext)factory.getSchemaContext();


            if(USE_SOCKET_PROTOCOL) {
                try {
                    manager = factory.getSocketPersistenceManager();
                } catch (EntityException e) {
                    e.printStackTrace();
                }
            }
            else
            {
                manager = factory.getPersistenceManager();
            }
        }
    }

    protected void shutdown() throws IOException {
        if (factory != null)
            factory.close();
        context = null;
        factory = null;
        manager = null;
    }

    private static void delete(File f) {
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
