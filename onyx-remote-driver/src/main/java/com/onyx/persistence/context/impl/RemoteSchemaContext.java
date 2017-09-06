package com.onyx.persistence.context.impl;

import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.impl.DefaultQueryCacheController;

import java.io.File;

/**
 * Schema context that defines remote resources for a database.  Use this when connecting to a remote database not to be confused with a Web REST API Database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 * PersistenceManagerFactory fac = new RemotePersistenceManagerFactory();
 * fac.setDatabaseLocation("onx://52.13.31.322:8080");
 * fac.setCredentials("username", "password");
 * fac.initialize();
 *
 * PersistenceManager manager = fac.getPersistenceManager();
 *
 * fac.close();
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.context.SchemaContext
 */
public class RemoteSchemaContext extends DefaultSchemaContext implements SchemaContext
{
    private PersistenceManager defaultRemotePersistenceManager = null;

    public RemoteSchemaContext() {
        super();
    }

    /**
     * Default Constructor
     * @since 1.0.0
     */
    public RemoteSchemaContext(String contextId)
    {
        super(contextId, createTempDir().getPath());
    }

    /**
     * Helper method used to create a temporary directory.  This is needed because Android has a shit fit when
     * using Files.createTempDirectory("onx").toString();
     *
     * @return File
     *
     * @since 1.3.0
     */
    private static File createTempDir() {
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        String baseName = System.currentTimeMillis() + "-";
        int TEMP_DIR_ATTEMPTS = 10000;
        for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
            File tempDir = new File(baseDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory within "
                + TEMP_DIR_ATTEMPTS + " attempts (tried "
                + baseName + "0 to " + baseName + (TEMP_DIR_ATTEMPTS - 1) + ')');
    }

    /**
     * Set Default Remote Persistence Manager
     * This is not meant to be a public API and is set by the factory
     * @since 1.0.0
     * @param defaultPersistenceManager Default Persistence manager for System Entities
     */
    public void setDefaultRemotePersistenceManager(PersistenceManager defaultPersistenceManager)
    {
        this.defaultRemotePersistenceManager = defaultPersistenceManager;
    }

    /**
     * Override to use the remote persistence manager.  This is used by
     *
     * @see com.onyx.persistence.collections.LazyRelationshipCollection
     * @see com.onyx.persistence.collections.LazyQueryCollection
     *
     * @since 1.0.0
     * @return System entity persistence manager
     */
    @Override
    public PersistenceManager getSystemPersistenceManager()
    {
        // NOTE: THIS USES THE DEFAULT ON PURPOSE.  So that it talks to the remote server rather than system
        return defaultRemotePersistenceManager;
    }

}
