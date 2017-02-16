package com.onyx.persistence.factory.impl;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.SingletonException;
import com.onyx.persistence.context.impl.RemoteSchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.manager.impl.RemotePersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.client.SSLPeer;
import com.onyx.client.exception.ConnectionFailedException;
import com.onyx.client.rmi.OnyxRMIClient;

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
 *
 * Tim Osborn, 02/13/2017 - This was augmented to use the new RMI Socket Server.  It has since been optimized
 */
public class RemotePersistenceManagerFactory extends EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory, SSLPeer {

    private OnyxRMIClient onyxRMIClient = null;

    /**
     * Default Constructor
     * @since 1.0.0
     */
    public RemotePersistenceManagerFactory()
    {
        super();
        onyxRMIClient = new OnyxRMIClient();
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
        this.context = new RemoteSchemaContext(instance);

        PersistenceManager proxy = (PersistenceManager)onyxRMIClient.getRemoteObject((short)1, PersistenceManager.class);
        this.persistenceManager = new RemotePersistenceManager(proxy);
        this.persistenceManager.setContext(context);

        final EmbeddedPersistenceManager systemPersistenceManager;

        // Since the connection remains persistent and open, we do not want to reset the system persistence manager.  That should have
        // remained open and valid through any network blip.
        if (context.getSystemPersistenceManager() == null) {
            systemPersistenceManager = new EmbeddedPersistenceManager();
            systemPersistenceManager.setContext(context);
            context.setSystemPersistenceManager(systemPersistenceManager);
        }

        ((RemoteSchemaContext) context).setDefaultRemotePersistenceManager(persistenceManager);
    }

    /**
     * Connect to the remote database server
     *
     * @since 1.1.0
     * @throws InitializationException Exception occurred while connecting
     */
    private void connect() throws InitializationException
    {
        location = location.replaceFirst("onx://", "");
        String[] locationParts = location.split(":");

        String host = locationParts[0];
        String port = locationParts[1];

        onyxRMIClient.setSslTrustStoreFilePath(this.sslTrustStoreFilePath);
        onyxRMIClient.setSslTrustStorePassword(this.sslTrustStorePassword);
        onyxRMIClient.setSslKeystoreFilePath(this.sslKeystoreFilePath);
        onyxRMIClient.setSslKeystorePassword(this.sslKeystorePassword);
        onyxRMIClient.setSslStorePassword(this.sslStorePassword);
        onyxRMIClient.setCredentials(this.user, this.password);
        AuthenticationManager authenticationManager = (AuthenticationManager)onyxRMIClient.getRemoteObject((short)2, AuthenticationManager.class);
        onyxRMIClient.setAuthenticationManager(authenticationManager);

        try {
            onyxRMIClient.connect(host, Integer.valueOf(port));
        } catch (ConnectionFailedException e) {
            this.close();
            throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
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
    }

    /**
     * Safe shutdown of database connection
     * @since 1.0.0
     */
    @Override
    public void close()
    {
        onyxRMIClient.close();

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
    }

    // SSL Protocol
    private String protocol = "TLSv1.2";

    // Keystore Password
    private String sslStorePassword;

    // Keystore file path
    private String sslKeystoreFilePath;

    // Keystore Password
    private String sslKeystorePassword;

    // Trust Store file path
    private String sslTrustStoreFilePath;

    // Trust store password.  This is typically the same as keystore Password
    private String sslTrustStorePassword;

    /**
     * Set for SSL Store Password.  Note, this is different than Keystore Password
     * @param sslStorePassword Password for SSL Store
     * @since 1.2.0
     */
    public void setSslStorePassword(String sslStorePassword) {
        this.sslStorePassword = sslStorePassword;
    }

    /**
     * Set Keystore file path.  This should contain the location of the JKS Keystore file
     * @param sslKeystoreFilePath Resource location of the JKS keystore
     * @since 1.2.0
     */
    public void setSslKeystoreFilePath(String sslKeystoreFilePath) {
        this.sslKeystoreFilePath = sslKeystoreFilePath;
    }

    /**
     * Set for SSL KeysStore Password.
     * @param sslKeystorePassword Password for SSL KEY Store
     * @since 1.2.0
     */
    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    /**
     * Set Trust store file path.  Location of the trust store JKS File.  This should contain
     * a file of the trusted sites that can access your secure endpoint
     * @param sslTrustStoreFilePath File path for JKS trust store
     */
    public void setSslTrustStoreFilePath(String sslTrustStoreFilePath) {
        this.sslTrustStoreFilePath = sslTrustStoreFilePath;
    }

    /**
     * Trust store password
     * @param sslTrustStorePassword Password used to access your JKS Trust store
     */
    public void setSslTrustStorePassword(String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    /**
     * Getter for SSL Protocol.  By default this is TLSv1.2
     * @return Protocol used for SSL
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public String getProtocol() {
        return protocol;
    }

    /**
     * Set Protocol for SSL
     * @param protocol Protocol used
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

}

