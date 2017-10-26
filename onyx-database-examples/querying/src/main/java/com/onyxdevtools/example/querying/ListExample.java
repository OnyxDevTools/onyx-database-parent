package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.util.List;


/**
 @author  Chris Osborn
 */
class ListExample
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

        final List<Player> players = manager.list(Player.class);

        for (final Player player : players)
        {
            System.out.println(player.getLastName() + ", " + player.getFirstName());
        }

        factory.close(); // close the factory so that we can use it again

    }
}
