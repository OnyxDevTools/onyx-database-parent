package com.onyx.persistence.context.impl;

import com.onyx.util.EntityClassLoader;

/**
 * The purpose of this class is to be used to browse a remote or local database.  It resolves remote database dependencies and relieves the need of not having compiled classes within your classpath.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *
 *   EmbeddedPersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(connection.getDatabaseLocation());
 *   factory.setCredentials(connection.getUsername(), connection.getPassword());
 *
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 * </code>
 * </pre>
 *
 */
public class ViewerSchemaContext extends DefaultSchemaContext
{

    /**
     * Overridden Constructor
     * @param location Remote endpoint to database or local store location
     *
     */
    @SuppressWarnings("unused")
    public ViewerSchemaContext(String contextId, String location)
    {
        super(contextId, location);
        EntityClassLoader.loadClasses(this);
    }

}
