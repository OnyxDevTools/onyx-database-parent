package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryOrder;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.util.Arrays;
import java.util.List;


/**
 @author  cosborn
 */
class SortingAndPagingExample
{

    @SuppressWarnings("unchecked")
    public static void demo() throws EntityException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
                File.separatorChar + "querying-db.oxd";

        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a list of query orders
        final List<QueryOrder> queryOrders = Arrays.asList(new QueryOrder("lastName", true), new QueryOrder("firstName", true));

        // Create a query
        final Query query = new Query(Player.class, queryOrders);

        // Set the firstRow and maxResults to implement paging
        query.setFirstRow(0);
        query.setMaxResults(10);

        // Query for the first page (rows 0-9, records 1-10)
        final List<Player> page1 = manager.executeQuery(query);

        // Print the first page results
        System.out.println("\nPage 1:");

        for (final Player player : page1)
        {
            System.out.println(player.getLastName() + ", " + player.getFirstName());
        }

        // Set the firstRow and maxResults to retrieve the second page
        query.setFirstRow(query.getFirstRow() + query.getMaxResults());

        // Query for the second page (rows 10-19, records 11-20)
        final List<Player> page2 = manager.executeQuery(query);

        // Print the second page results
        System.out.println("\nPage 2:");

        for (final Player player : page2)
        {
            System.out.println(player.getLastName() + ", " + player.getFirstName());
        }

        factory.close(); // close the factory so that we can use it again

    }
}
