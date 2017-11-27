package com.onyx.persistence.factory.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.context.impl.WebSchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.persistence.manager.impl.WebPersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.request.RequestAuthenticationInterceptor
import com.onyx.serialization.CustomAnnotationInspector
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

import java.util.concurrent.Executors

/**
 * Persistence manager factory for an remote Web Service Onyx Database
 *
 *
 * This is responsible for configuring a database connections to an external database.  This is only used to connect to the REST-full web services.
 *
 * @author Tim Osborn
 * @see PersistenceManagerFactory
 *
 * @since 1.0.0
 *
 *
 * PersistenceManagerFactory factory = new WebPersistenceManagerFactory();
 * factory.setCredentials("username", "password");
 * factory.setLocation("http://23.234.13.33:8080");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 */
class WebPersistenceManagerFactory(location: String, instance: String) : EmbeddedPersistenceManagerFactory(location, instance), PersistenceManagerFactory {

    // Executor Service.  Thread pool for running requests
    private var executorService = Executors.newCachedThreadPool()

    // Rest Template
    private var restTemplate: RestTemplate? = null

    // HTTP Client
    private var httpClient: HttpClient = HttpClientBuilder.create().build()

    // Object Mapper for JSON Serialization
    private var objectMapper = ObjectMapper()

    constructor(location: String) : this(location, location)

    init {
        schemaContext = WebSchemaContext(EmbeddedPersistenceManagerFactory.DEFAULT_INSTANCE)
        // Initialize Rest Template
        this.restTemplate = RestTemplate()

        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setReadTimeout(60000)
        requestFactory.setConnectTimeout(20000)
        restTemplate!!.requestFactory = requestFactory
        restTemplate!!.interceptors = listOf<ClientHttpRequestInterceptor>(RequestAuthenticationInterceptor(this))
    }

    override val persistenceManager: PersistenceManager by lazy<PersistenceManager> {
        val manager = WebPersistenceManager(schemaContext)

        val systemPersistenceManager = EmbeddedPersistenceManager(schemaContext)
        schemaContext.systemPersistenceManager = systemPersistenceManager

        (schemaContext as WebSchemaContext).remoteEndpoint = this.databaseLocation

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.setAnnotationIntrospector(CustomAnnotationInspector())

        manager.executorService = executorService
        manager.restTemplate = restTemplate!!
        manager.httpClient = httpClient
        manager.factory = this
        manager.objectMapper = objectMapper
        return@lazy manager
    }

    /**
     * Initialize the database connection
     *
     * @throws InitializationException Failure to start database due to either invalid credentials invalid network connection
     * @since 1.0.0
     */
    @Throws(InitializationException::class)
    override fun initialize() = try {
        val query = Query(SystemEntity::class.java, QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, ""))
        persistenceManager.executeQuery<Any>(query)
        schemaContext.start()
    } catch (e: ResourceAccessException) {
        throw InitializationException(InitializationException.CONNECTION_EXCEPTION)
    } catch (e: HttpClientErrorException) {
        throw InitializationException(InitializationException.INVALID_CREDENTIALS)
    } catch (e: OnyxException) {
        throw InitializationException(InitializationException.INVALID_CREDENTIALS)
    }

    /**
     * Safe shutdown of database connection
     *
     * @since 1.0.0
     */
    override fun close() = schemaContext.shutdown()
}
