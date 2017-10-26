package com.onyxdevtools.provider;

import java.io.File;


/**
 * Created by Tim Osborn on 8/26/16.
 *
 * This enum represents the different databases the test can support.  For now this includes embedded databases within java
 * that support JPA.  This is set to expand in the future.
 */
public enum  DatabaseProvider {

    H2("h2"),
    ONYX("onyx"),
    HSQL("hsqldb"),
    DERBY("derby");

    private final String persistenceProviderName;

    public static final String DATABASE_LOCATION = System.getProperty("user.home") + File.separator + "OnyxBenchmark";

    /**
     * Constructor with provider name
     * @param persistenceProviderName The persistence unit defined in the persistence.xml file
     */
    DatabaseProvider(String persistenceProviderName)
    {
        this.persistenceProviderName = persistenceProviderName;
    }

    /**
     * Getter for Persistence Provider Name
     * @return The persistence unit defined in the persistence.xml file
     */
    public String getPersistenceProviderName()
    {
        return persistenceProviderName;
    }

}
