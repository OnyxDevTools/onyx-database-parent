package com.onyx.factory.impl;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIdentifier;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.context.impl.WebSchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.manager.impl.WebPersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.request.RequestAuthenticationInterceptor;
import com.onyx.serialization.CustomAnnotationInspector;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistence manager factory for an remote Web Service Onyx Database
 * <p>
 * This is responsible for configuring a database connections to an external database.  This is only used to connect to the RESTful web services.
 *
 * @author Tim Osborn
 * @see PersistenceManagerFactory
 * @since 1.0.0
 * <p>
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
 */
public class WebPersistenceManagerFactory extends EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory {

    // Executor Service.  Thread pool for running requests
    protected ExecutorService executorService = Executors.newCachedThreadPool();

    // Rest Template
    protected RestTemplate restTemplate = null;

    // HTTP Client
    protected HttpClient httpClient = HttpClientBuilder.create().build();

    // Object Mapper for JSON Serialization
    protected ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unused")
    public WebPersistenceManagerFactory(String location) {
        this(location, location);
    }

    /**
     * Default Constructor
     */
    public WebPersistenceManagerFactory(String location, String instance) {
        super(location, instance);
        setSchemaContext(new WebSchemaContext(DEFAULT_INSTANCE));
        // Initialize Rest Template
        this.restTemplate = new RestTemplate();

        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(60000);
        requestFactory.setConnectTimeout(20000);
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.setInterceptors(Collections.singletonList(new RequestAuthenticationInterceptor(this)));
    }

    private PersistenceManager persistenceManager;
    /**
     * Getter for persistence manager
     *
     * @return Instantiated Persistence Manager
     * @since 1.0.0
     */
    public PersistenceManager getPersistenceManager() {

        if (persistenceManager == null) {
            this.persistenceManager = new WebPersistenceManager(getSchemaContext());

            WebPersistenceManager tmpPersistenceManager = (WebPersistenceManager) this.persistenceManager;

            final EmbeddedPersistenceManager systemPersistenceManager = new EmbeddedPersistenceManager(null);
            systemPersistenceManager.setContext(getSchemaContext());
            getSchemaContext().setSystemPersistenceManager(systemPersistenceManager);

            ((WebSchemaContext) getSchemaContext()).setRemoteEndpoint(this.getDatabaseLocation());

            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.setAnnotationIntrospector(new CustomAnnotationInspector());

            tmpPersistenceManager.setContext(getSchemaContext());
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
     * @throws InitializationException Failure to start database due to either invalid credentials invalid network connection
     * @since 1.0.0
     */
    @Override
    public void initialize() throws InitializationException {
        try {
            Query query = new Query(SystemEntity.class, new QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, ""));
            getPersistenceManager().executeQuery(query);
            getSchemaContext().start();
        } catch (ResourceAccessException e) {
            throw new InitializationException(InitializationException.CONNECTION_EXCEPTION);
        } catch (HttpClientErrorException | OnyxException e) {
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        }
    }

    /**
     * Safe shutdown of database connection
     *
     * @since 1.0.0
     */
    @Override
    public void close() {
        getSchemaContext().shutdown();
    }

    public interface BidirectionalDefinition {

        @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = SystemEntity.class)
        public interface ParentDef {};

        @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = SystemIdentifier.class)
        public interface ChildDef {};

    }
}
