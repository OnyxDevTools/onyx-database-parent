package com.onyx.persistence.context.impl

import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager

import java.io.File

/**
 * Schema context that defines remote resources for a database.  Use this when connecting to a remote database not to be confused with a Web REST API Database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * @see com.onyx.persistence.context.SchemaContext
 */
class RemoteSchemaContext : DefaultSchemaContext, SchemaContext {

    // region Constructors

    constructor() : super()

    constructor(contextId: String) : super(contextId, createTempDir().path)

    // endregion

    // region Override Properties

    // NOTE: THIS USES THE DEFAULT ON PURPOSE.  So that it talks to the remote server rather than system
    override var systemPersistenceManager: PersistenceManager?
        set(value) { super.systemPersistenceManager = value }
        get() = defaultRemotePersistenceManager

    // Default Remote Persistence Manager
    var defaultRemotePersistenceManager:PersistenceManager? = null

    // endregion

    companion object {
        /**
         * Helper method used to create a temporary directory.  This is needed because Android has a shit fit when
         * using Files.createTempDirectory("onx").toString();
         *
         * @return File Directory
         *
         * @since 1.3.0
         */
        @JvmStatic
        private fun createTempDir(): File {
            val baseDir = File(System.getProperty("java.io.tmpdir"))
            val baseName = System.currentTimeMillis().toString() + "-"
            val TEMP_DIR_ATTEMPTS = 10000
            (0 until TEMP_DIR_ATTEMPTS)
                    .map { File(baseDir, baseName + it) }
                    .filter { it.mkdir() }
                    .forEach { return it }
            throw IllegalStateException("Failed to create directory within "
                    + TEMP_DIR_ATTEMPTS + " attempts (tried "
                    + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')')
        }
    }
}