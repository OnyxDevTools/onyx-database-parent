package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.util.List;


/**
 @author  cosborn
 */
class CompoundQueryExample
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

        // Create a query and criteria
        final QueryCriteria positionCriteria = new QueryCriteria("position", QueryCriteriaOperator.EQUAL, "RB");
        final QueryCriteria rushingYardsCriteria = new QueryCriteria("stats.rushingYards", QueryCriteriaOperator.GREATER_THAN_EQUAL, 1000);
        final QueryCriteria compoundCriteria = positionCriteria.and(rushingYardsCriteria);

        final Query query = new Query(Player.class, compoundCriteria, new QueryOrder("stats.rushingYards", false));

        final List<Player> runningBacks = manager.executeQuery(query);

        for (final Player rb : runningBacks)
        {
            System.out.println(rb.getFirstName() + " " + rb.getLastName());
        }

        System.out.println("* Only " + query.getResultsCount() + " running backs reached 1000 yards in 2015.");

        factory.close(); // close the factory so that we can use it again

    }
}
