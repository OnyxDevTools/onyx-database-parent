package com.onyxdevtools.listener;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryListener;
import com.onyxdevtools.listener.entities.Player;

import java.io.File;

/**
 * Created by tosborn1 on 4/1/17.
 *
 * This example demonstrates how to subscribe to query changes.
 */
public class Main {

    public static void main(String[] args) throws OnyxException
    {
        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar + "query-change-listener.oxd";

        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("username", "password");
        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        Player johnElway = new Player();
        johnElway.setFirstName("John");
        johnElway.setLastName("Elway");
        johnElway.setHallOfFame(true);
        johnElway.setPosition("QB");

        manager.saveEntity(johnElway);

        // Define critiera to match position = QB & isHallOfFame = true
        QueryCriteria hallOfFameQuarterbackCriteria = new QueryCriteria("position", QueryCriteriaOperator.EQUAL, "QB")
                                                            .and(new QueryCriteria("isHallOfFame", QueryCriteriaOperator.EQUAL, true));
        final Query hallOfFameQuarterBackQuery = new Query(Player.class, hallOfFameQuarterbackCriteria);

        // Define Change listener for query
        hallOfFameQuarterBackQuery.setChangeListener(new QueryListener<Player>()
        {
            @Override
            public void onItemUpdated(Player item) {
                System.out.println("Player " + item.getFirstName() + " " + item.getLastName() + " has been updated!");
            }

            @Override
            public void onItemAdded(Player item) {
                System.out.println("Player " + item.getFirstName() + " " + item.getLastName() + " has been added!");
            }

            @Override
            public void onItemRemoved(Player item) {
                System.out.println("Player " + item.getFirstName() + " " + item.getLastName() + " has been removed!");
            }
        });

        // Execute the query to register the change listener
        manager.executeQuery(hallOfFameQuarterBackQuery);

        // Add some data to see how the change listener reacts
        Player tomBrady = new Player();
        tomBrady.setFirstName("Tom");
        tomBrady.setLastName("Brady");
        tomBrady.setPosition("QB");
        tomBrady.setHallOfFame(true);

        // On Item Added should be fired
        manager.saveEntity(tomBrady);

        // He was ejected from the Hall of Fame because of Deflate gate.
        tomBrady.setHallOfFame(false);
        // See that he was removed from the result set and teh onItemRemoved was fired.
        manager.saveEntity(tomBrady);

        // Modify an entity and save, the onItemUpdated should be fired
        johnElway.setDidNotCheat(true);
        manager.saveEntity(johnElway);

        // Remove the change listener when done
        manager.removeChangeListener(hallOfFameQuarterBackQuery);

        factory.close(); //Close the embedded database after you're done with it
    }

}
