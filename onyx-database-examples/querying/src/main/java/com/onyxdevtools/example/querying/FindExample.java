package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.example.querying.entities.League;

import java.io.File;


/**
 @author  cosborn
 */
class FindExample
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

        // Create a new entity variable to hold the entity
        League league = new League();
        league.setName("NFL"); // set the id

        // invoke the PersistenceManager#find method
        league = manager.find(league);

        // Confirm that the other fields were populated
        System.out.println("The description of the league that was found is: '" + league.getDescription() + "'");

        factory.close(); // close the factory so that we can use it again

    }
}
