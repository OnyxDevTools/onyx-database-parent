package com.onyxdevtools.example.querying;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 @author  cosborn
 */
public class LazyQueryExample
{
    public LazyQueryExample()
    {
    }

    public static void demo() throws IOException
    {
        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setCredentials("onyx-user", "SavingDataIsFun!");

        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
            File.separatorChar + "querying-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a simple query to include all records
        final Query query = new Query(Player.class);

        // Invoke manager#executeLazyQuery
        final List<Player> allPlayers = manager.executeLazyQuery(query); // returns LazyQueryCollection

        // Get and print out all of the entites in the LazyQueryCollection
        for (int i = 0; i < allPlayers.size(); i++)
        {
            final Player player = allPlayers.get(i); // retreives the Player when invoked
            System.out.println(player.getFirstName() + " " + player.getLastName());
        }

        factory.close(); // close the factory so that we can use it again

    }
}
