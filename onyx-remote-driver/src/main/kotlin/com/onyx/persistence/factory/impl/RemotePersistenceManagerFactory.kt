package com.onyx.persistence.factory.impl

import com.onyx.diskmap.store.StoreType
import com.onyx.network.auth.AuthenticationManager
import com.onyx.exception.ConnectionFailedException
import com.onyx.network.rmi.OnyxRMIClient
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.extension.common.catchAll
import com.onyx.lang.property.mutableLazy
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.RemoteSchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.persistence.manager.impl.RemotePersistenceManager

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
 * PersistenceManagerFactory factory = new RemotePersistenceManagerFactory("onx://23.234.13.33:8080");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 * or... Kotlin
 *
 * val factory = RemotePersistenceManagerFactory("onx://23.234.13.33:8080")
 * factory.setCredentials("username", "password")
 * factory.initialize(0
 *
 * val manager = factory.persistenceManager
 *
 * factory.close()
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 *
 * Tim Osborn, 02/13/2017 - This was augmented to use the new RMI Socket Server.  It has since been optimized
 */
open class RemotePersistenceManagerFactory @JvmOverloads constructor(databaseLocation: String, instance: String = databaseLocation, override var schemaContext: SchemaContext = RemoteSchemaContext(instance)) : EmbeddedPersistenceManagerFactory(databaseLocation, instance, schemaContext), PersistenceManagerFactory {

    override var storeType: StoreType = StoreType.MEMORY_MAPPED_FILE

    var keepAlive = true

    // region Private Values

    private val onyxRMIClient: OnyxRMIClient by lazy { OnyxRMIClient().apply { this.keepAlive = keepAlive } }

    // endregion

    // region Override Values

    // Set Persistence Manager will cause it to re-initialize next time
    override var persistenceManager: PersistenceManager by mutableLazy {

        val proxy = onyxRMIClient.getRemoteObject(Services.PERSISTENCE_MANAGER_SERVICE.serviceId, PersistenceManager::class.java) as PersistenceManager
        val manager = RemotePersistenceManager(proxy, onyxRMIClient)
        manager.context = schemaContext

        val systemPersistenceManager: EmbeddedPersistenceManager

        // Since the connection remains persistent and open, we do not want to reset the system persistence manager.  That should have
        // remained open and valid through any network blip.
        if (schemaContext.systemPersistenceManager == null) {
            systemPersistenceManager = EmbeddedPersistenceManager(schemaContext)
            schemaContext.systemPersistenceManager = systemPersistenceManager
        }

        (schemaContext as RemoteSchemaContext).defaultRemotePersistenceManager = manager
        return@mutableLazy manager
    }

    // remove the onx:// prefix
    override val databaseLocation: String
        get() = super.databaseLocation.replaceFirst("onx://".toRegex(), "")

    // endregion

    // region Private Methods

    /**
     * Connect to the remote database server
     *
     * @since 1.1.0
     * @throws InitializationException Exception occurred while connecting
     */
    @Throws(InitializationException::class)
    private fun connect() {

        val locationParts = databaseLocation.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val port = locationParts[locationParts.size - 1]
        val host = databaseLocation.replace(":$port", "")

        onyxRMIClient.setCredentials(this.user, this.password)
//        copySSLPeerTo(onyxRMIClient)

        val authenticationManager = onyxRMIClient.getRemoteObject(Services.AUTHENTICATION_MANAGER_SERVICE.serviceId, AuthenticationManager::class.java) as AuthenticationManager
        onyxRMIClient.authenticationManager = authenticationManager

        try {
            onyxRMIClient.connect(host, Integer.valueOf(port))
        } catch (e: ConnectionFailedException) {
            throw InitializationException(InitializationException.CONNECTION_EXCEPTION)
        }
    }

    // endregion

    // region Override Methods

    /**
     * Initialize the database connection
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials invalid network connection
     */
    @Throws(InitializationException::class)
    override fun initialize() = try {
        connect()

        // Verify Connection by getting System Entities
        persistenceManager.findById<SystemEntity>(SystemEntity::class.java, 1)
        schemaContext.storeType = storeType
        schemaContext.maxCardinality = maxCardinality
        schemaContext.start()
    } catch (e: OnyxException) {
        if(e is InitializationException && e.message != InitializationException.INVALID_CREDENTIALS) {
            catchAll {
                persistenceManager
                schemaContext.start()
            }
        }
        throw e
    }

    /**
     * Safe shutdown of database connection
     *
     * @since 1.0.0
     */
    override fun close() {
        onyxRMIClient.close()
        schemaContext.shutdown()
        schemaContext = UNINITIALIZED_SCHEMA_CONTEXT // Reset the lazy initializer
        persistenceManager = UNINITIALIZED_PERSISTENCE_MANAGER // Reset the lazy initializer
    }

    // endregion

    @Suppress("unused")
    fun <T> service(name: String, type: Class<*>): T {
        @Suppress("UNCHECKED_CAST")
        return onyxRMIClient.getRemoteObject(name, type) as? T ?: throw Exception("Service not found")
    }

    companion object {

        enum class Services(val serviceId:String) {
            PERSISTENCE_MANAGER_SERVICE("1"),
            AUTHENTICATION_MANAGER_SERVICE("2")
        }

        // Placeholder to indicate the persistence manager is uninitialized
        val UNINITIALIZED_PERSISTENCE_MANAGER = RemotePersistenceManager()

        // Placeholder for schema context that is not initialized.  This is a workaround
        // so we can have it as non nullable
        val UNINITIALIZED_SCHEMA_CONTEXT: RemoteSchemaContext = RemoteSchemaContext()
    }


}