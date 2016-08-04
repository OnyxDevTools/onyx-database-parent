package com.onyx.application;

import com.onyx.client.auth.AuthRMIClientSocketFactory;
import com.onyx.client.auth.Authorize;
import com.onyx.entity.*;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.SocketPersistenceManager;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.server.DatabaseConnectionListener;
import com.onyx.server.DatabaseHandshakeHandler;
import com.onyx.server.DatabaseIdentityManager;
import com.onyx.server.JSONDatabaseMessageListener;
import com.onyx.server.auth.*;
import com.onyx.util.EncryptionUtil;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import com.onyx.client.auth.AuthSslRMIClientSocketFactory;
import org.apache.commons.cli.*;

import javax.net.ssl.*;

import static io.undertow.Handlers.resource;

/**
 * Base Database Server Application.
 *
 * All servers must inherit from this class in order to extend the Onyx Remote Database.  This does not have
 * any logic that is used within the Web Database.
 *
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
 *   java -cp pathtomyclasses(.jar):onyx-remote-database-(0.0.1)-alpha.jar com.onyx.application.DatabaseServer -l /path/to/database/location -port=8080
 *
 *   e.x
 *   java -cp /Users/tosborn1/Dropbox/OnyxSandbox/onyxdb-parent/OnyxDatabaseTests/target/classes/:onyx-remote-database-0.0.1-alpha.jar com.onyx.application.DatabaseServer -l /Users/tosborn1/Desktop/database1 -port=8080
 * </code>
 * </pre>
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
public class DatabaseServer extends EmbeddedPersistenceManagerFactory implements OnyxServer {

    protected int port;

    protected Undertow server;

    /**
     * Server State is either started or stopped
     * @since 1.0.0
     */
    public enum ServerState {
        START,
        STOP
    }

    protected IdentityManager databaseAuthenticationManager = null;

    protected ServerState state = ServerState.STOP;

    protected Registry rmiServerRegistry = null;

    // Authorization properties
    private static RMIClientSocketFactory clientSocketFactory;
    private static RMIServerSocketFactory serverSocketFactory;

    public static final String DEFAULT_INSTANCE = "ONYX_DATABASE";

    // Cluster instance unique identifier
    protected String instance = DEFAULT_INSTANCE;

    // Command Line Options
    public static final String OPTION_PORT = "port";
    public static final String OPTION_USER = "user";
    public static final String OPTION_PASSWORD = "password";
    public static final String OPTION_LOCATION = "location";
    public static final String OPTION_INSTANCE = "instance";
    public static final String OPTION_KEYSTORE = "keystore";
    public static final String OPTION_TRUST_STORE = "trust-store";
    public static final String OPTION_KEYSTORE_PASSWORD = "keystore-password";
    public static final String OPTION_TRUST_STORE_PASSWORD = "trust-password";
    public static final String OPTION_DISABLE_SOCKET = "disable-socket";
    public static final String OPTION_SOCKET_PORT = "socket-port";
    public static final String OPTION_HELP = "help";

    // Keystore property keys
    public static final String TRUST_STORE_FILE = "javax.net.ssl.trustStore";
    public static final String KEY_STORE_FILE = "javax.net.ssl.keyStore";
    public static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    public static final String KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";

    /**
     * Run Database Server Main Method
     *
     * ex:  executable /Database/Location/On/Disk 8080 admin admin
     *
     * @since 1.0.0
     * @param args Command Line Arguments
     * @throws Exception General Exception
     */
    public static void main(String[] args) throws Exception
    {

        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        Options options = configureCommandLineOptions();

        CommandLine commandLine = null;
        try {
            // parse the command line arguments
            commandLine = parser.parse( options, args );
        }
        catch( ParseException exp ) {
            // oops, something went wrong
            System.err.println( "Invalid Arguments.  Reason: " + exp.getMessage() );
            return;
        }

        if(commandLine.hasOption(OPTION_HELP))
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "onyx", options );
            return;
        }

        if(!commandLine.hasOption(OPTION_LOCATION))
        {
            System.err.println( "Invalid Database Location.  Location is required!");
            return;
        }

        final OnyxServer instance = new DatabaseServer();

        configureDatabaseWithCommandLineOptions((DatabaseServer)instance, commandLine);

        instance.start();
        instance.join();
    }

    /**
     * Configure Command Line Options for Data Server
     *
     * @return CLI Options
     */
    protected static Options configureCommandLineOptions()
    {
        // create the Options
        Options options = new Options();


        options.addOption( "P", OPTION_PORT, true, "Server port number" );
        options.addOption( "u", OPTION_USER, true, "Database username" );
        options.addOption( "p", OPTION_PASSWORD, true, "Database password");
        options.addOption( "l", OPTION_LOCATION, true, "Database filesystem location" );
        options.addOption( "i", OPTION_INSTANCE, true, "Database instance name" );
        options.addOption( "k", OPTION_KEYSTORE, true, "Keystore file path." );
        options.addOption( "t",  OPTION_TRUST_STORE, true, "Trust Store file path." );
        options.addOption( "kp",  OPTION_KEYSTORE_PASSWORD, true, "Keystore password. " );
        options.addOption( "tp",  OPTION_TRUST_STORE_PASSWORD, true, "Trust Store password.");
        options.addOption( "ds",  OPTION_DISABLE_SOCKET, false, "(BOOL) Disable socket support." );
        options.addOption( "sp",  OPTION_SOCKET_PORT, true, "Socket Server port.  Default - " + Registry.REGISTRY_PORT );

        options.addOption( "h", OPTION_HELP, false, "Help" );

        return options;
    }

    /**
     * Configure Database With Command Line Options
     *
     * @param databaseServer - Database Server instance
     * @param commandLine - Parsed Command Line Options
     */
    protected static void configureDatabaseWithCommandLineOptions(DatabaseServer databaseServer, CommandLine commandLine)
    {
        if(commandLine.hasOption(OPTION_PORT))
        {
            databaseServer.setPort(Integer.valueOf(commandLine.getOptionValue(OPTION_PORT)));
        }

        if(commandLine.hasOption(OPTION_USER)
                && commandLine.hasOption(OPTION_PASSWORD))
        {
            databaseServer.setCredentials(commandLine.getOptionValue(OPTION_USER), commandLine.getOptionValue(OPTION_PASSWORD));
        }
        else
        {
            databaseServer.setCredentials("admin", "admin");
        }

        databaseServer.setDatabaseLocation(commandLine.getOptionValue(OPTION_LOCATION));

        if(commandLine.hasOption(OPTION_INSTANCE))
        {
            databaseServer.instance = commandLine.getOptionValue(OPTION_INSTANCE);
        }

        if(commandLine.hasOption(OPTION_KEYSTORE))
        {
            databaseServer.setSslKeystoreFilePath(commandLine.getOptionValue(OPTION_KEYSTORE));
            databaseServer.setUseSSL(true);
        }

        if(commandLine.hasOption(OPTION_TRUST_STORE))
        {
            databaseServer.setSslTrustStoreFilePath(commandLine.getOptionValue(OPTION_TRUST_STORE));
        }

        if(commandLine.hasOption(OPTION_KEYSTORE_PASSWORD))
        {
            databaseServer.setSslKeystorePassword(commandLine.getOptionValue(OPTION_KEYSTORE_PASSWORD));
        }

        if(commandLine.hasOption(OPTION_TRUST_STORE_PASSWORD))
        {
            databaseServer.setSslTrustStorePassword(commandLine.getOptionValue(OPTION_TRUST_STORE_PASSWORD));
        }

        if(commandLine.hasOption(OPTION_DISABLE_SOCKET))
        {
            databaseServer.setEnableSocketSupport(false);
        }
        else
        {
            databaseServer.setEnableSocketSupport(true);
        }

        if(commandLine.hasOption(OPTION_SOCKET_PORT))
        {
            databaseServer.setSocketPort(Integer.valueOf(commandLine.getOptionValue(OPTION_SOCKET_PORT)));
        }
    }

    /**
     * Start the database server
     * @since 1.0.0
     */
    @Override
    public void start()
    {
        if (state != ServerState.START)
        {
            try
            {

                this.context = new DefaultSchemaContext(instance);
                this.context.setLocation(location);

                final EmbeddedPersistenceManager systemPersistenceManager = new EmbeddedPersistenceManager();
                systemPersistenceManager.setContext(this.context);
                this.context.setSystemPersistenceManager(systemPersistenceManager);
                this.initialize();

                SystemUser user = new SystemUser();
                user.setUsername(this.user);
                user.setPassword(EncryptionUtil.encrypt(this.password));
                user.setRole(SystemUserRole.ROLE_ADMIN);

                // Default User and password
                systemPersistenceManager.saveEntity(user);

                // Setup the socket authorization class.  Note: This must be done before we call the getPersistenceManager
                // otherwise it will cause a race condition.
                SocketDatabaseAuthrorizeImpl socketDatabaseAuthrorize = new SocketDatabaseAuthrorizeImpl();
                setupSocketSupportWithAuthorization(socketDatabaseAuthrorize);

                // Session Manager
                final SessionManager sessionManager = new InMemorySessionManager("SESSION_MANAGER");
                final SessionCookieConfig sessionConfig = new SessionCookieConfig();

                // Setup Authentication classes
                socketDatabaseAuthrorize.setSystemPersistenceManager(this.getPersistenceManager());
                databaseAuthenticationManager = new DatabaseIdentityManager(getPersistenceManager());

                // File Server Handler
                ResourceHandler fileServerHandler = resource(new FileResourceManager(new File(System.getProperty("user.home")), 100))
                        .setDirectoryListingEnabled(true);

                // Persistence Handler
                final PathHandler persistenceHandler = Handlers.path()
                        .addPrefixPath("/onyx", new DatabaseHandshakeHandler(new DatabaseConnectionListener(getPersistenceManager(), context, null), new JSONDatabaseMessageListener(persistenceManager, context)))
                        .addPrefixPath("/files", fileServerHandler);

                // Security Handler
                HttpHandler securityHandler = new AuthenticationCallHandler(persistenceHandler);
                securityHandler = new AuthenticationConstraintHandler(securityHandler);
                final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("DATABASE REALM"));
                securityHandler = new AuthenticationMechanismsHandler(securityHandler, mechanisms);
                securityHandler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, databaseAuthenticationManager, securityHandler);

                // Create the base handler
                SessionAttachmentHandler baseHandler = new SessionAttachmentHandler(sessionManager, sessionConfig);
                baseHandler.setNext(securityHandler);


                final Undertow server = buildUndertowConfigurationWithHandler(baseHandler);

                try
                {
                    server.start();
                } catch (Exception e)
                {
                    //state = ServerState.STOP;
                    throw new RuntimeException(e);
                }


                // Register RMI persistence manager service
                if (enableSocketSupport) {
                    try {
                        this.rmiServerRegistry = LocateRegistry.createRegistry(this.getSocketPort(), clientSocketFactory, serverSocketFactory);
                        this.rmiServerRegistry.rebind(this.instance, (SocketPersistenceManager) persistenceManager);
                    } catch (RemoteException e) {
                        throw new RuntimeException(e);
                    }
                }

                state = ServerState.START;


            } catch (Throwable t)
            {
                t.printStackTrace(System.err);
            }
        }
    }

    /**
     * Build Undertow server and define its handler
     * In addition to that it determines whether the server should be setup for SSL or regular HTTP
     *
     * @param baseHandler HTTP Handler
     *
     * @return Undertow Server Instance
     */
    protected Undertow buildUndertowConfigurationWithHandler(HttpHandler baseHandler) throws Exception
    {
        // Build the server configuration
        if (useSSL)
        {
            SSLContext sslContext = createSSLContext(loadKeyStore(this.sslKeystoreFilePath, this.sslKeystorePassword), loadKeyStore(this.sslTrustStoreFilePath, this.sslKeystorePassword), this.sslKeystorePassword);

            server = Undertow.builder()
                    .addHttpsListener(getPort(), "0.0.0.0", sslContext)
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build();
        }
        else
        {
            server = Undertow.builder()
                    .addHttpListener(getPort(), "0.0.0.0")
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build();
        }

        return server;
    }

    /**
     * Setup Socket Support if we are entitled to it
     *
     * This will determine the socket methodology and authorization methodology
     *
     * @param socketDatabaseAuthrorize socketDatabaseAuthrorize instance of socket authorization
     */
    protected void setupSocketSupportWithAuthorization(Authorize socketDatabaseAuthrorize)
    {
        if(enableSocketSupport) {
            if (useSSL) {

                System.getProperties().put(KEY_STORE_FILE, this.sslKeystoreFilePath);
                System.getProperties().put(TRUST_STORE_FILE, this.sslTrustStoreFilePath);
                System.getProperties().put(KEY_STORE_PASSWORD, this.sslKeystorePassword);
                System.getProperties().put(TRUST_STORE_PASSWORD, this.sslTrustStorePassword);

                serverSocketFactory = new AuthSslRMIServerSocketFactory(socketDatabaseAuthrorize);
                clientSocketFactory = new AuthSslRMIClientSocketFactory();
            } else {
                serverSocketFactory = new AuthRMIServerSocketFactory(socketDatabaseAuthrorize);
                clientSocketFactory = new AuthRMIClientSocketFactory();
            }
        }
    }

    /**
     * Load Keystore from a file
     * @param name file path the keystore is located
     * @param password keystore password
     * @return KeyStore POJO
     * @throws Exception
     */
    private static KeyStore loadKeyStore(String name, String password) throws Exception {
        final InputStream stream = Files.newInputStream(Paths.get(name));

        try(InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, password.toCharArray());
            return loadedKeystore;
        }
    }

    /**
     * Create SSL Context with all the keystore information.  This is used to configure SSL for Undertow IO
     *
     * @param keyStore Keystore used to verify SSL
     * @param trustStore Trust store used to determine Trust information
     * @param password Keystore Password
     * @return SSLContext with the correct protocols, and algorithms
     * @throws Exception Generic Exception
     */
    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore, String password) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();

        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        return sslContext;
    }

    /**
     * Stop the database server
     * @since 1.0.0
     */
    @Override
    public void stop()
    {
        try
        {
            server.stop();

            if(enableSocketSupport) {
                rmiServerRegistry.unbind(instance);
            }

            this.close();

            if(startStopCountDownLatch != null) {
                startStopCountDownLatch.countDown();
            }
        } catch (Exception ignore)
        {
            ignore.printStackTrace();
        } finally
        {
            state = ServerState.STOP;
        }
    }

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    @Override
    public boolean isRunning()
    {
        return (state == ServerState.START);
    }

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    @Override
    public int getPort()
    {
        return port;
    }

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     * @param port Port Number
     */
    @Override
    public void setPort(int port)
    {
        this.port = port;
    }

    // Count down latch used to keep the application alive
    protected CountDownLatch startStopCountDownLatch;

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    public void join()
    {
        try {
            startStopCountDownLatch = new CountDownLatch(1);
            startStopCountDownLatch.await();
        } catch (InterruptedException e) {}
    }

    /**
     * Set authentication filter / Identity Manager
     *
     * @since 1.0.0
     * @param identityManager Security filter
     */
    @Override
    public void setAuthenticationIdentityManager(IdentityManager identityManager)
    {
        this.databaseAuthenticationManager = identityManager;
    }

    /**
     * Getter for creating and returning the persistence manager.
     * It will configure and set the schema context and in addition this method will register
     * an RMI Service for the persistence manager
     * @return PersistenceManager
     */
    @Override
    public PersistenceManager getPersistenceManager()
    {
        if(persistenceManager == null)
        {
            try {
                this.persistenceManager = new EmbeddedPersistenceManager(true);
                ((EmbeddedPersistenceManager)this.persistenceManager).setJournalingEnabled(this.enableJournaling);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            this.persistenceManager.setContext(context);
        }
        return persistenceManager;
    }

    //////////////////////////////////////////////////////////////////////////////////
    //
    // Socket Support Settings
    //
    // Socket support enables RMI communication.
    // This is more efficient than the Undertow server
    //
    //////////////////////////////////////////////////////////////////////////////////

    protected int socketPort = Registry.REGISTRY_PORT;
    protected boolean enableSocketSupport = false;

    /**
     * Get Socket Port that the socket server will run on.
     * @return
     */
    public int getSocketPort() {
        return socketPort;
    }

    /**
     * Set socket port.  This will default to 1099 Registry.REGISTRY_PORT
     * @param socketPort
     */
    public void setSocketPort(int socketPort) {
        this.socketPort = socketPort;
    }

    /**
     * Setter for socket support.  If you enable this, it will allow you to access Onyx Database via sockets rather than Undertow Server
     * @param enableSocketSupport
     */
    public void setEnableSocketSupport(boolean enableSocketSupport) {
        this.enableSocketSupport = enableSocketSupport;
    }

    //////////////////////////////////////////////////////////////////////////////////
    //
    // SSL Security Settings
    //
    //////////////////////////////////////////////////////////////////////////////////

    // Keystore Password
    protected String sslKeystorePassword;

    // Keystore file path
    protected String sslKeystoreFilePath;

    // Trust Store file path
    protected String sslTrustStoreFilePath;

    // Trust store password.  This is typically the same as keystore Password
    protected String sslTrustStorePassword;

    // Use SSL, by default this is false
    protected boolean useSSL = false;


    /**
     * Setter for SSL Keystore Password.  This depends on useSSL being true
     * @param sslKeystorePassword Keystore Password
     */
    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    /**
     * Setter for SSL Keystore file path.  This is typically in format "C:\\ssl\\clientkeystore.jks")
     * @param sslKeystoreFilePath Keystore File Path
     */
    public void setSslKeystoreFilePath(String sslKeystoreFilePath) {
        this.sslKeystoreFilePath = sslKeystoreFilePath;
    }

    /**
     * Setter for SSL Trust Store file path.  This is typically in format "C:\\ssl\\clienttruststore.jks".jks")
     * @param sslTrustStoreFilePath Trust Store File Path
     */
    public void setSslTrustStoreFilePath(String sslTrustStoreFilePath) {
        this.sslTrustStoreFilePath = sslTrustStoreFilePath;
    }

    /**
     * Trust store password.  This is typically the same as your keystore password
     * @param sslTrustStorePassword Trust Store Password
     */
    public void setSslTrustStorePassword(String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    /**
     * Defines whether you would like to use regular http or SSL
     * @param useSSL Use SSL true or not false
     */
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

}


