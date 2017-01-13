package com.onyx.persistence.factory.impl;

import com.onyx.client.DefaultDatabaseEndpoint;
import com.onyx.client.auth.AuthData;
import com.onyx.client.auth.AuthRMIClientSocketFactory;
import com.onyx.client.auth.AuthSslRMIClientSocketFactory;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.SingletonException;
import com.onyx.exceptions.RemoteInstanceException;
import com.onyx.persistence.context.impl.RemoteSchemaContext;
import com.onyx.persistence.factory.ConnectionManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.SocketPersistenceManager;
import com.onyx.persistence.manager.impl.DefaultSocketPersistenceManager;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.manager.impl.RemotePersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.util.EncryptionUtil;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.ThreadPoolConfig;
import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;

import javax.websocket.DeploymentException;
import javax.websocket.Session;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.ConnectIOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Persistence manager factory for an remote Onyx Database
 *
 * This is responsible for configuring a database connections to an external database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new RemotePersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("onx://23.234.13.33:8080");
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
public class RemotePersistenceManagerFactory extends EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory,ConnectionManager {

    public static final String PERSISTENCE = "/onyx";
    public static final int CONNECTION_TIMEOUT = 20;

    // Query timeout is the max time for database execution time
    protected int queryTimeout = 120;

    protected Session session = null;

    protected DefaultDatabaseEndpoint endpoint = null;

    protected int socketPort = Registry.REGISTRY_PORT;

    // The amount of re-try attempts
    private volatile int retryConnectionCount = 0;

    private static int MAX_RETRY_CONNECTION_ATTEMPTS = 3;

    /**
     * Default Constructor
     * @since 1.0.0
     */
    public RemotePersistenceManagerFactory()
    {
        super();
        this.context = new RemoteSchemaContext(DEFAULT_INSTANCE);
    }

    /**
     * Default Constructor
     *
     * @param instance Cluster Instance unique identifier
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public RemotePersistenceManagerFactory(String instance)
    {
        super(instance);
        this.context = new RemoteSchemaContext(instance);
        this.instance = instance;
    }


    /**
     * Getter for persistence manager.  Modified in 1.1.0 to keep a connection open.  If the connection is somehow
     * closed, this will automatically re-open it.
     *
     * @since 1.0.0
     * @return Instantiated Persistence Manager
     */
    public PersistenceManager getPersistenceManager() {

        if (persistenceManager == null)
        {
            createPersistenceManager();
        }

        return persistenceManager;
    }

    /**
     * Helper method to instantiate and configure the persistence manager
     */
    private void createPersistenceManager()
    {
        this.persistenceManager = new RemotePersistenceManager(this);

        final RemotePersistenceManager tmpPersistenceManager = (RemotePersistenceManager) this.persistenceManager;

        final EmbeddedPersistenceManager systemPersistenceManager;

        // Since the connection remains persistent and open, we do not want to reset the system persistence manager.  That should have
        // remained open and valid through any network blip.
        if(context.getSystemPersistenceManager() == null) {
            try {
                systemPersistenceManager = new EmbeddedPersistenceManager();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            systemPersistenceManager.setContext(context);
            context.setSystemPersistenceManager(systemPersistenceManager);
            ((RemoteSchemaContext) context).setRemoteEndpoint(this.location);
        }

        tmpPersistenceManager.setContext(context);
        tmpPersistenceManager.setDatabaseEndpoint(endpoint);
        tmpPersistenceManager.setFactory(this);

        ((RemoteSchemaContext) context).setDefaultRemotePersistenceManager(persistenceManager);

    }

    // Socket Persistence Manager that utilizes RMI
    protected PersistenceManager remotePersistenceManager = null;

    /**
     * Getter for persistence manager.  This utilizes direct method invocation and secure sockets in order to access data
     * rather than a going through the server.  We recommend using this when using a pure java implementation rather than
     * the getPersistenceManager() method.  The purpose of that is to maximize throughput.
     *
     * @see #getPersistenceManager
     *
     * @since 1.0.0
     * @return Instantiated Persistence Manager
     */
    public PersistenceManager getSocketPersistenceManager() throws EntityException
    {
        if(remotePersistenceManager == null)
        {
            RMIClientSocketFactory socketFactory = null;

            // Setup SSL Socket Client Factory and set the authorization information
            // This is to perform custom authentication to the remote database via the socket
            if(this.useSSL)
            {
                socketFactory = new AuthSslRMIClientSocketFactory();
                ((AuthSslRMIClientSocketFactory)socketFactory).setAuthData(new AuthData(this.user, this.password));
            }
            // Still use authrization data and custom factory if we are not using SSL.
            else
            {
                socketFactory = new AuthRMIClientSocketFactory();
                AuthRMIClientSocketFactory.setHostAuthData(getHostName(), new AuthData(this.user, this.password));
            }

            try {
                final Registry remoteRegistry = LocateRegistry.getRegistry(getHostName(), getSocketPort(), socketFactory);
                SocketPersistenceManager socketPersistenceManager = (SocketPersistenceManager) remoteRegistry.lookup(this.instance);
                this.remotePersistenceManager = new DefaultSocketPersistenceManager(socketPersistenceManager, context);
            } catch (NotBoundException e) {
                throw new RemoteInstanceException(instance);
            } catch (ConnectIOException e)
            {
                throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
            } catch (RemoteException e)
            {
                throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
            }
        }
        return remotePersistenceManager;
    }

    /**
     * The purpose of this is to verify a connection.  This method is to ensure the connection is always open
     * ConnectionManager delegate method
     *
     * @since 1.1.0
     * @throws EntityException Cannot re-connect if not connected
     */
    public void verifyConnection() throws EntityException
    {
        if(session == null || !session.isOpen())
        {
            if(context != null) {
                context.shutdown();
            }
            this.connect();

            RemotePersistenceManager tmpPersistenceManager = (RemotePersistenceManager)this.persistenceManager;

            tmpPersistenceManager.setContext(context);
            tmpPersistenceManager.setDatabaseEndpoint(endpoint);
            tmpPersistenceManager.setFactory(this);

            ((RemoteSchemaContext) context).setDefaultRemotePersistenceManager(persistenceManager);
            context.start();
        }
    }

    /**
     * Connect to the remote database server
     *
     * @since 1.1.0
     * @throws InitializationException Exception occurred while connecting
     */
    public void connect() throws InitializationException
    {
        location = location.replaceFirst("onx://", "ws://");

        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());
        client.getProperties().put(ClientProperties.CREDENTIALS, new Credentials(user, EncryptionUtil.encrypt(password)));
        client.getProperties().put(ClientProperties.WORKER_THREAD_POOL_CONFIG, ThreadPoolConfig.defaultConfig().setMaxPoolSize(16));
        client.getProperties().put(ClientProperties.SHARED_CONTAINER, true);

        if(useSSL)
        {
            this.setupSslSettingsWithClientManager(client);
        }
        endpoint = new DefaultDatabaseEndpoint(queryTimeout, context);

        try {
            session = client.asyncConnectToServer(endpoint, new URI(location + PERSISTENCE)).get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
            endpoint.setSession(session);
        } catch (InterruptedException e) {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION);
        } catch (ExecutionException e) {
            throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
        } catch (TimeoutException e) {
            throw new InitializationException(InitializationException.CONNECTION_TIMEOUT);
        } catch (DeploymentException e) {
            throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
        } catch (URISyntaxException e) {
            throw new InitializationException(InitializationException.INVALID_URI);
        }
    }
    /**
     * Initialize the database connection
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials invalid network connection
     */
    @Override
    public void initialize() throws InitializationException
    {

        connect();

        try
        {
            Query query = new Query(SystemEntity.class, new QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, ""));
            getPersistenceManager().executeQuery(query);
            context.start();
        } catch (EntityException e)
        {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }
        catch (RemoteException e)
        {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }

    }

    /**
     * Configure the client to use SSL
     *
     * @param client Tyrus Client Manager
     */
    protected void setupSslSettingsWithClientManager(ClientManager client)
    {
        // Use Secure socket prefix
        location = location.replaceFirst("ws://", "wss://");

        // Set system properties to ssl settings
        System.getProperties().put(SSLContextConfigurator.KEY_STORE_FILE, this.sslKeystoreFilePath);
        System.getProperties().put(SSLContextConfigurator.TRUST_STORE_FILE, this.sslTrustStoreFilePath);
        System.getProperties().put(SSLContextConfigurator.KEY_STORE_PASSWORD, this.sslKeystorePassword);
        System.getProperties().put(SSLContextConfigurator.TRUST_STORE_PASSWORD, this.sslTrustStorePassword);

        // Build the Tyrus SSL Context Configuration
        final SSLContextConfigurator defaultConfig = new SSLContextConfigurator();
        defaultConfig.retrieve(System.getProperties());

        SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(defaultConfig, true, false, false);

        // Set the configuration
        client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
    }

    /**
     * Safe shutdown of database connection
     * @since 1.0.0
     */
    @Override
    public void close()
    {
        // Force lifecycle stop when done with container.
        // This is to free up threads and resources that the
        // JSR-356 container allocates. But unfortunately
        // the JSR-356 spec does not handle lifecycles (yet)
        try
        {
            session.close();
        } catch (Exception ignore)
        {
        }

        if(context != null) {
            try {
                context.shutdown();
            } catch (SingletonException ignore) {}
        }
        persistenceManager = null;
        context = null;
    }

    /**
     * Set Database Remote location.  This must be formatted with onx://host:port
     *
     * @since 1.0.0
     * @param location Database Remote Endpoint
     */
    @Override
    public void setDatabaseLocation(String location)
    {
        this.location = location;
        if (context != null)
            ((RemoteSchemaContext) context).setRemoteEndpoint(location);
    }

    /**
     * Get Socket port
     * @return
     */
    public int getSocketPort() {
        return socketPort;
    }

    /**
     * Set Socket port for accessing RMI socket interface
     * @param socketPort
     */
    public void setSocketPort(int socketPort) {
        this.socketPort = socketPort;
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
