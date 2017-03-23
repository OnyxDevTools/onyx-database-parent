package remote;

import category.RemoteServerTests;
import com.onyx.client.exception.RequestTimeoutException;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.application.DatabaseServer;
import entities.SimpleEntity;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@Category({ RemoteServerTests.class })
@org.junit.Ignore
public class TestDatabaseInitialization extends RemoteBaseTest
{
    public static final String INVALID_DATABASE_LOCATION = "onx://localhost:8081";
    public static final String DATABASE_LOCATION = "onx://localhost:8080";
    public static final String PERSIST_CONN_DATABASE_LOCATION = "onx://localhost:8082";

    @Test
    public void testPersistantConnection() throws Exception
    {
        DatabaseServer dbServer = new DatabaseServer();
        dbServer.setPort(8082);
        dbServer.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server2.oxd");
        dbServer.start();

        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(PERSIST_CONN_DATABASE_LOCATION);
        fac.setCredentials("admin", "admin");

        fac.initialize();

        PersistenceManager mgr = fac.getPersistenceManager();

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("MYIDYO");

        mgr.saveEntity(simpleEntity);

        dbServer.stop();

        dbServer = new DatabaseServer();
        dbServer.setPort(8082);
        dbServer.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server2.oxd");
        dbServer.start();

        SimpleEntity foundAfterClose = (SimpleEntity)mgr.findById(SimpleEntity.class, simpleEntity.simpleId);

        assert foundAfterClose.getSimpleId().equals(simpleEntity.getSimpleId());

        fac.close();
        dbServer.stop();
    }

    @Test
    public void testTryConnectBeforeStart() throws Exception
    {
        DatabaseServer dbServer = new DatabaseServer();
        dbServer.setPort(8082);
        dbServer.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server2.oxd");

        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(PERSIST_CONN_DATABASE_LOCATION);
        fac.setCredentials("admin", "admin");

        try {
            fac.initialize();
        } catch (InitializationException in){}

        PersistenceManager mgr = fac.getPersistenceManager();

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("MYIDYO");


        try {
            mgr.saveEntity(simpleEntity);
        }
        catch (InitializationException queryException)
        {
        }

        dbServer.start();

        mgr.saveEntity(simpleEntity);
        SimpleEntity foundAfterClose = (SimpleEntity)mgr.findById(SimpleEntity.class, simpleEntity.simpleId);

        assert foundAfterClose.getSimpleId().equals(simpleEntity.getSimpleId());

        fac.close();
        dbServer.stop();
    }

    @Test(expected = InitializationException.class)
    public void testPersistantConnectionNotReOpened() throws Exception {
        RemotePersistenceManagerFactory fac = null;
        try {

            DatabaseServer dbServer = new DatabaseServer();
            dbServer.setPort(8082);
            dbServer.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server2.oxd");
            dbServer.start();

            fac = new RemotePersistenceManagerFactory();
            fac.setDatabaseLocation(PERSIST_CONN_DATABASE_LOCATION);
            fac.setCredentials("admin", "admin");

            long time = System.currentTimeMillis();
            fac.initialize();

            PersistenceManager mgr = fac.getPersistenceManager();

            SimpleEntity simpleEntity = new SimpleEntity();
            simpleEntity.setSimpleId("MYIDYO");


            mgr.saveEntity(simpleEntity);

            dbServer.stop();

            SimpleEntity foundAfterClose = (SimpleEntity) mgr.findById(SimpleEntity.class, simpleEntity.simpleId);

            assert foundAfterClose.getSimpleId().equals(simpleEntity.getSimpleId());
        }
        finally {
            fac.close();
        }

    }

    /**
     * Positive test
     *
     * @throws Exception
     */
    @Test
    public void testInitializeDatabase() throws Exception
    {
        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(DATABASE_LOCATION);
        fac.setCredentials("admin", "admin");

        long time = System.currentTimeMillis();
        fac.initialize();
        System.out.println("Done in " + (System.currentTimeMillis() - time));

        PersistenceManager mgr = fac.getPersistenceManager();

        fac.close();
    }

    /**
     * Negative Test for access violation
     *
     * @throws Exception
     */
    @Ignore
    @Test(expected=InitializationException.class)
    public void testDataFileIsNotAccessible() throws Exception
    {
        PersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(INVALID_DATABASE_LOCATION);
        fac.initialize();

        EmbeddedPersistenceManager mgr  = new EmbeddedPersistenceManager();
        mgr.setContext(fac.getSchemaContext());
    }

    /**
     * Negative Test for invalid credentials
     *
     * @throws Exception
     */
    @Test(expected=InitializationException.class)
    public void testInvalidCredentials() throws Exception
    {
        RemotePersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
        fac.setDatabaseLocation(DATABASE_LOCATION);
        fac.setCredentials("bill", "tom");
        fac.initialize();
        fac.close();
    }


}
