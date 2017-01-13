package com.onyxdevtools.example.querying;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 @author  cosborn
 */
public class DeleteQueryExample
{
    public DeleteQueryExample()
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

        // Create a query and criteria
        final QueryCriteria criteria = new QueryCriteria("active", QueryCriteriaOperator.EQUAL, false);
        final Query query = new Query(Player.class, criteria);

        List<Player> inactivePlayers = manager.executeQuery(query);

        // There should only be one inactivePlayer: Calvin Johnson
        for (final Player player : inactivePlayers)
        {
            System.out.println(player.getFirstName() + " " + player.getLastName() + " is not active.");
        }

        manager.executeDelete(query);

        // re-execute the query get the updated list
        inactivePlayers = manager.executeQuery(query);

        System.out.println("There are " + inactivePlayers.size() + " inactive players now."); // should be zero

        factory.close(); // close the factory so that we can use it again

    }
}
