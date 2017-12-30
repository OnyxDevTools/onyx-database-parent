package com.onyxdevtools.example.querying;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.example.querying.entities.Stats;

import java.io.File;
import java.util.List;
import java.util.Map;

class GroupByExample {

    @SuppressWarnings("unchecked")
    public static void demo() throws OnyxException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
                File.separatorChar + "querying-db.oxd";

        // get an instance of the persistenceManager
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.initialize();

        groupStatsByRushingYards(factory.getPersistenceManager());
        getLeadingRusher(factory.getPersistenceManager());

        factory.close();
    }

    private static void groupStatsByRushingYards(PersistenceManager manager) throws OnyxException {

        QueryCriteria criteria = new QueryCriteria("rushingYards", QueryCriteriaOperator.NOT_EQUAL, 0);
        Query query = new Query(Stats.class);
        query.setCriteria(criteria);
        query.groupBy("rushingYards");
        query.selections("count(player.playerId)", "rushingYards");
        query.setDistinct(true);

        List<Map<String, Object>> results = manager.executeQuery(query);

        results.forEach(stringObjectMap -> System.out.println("There were " + stringObjectMap.get("count(player.playerId)") + " players with " + stringObjectMap.get("rushingYards") + " rushing yards"));
    }

    private static void getLeadingRusher(PersistenceManager manager) throws OnyxException {
        QueryCriteria criteria = new QueryCriteria("rushingYards", QueryCriteriaOperator.NOT_EQUAL, 0);
        Query query = new Query(Stats.class);
        query.setCriteria(criteria);
        query.selections("max(rushingYards)", "player.firstName", "player.lastName");

        List<Map<String, Object>> results = manager.executeQuery(query);

        System.out.println("The max rushing yards was " + results.get(0).get("max(rushingYards)") + " for " + results.get(0).get("player.firstName") + " " + results.get(0).get("player.lastName"));
    }
}
