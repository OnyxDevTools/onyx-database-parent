package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.util.List;


/**
 @author  Chris Osborn
 */
class LazyQueryExample
{

    @SuppressWarnings("unchecked")
    public static void demo() throws OnyxException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
                File.separatorChar + "querying-db.oxd";

        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a simple query to include all records
        final Query query = new Query(Player.class);

        // Invoke manager#executeLazyQuery
        final List<Player> allPlayers = manager.executeLazyQuery(query); // returns LazyQueryCollection

        // Get and print out all of the entities in the LazyQueryCollection
        for (final Player player : allPlayers) {
            System.out.println(player.getFirstName() + " " + player.getLastName());
        }

        factory.close(); // close the factory so that we can use it again

    }
}
