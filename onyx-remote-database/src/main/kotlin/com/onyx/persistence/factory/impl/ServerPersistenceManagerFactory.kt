package com.onyx.persistence.factory.impl

import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.ServerSchemaContext

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * Persistence manager factory for a server based database.
 *
 * This is a server persistence manager factory.  It is overridden so that the context
 * type is also a server schema context.  It differs due to the query caching policies.
 *
 * @author Tim Osborn
 * @since 1.3.0 Introduced as work done on query subscribers.
 *
 * <pre>
 * <code>
 *
 * PersistenceManagerFactory factory = new ServerPersistenceManagerFactory("/MyDatabaseLocation");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in embedded database
 *
 * or Kotlin ...
 *
 * val factory = ServerPersistenceManagerFactory("/MyDatabaseLocation")
 * factory.setCredentials("username", "password")
 * factory.initialize()
 *
 * val manager = factory.persistenceManager
 *
 * factory.close()
 *
 * </code
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
class ServerPersistenceManagerFactory @JvmOverloads constructor(databaseLocation: String, override var schemaContext: SchemaContext = ServerSchemaContext(databaseLocation, databaseLocation)) : EmbeddedPersistenceManagerFactory(databaseLocation, databaseLocation, schemaContext)
