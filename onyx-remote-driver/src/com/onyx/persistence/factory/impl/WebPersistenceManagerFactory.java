package com.onyx.persistence.factory.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.SingletonException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.manager.impl.WebPersistenceManager;
import com.onyx.persistence.context.impl.WebSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.request.RequestAuthenticationInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistence manager factory for an remote Web Service Onyx Database
 *
 * This is responsible for configuring a database connections to an external database.  This is only used to connect to the RESTful web services.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new WebPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("http://23.234.13.33:8080");
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
public class WebPersistenceManagerFactory extends EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory
{

    // Executor Service.  Thread pool for running requests
    protected ExecutorService executorService = Executors.newCachedThreadPool();

    // Rest Template
    protected RestTemplate restTemplate = null;

    // HTTP Client
    protected HttpClient httpClient = HttpClientBuilder.create().build();

    // Object Mapper for JSON Serialization
    protected ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Default Constructor
     */
    public WebPersistenceManagerFactory()
    {
        super();
        this.context = new WebSchemaContext(DEFAULT_INSTANCE);
        // Initialize Rest Template
        this.restTemplate = new RestTemplate();

        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(60000);
        requestFactory.setConnectTimeout(20000);
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.setInterceptors(Collections.singletonList(new RequestAuthenticationInterceptor(this)));
    }

    /**
     * Getter for persistence manager
     *
     * @since 1.0.0
     * @return Instantiated Persistence Manager
     */
    public PersistenceManager getPersistenceManager() {

        if(persistenceManager == null)
        {
            this.persistenceManager = new WebPersistenceManager();

            WebPersistenceManager tmpPersistenceManager = (WebPersistenceManager)this.persistenceManager;

            final EmbeddedPersistenceManager systemPersistenceManager;
            try {
                systemPersistenceManager = new EmbeddedPersistenceManager();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            systemPersistenceManager.setContext(context);
            context.setSystemPersistenceManager(systemPersistenceManager);
            ((WebSchemaContext)context).setRemoteEndpoint(this.location);

            tmpPersistenceManager.setContext(context);
            tmpPersistenceManager.setExecutorService(executorService);
            tmpPersistenceManager.setRestTemplate(restTemplate);
            tmpPersistenceManager.setHttpClient(httpClient);
            tmpPersistenceManager.setFactory(this);
            tmpPersistenceManager.setObjectMapper(objectMapper);
        }
        return persistenceManager;
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
        try
        {
            Query query = new Query(SystemEntity.class, new QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, ""));
            getPersistenceManager().executeQuery(query);
            context.start();
        }
        catch (ResourceAccessException e)
        {
            throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
        }
        catch (HttpClientErrorException e)
        {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }
        catch (EntityException e)
        {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }
        catch (RemoteException e)
        {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }
    }

    /**
     * Safe shutdown of database connection
     * @since 1.0.0
     */
    @Override
    public void close() throws IOException, SingletonException
    {
        context.shutdown();
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
        if(context != null)
            ((WebSchemaContext)context).setRemoteEndpoint(location);
    }
}
