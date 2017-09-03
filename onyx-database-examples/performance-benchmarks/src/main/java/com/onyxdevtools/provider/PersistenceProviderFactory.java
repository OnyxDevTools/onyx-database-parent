package com.onyxdevtools.provider;

import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyxdevtools.provider.manager.JPAPersistenceManager;
import com.onyxdevtools.provider.manager.OnyxPersistenceManager;
import com.onyxdevtools.provider.manager.ProviderPersistenceManager;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.File;

/**
 * Created by tosborn1 on 8/26/16.
 *
 * This class is a factory for returning the persistence manager so that we can use a single contract for persisting and fetching
 * data
 */
public class PersistenceProviderFactory
{
    public static ProviderPersistenceManager getPersistenceManager(DatabaseProvider databaseProvider) throws Exception
    {
        switch (databaseProvider)
        {
            // Return JPA Persistence Manager for other embedded databases
            case H2:
            case HSQL:
            case DERBY:
                final EntityManagerFactory emf = Persistence.createEntityManagerFactory(databaseProvider.getPersistenceProviderName());
                return new JPAPersistenceManager(emf, databaseProvider);
            // Onyx persistence manager
            case ONYX:
                final EmbeddedPersistenceManagerFactory embeddedPersistenceManagerFactory = new EmbeddedPersistenceManagerFactory(DatabaseProvider.DATABASE_LOCATION + File.separator + "onyx.oxd");
                embeddedPersistenceManagerFactory.initialize();
                return new OnyxPersistenceManager(embeddedPersistenceManagerFactory.getPersistenceManager());
        }

        return null;
    }

}
