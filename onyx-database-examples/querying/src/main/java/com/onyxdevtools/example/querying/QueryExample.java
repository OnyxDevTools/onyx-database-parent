package com.onyxdevtools.example.querying;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;

import com.onyxdevtools.example.querying.entities.Player;

import java.io.File;
import java.io.IOException;

import java.util.List;


/**
 @author  cosborn
 */
public class QueryExample
{
    public QueryExample()
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

        // Create a query and criteria
        final QueryCriteria criteria = new QueryCriteria("position", QueryCriteriaOperator.EQUAL, "QB");
        criteria.and("firstName", QueryCriteriaOperator.STARTS_WITH, "D");

        final Query query = new Query(Player.class, criteria, new QueryOrder("lastName"));
        // query.setCriteria(criteria); or you can set the critiera after construction

        final List<Player> quarterbacks = manager.executeQuery(query);

        for (final Player qb : quarterbacks)
        {
            System.out.println(qb.getLastName() + "," + qb.getFirstName());
        }

        factory.close(); // close the factory so that we can use it again

    }
}
