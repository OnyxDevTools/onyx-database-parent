package com.onyx.application;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.entity.SystemUser;
import com.onyx.entity.SystemUserRole;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.server.auth.DefaultAuthenticationManager;
import com.onyx.server.base.AbstractDatabaseServer;
import com.onyx.server.base.ServerState;
import com.onyx.server.cli.CommandLineParser;
import com.onyx.server.rmi.OnyxRMIServer;
import com.onyx.util.EncryptionUtil;


/**
 * Base Database Server Application.
 * <p>
 * All servers must inherit from this class in order to extend the Onyx Remote Database.  This does not have
 * any logic that is used within the Web Database.
 * <p>
 * <pre>
 * <code>
 *
 *    DatabaseServer server1 = new DatabaseServer();
 *    server1.setPort(8080);
 *    server1.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 *    server1.start();
 *    server1.join();
 *
 *
 *   To invoke command line
 *   java -cp path_to_my_classes(.jar):onyx-remote-database-(0.0.1)-alpha.jar com.onyx.application.DatabaseServer -l /path/to/database/location -port=8080
 *
 *   e.x
 *   java -cp /Users/tosborn1/Dropbox/OnyxSandbox/onyxdb-parent/OnyxDatabaseTests/target/classes/:onyx-remote-database-0.0.1-alpha.jar com.onyx.application.DatabaseServer -l /Users/tosborn1/Desktop/database1 -port=8080
 * </code>
 * </pre>
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
public class DatabaseServer extends AbstractDatabaseServer implements OnyxServer {

    // RMI Server.  This is the underlying network io server
    @SuppressWarnings("WeakerAccess")
    protected OnyxRMIServer rmiServer;

    @SuppressWarnings("WeakerAccess")
    protected PersistenceManagerFactory persistenceManagerFactory;

    private AuthenticationManager authenticationManager = null;

    /**
     * Constructor
     */
    public DatabaseServer() {
        this.persistenceManagerFactory = new EmbeddedPersistenceManagerFactory();
    }

    /**
     * Run Database Server Main Method
     * <p>
     * ex:  executable /Database/Location/On/Disk 8080 admin admin
     *
     * @param args Command Line Arguments
     * @throws Exception General Exception
     * @since 1.0.0
     */
    public static void main(String[] args) throws Exception {

        final DatabaseServer instance = new DatabaseServer();

        final CommandLineParser commandLineParser = new CommandLineParser();
        commandLineParser.configureDatabaseWithCommandLineOptions(instance, args);

        instance.start();
        instance.join();
    }


    /**
     * Start the database socket server
     *
     * @since 1.0.0
     */
    @Override
    public void start() {
        if (state != ServerState.RUNNING) {
            try {
                this.persistenceManagerFactory.setCredentials(this.user, this.password);
                this.persistenceManagerFactory.setDatabaseLocation(this.location);
                this.persistenceManagerFactory.initialize();

                // Create a default user
                SystemUser user = new SystemUser();
                user.setUsername(this.user);
                user.setPassword(EncryptionUtil.encrypt(this.password));
                user.setRole(SystemUserRole.ROLE_ADMIN);

                // Default User and password
                persistenceManagerFactory.getPersistenceManager().saveEntity(user);

                this.authenticationManager = new DefaultAuthenticationManager(persistenceManagerFactory.getPersistenceManager());

                // Create the RMI Server
                rmiServer = new OnyxRMIServer();
                rmiServer.setPort(this.port);
                rmiServer.setSslKeystoreFilePath(this.sslKeystoreFilePath);
                rmiServer.setSslKeystorePassword(this.sslKeystorePassword);
                rmiServer.setSslTrustStoreFilePath(this.sslTrustStoreFilePath);
                rmiServer.setSslTrustStorePassword(this.sslTrustStorePassword);
                rmiServer.setSslStorePassword(this.sslStorePassword);
                rmiServer.setMaxWorkerThreads(this.maxWorkerThreads);

                registerServices();

                try {
                    rmiServer.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                state = ServerState.RUNNING;

            } catch (Throwable t) {
                t.printStackTrace(System.err);
            }
        }
    }

    /**
     * Register services.  This method registers all of the proxy objects and makes them public
     */
    @SuppressWarnings("WeakerAccess")
    protected void registerServices()
    {
        // Register the Persistence Manager
        rmiServer.register((short) 1, this.persistenceManagerFactory.getPersistenceManager(), PersistenceManager.class);
        rmiServer.register((short) 2, this.authenticationManager, AuthenticationManager.class);
    }

    /**
     * Stop the database server
     *
     * @since 1.0.0
     */
    public void stop()
    {
        rmiServer.stop();
        persistenceManagerFactory.close();
        super.stop();
    }
}


