package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 @author  cosborn
 */
public class ListExample
{
    public ListExample()
    {
    }

    public static void demo() throws EntityException
    {
        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setCredentials("onyx-user", "SavingDataIsFun!");

        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
            File.separatorChar + "querying-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);
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
