package com.onyx.persistence.factory.impl

import com.onyx.exception.InitializationException
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.CacheSchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import java.io.File

/**
 * Persistence manager factory for an in memory database.  This utilizes off heap memory buffers.  This is responsible for configuring a database that does not persist to disk.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 * CacheManagerFactory factory = new CacheManagerFactory();
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 * or... Kotlin
 *
 * val factory = CacheManagerFactory()
 * factory.initialize()
 *
 * val manager - factory.persistenceManager
 *
 * factory.close
 *
 * </code
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
open class CacheManagerFactory @JvmOverloads constructor(instance: String = DEFAULT_INSTANCE) : EmbeddedPersistenceManagerFactory(createTemporaryMetadataLocation(), instance), PersistenceManagerFactory {

    override var schemaContext: SchemaContext = CacheSchemaContext(instance, this.databaseLocation)

    /**
     * Initialize the in memory database
     *
     * @throws InitializationException Only one instance of the in memory database can be instantiated per process
     */
    @Throws(InitializationException::class)
    override fun initialize() {
        this.persistenceManager
        schemaContext.start()
    }

    companion object {
        internal fun createTemporaryMetadataLocation(): String = File.createTempFile("temp", java.lang.Long.toString(System.nanoTime())).path
    }
}
