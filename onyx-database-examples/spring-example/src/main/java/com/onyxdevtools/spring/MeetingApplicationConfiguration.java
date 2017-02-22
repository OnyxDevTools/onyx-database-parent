package com.onyxdevtools.spring;

import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.CacheManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings("unused")
public class MeetingApplicationConfiguration
{
    /**
     * Persistence Manager factory.  This determines your database connection.  This would have the same usage if
     * you were connecting to an embedded or remote database.  The only difference would be the factory type.
     *
     * @return Initialized Persistence Manager Factory
     */
    @Bean
    protected PersistenceManagerFactory persistenceManagerFactory() throws InitializationException
    {
        CacheManagerFactory cacheManagerFactory = new CacheManagerFactory();
        cacheManagerFactory.initialize();
        return cacheManagerFactory;
    }

    /**
     * Persistence Manager singleton
     *
     * @param factory Connection factory used to create the persistence manager
     * @return Initiated persistence manager used to interface with Onyx Database
     */
    @Bean
    protected PersistenceManager persistenceManager(PersistenceManagerFactory factory)
    {
        return factory.getPersistenceManager();
    }
}