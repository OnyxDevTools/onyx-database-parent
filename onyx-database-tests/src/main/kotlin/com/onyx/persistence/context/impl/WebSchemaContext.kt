package com.onyx.persistence.context.impl

import com.onyx.persistence.context.SchemaContext
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.store.StoreType

import java.io.IOException
import java.nio.file.Files

/**
 * Web Schema Context is used when connecting to a REST full web service database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 *
 * PersistenceManagerFactory fac = new WebPersistenceManagerFactory();
 * fac.setDatabaseLocation("http://52.13.31.322:8080");
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 *
 * @see SchemaContext
 */
class WebSchemaContext(contextId: String) : DefaultSchemaContext(contextId, generateTempLocation()), SchemaContext {

    var remoteEndpoint: String? = null

    companion object {
        private fun generateTempLocation(): String = Files.createTempDirectory("onyx.oxd").toString()
    }


    /**
     * Start the context
     * @since 1.0.0
     */
    override fun start() {
        try {
            location = Files.createTempDirectory("onyx.oxd").toString()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        super.start()
    }
}
