package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.example.querying.entities.Season;

import java.io.File;


/**
 @author  cosborn
 */
class FindByIdExample
{

    public static void demo() throws OnyxException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
                File.separatorChar + "querying-db.oxd";

        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Retreived the 2015 season using the PersistenceManager#findById method
        final Season season = manager.findById(Season.class, 2015);

        // Confirm that season was retreived
        if (season != null)
        {
            System.out.println("The season has " + season.getConferences().size() + " conferences!");
        }

        factory.close(); // close the factory so that we can use it again

    }
}
