package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;

import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.io.IOException;

import java.util.List;

/**
 * @author cosborn
 */
//J-
public class UpdateQueryExample
{

    public UpdateQueryExample()
    {
    }

    public static void demo() throws InitializationException, EntityException, IOException
    {
        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setCredentials("onyx-user", "SavingDataIsFun!");

        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox"
                + File.separatorChar + "querying-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);
        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a query and criteria
        final QueryCriteria positionCriteria = new QueryCriteria("position", QueryCriteriaOperator.EQUAL, "QB");
        final QueryCriteria passingCriteria = new QueryCriteria("stats.passingYards", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000);
        final QueryCriteria compoundCriteria = positionCriteria.and(passingCriteria);

        final Query query = new Query(Player.class, compoundCriteria);

        //Execute the query and see that the active attribute's value is true
        final List<Player> players = manager.executeQuery(query);
        for (final Player qb : players)
        {
            System.out.println(qb.getFirstName() + " " + qb.getLastName() + ": active=" + qb.getActive());
        }

        final QueryCriteria positionCriteria2 = new QueryCriteria("position", QueryCriteriaOperator.EQUAL, "QB");
        final QueryCriteria passingCriteria2 = new QueryCriteria("stats.passingYards", QueryCriteriaOperator.LESS_THAN_EQUAL, 5000);
        final QueryCriteria compoundCriteria2 = positionCriteria2.and(passingCriteria2);

        //Execute an update query to set active to false
        final Query updateQuery = new Query(Player.class, compoundCriteria2, new AttributeUpdate("active", false));
        manager.executeUpdate(updateQuery);

        //re-execute the query and see that the active attribute was updated to false for each record
        final List<Player> qbs = manager.executeQuery(query);
        for (final Player qb : qbs)
        {
            System.out.println(qb.getFirstName() + " " + qb.getLastName() + ": active=" + qb.getActive());
        }

        factory.close(); // close the factory so that we can use it again

    }
}

//J+
