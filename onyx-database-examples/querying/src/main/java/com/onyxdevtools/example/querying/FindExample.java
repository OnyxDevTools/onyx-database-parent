package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;

import com.onyxdevtools.example.querying.entities.League;
import com.onyxdevtools.example.querying.entities.Season;

import java.io.File;
import java.io.IOException;


/**
 @author  cosborn
 */
public class FindExample
{
    public FindExample()
    {
    }

    public static void demo() throws InitializationException, EntityException, IOException
    {
        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setCredentials("onyx-user", "SavingDataIsFun!");

        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
            File.separatorChar + "querying-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a new entity variable to hold the entity
        League league = new League();
        league.setName("NFL"); // set the id

        // invoke the PersistenceManager#find method
        league = (League) manager.find(league);

        // Confirm that the other fields were populated
        System.out.println("The description of the league that was found is: '" + league.getDescription() + "'");

        // Or you can find an entity using the PersistenceManager#findById method
        final Season season = (Season) manager.findById(Season.class, 2015);

        // Confirm that season was retreived
        System.out.println("The season has " + season.getConferences().size() + " conferences!");

        factory.close(); // close the factory so that we can use it again

    }
}
