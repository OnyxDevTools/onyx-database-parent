package com.onyxdevtools.relationship;

import com.onyx.persistence.collections.LazyRelationshipCollection;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Actor;
import com.onyxdevtools.relationship.entities.Episode;
import com.onyxdevtools.relationship.entities.Season;
import com.onyxdevtools.relationship.entities.Series;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by tosborn1 on 3/28/16.
 */
public class FetchPolicyExample extends AbstractDemo
{
    public static void demo() throws IOException
    {
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"relationship-cascade-save-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Populate some test data
        Series theSopranos = new Series();
        theSopranos.seriesId = "SOPRANOS";

        Season firstSeason = new Season(1, 1999);
        Season secondSeason = new Season(2, 2000);
        Season thirdSeason = new Season(3, 2001);
        Season fourthSeason = new Season(4, 2002);

        // Add the seasons to the sopranos
        theSopranos.seasons = new ArrayList();
        theSopranos.seasons.add(firstSeason);
        theSopranos.seasons.add(secondSeason);
        theSopranos.seasons.add(thirdSeason);
        theSopranos.seasons.add(fourthSeason);

        firstSeason.episodes = new ArrayList();
        firstSeason.episodes.add(new Episode("s01e01", 1));
        firstSeason.episodes.add(new Episode("s01e02", 2));
        firstSeason.episodes.add(new Episode("s01e03", 3));
        firstSeason.episodes.add(new Episode("s01e04", 4));
        firstSeason.episodes.add(new Episode("s01e05", 5));

        secondSeason.episodes = new ArrayList();
        secondSeason.episodes.add(new Episode("s02e01", 6));
        secondSeason.episodes.add(new Episode("s03e02", 7));
        secondSeason.episodes.add(new Episode("s04e03", 8));
        secondSeason.episodes.add(new Episode("s05e04", 9));
        secondSeason.episodes.add(new Episode("s06e05", 10));

        //...

        Actor james = new Actor();
        james.firstName = "James";
        james.lastName = "Gandolfini";

        Actor steve = new Actor();
        james.firstName = "Steve";
        james.lastName = "Buscemi";

        for(Season season : theSopranos.seasons)
        {
            if(season.episodes != null) {
                for (Episode episode : season.episodes) {
                    episode.actors = new ArrayList();
                    episode.actors.add(james);
                    episode.actors.add(steve);
                }
            }
        }

        // Save the Series.  Note: This will persist the entire object graph
        manager.saveEntity(theSopranos);

        // Fetch a new copy of the entity so that we can illustrate how eager relationships are fetched
        Series theSopranosCopy = (Series)manager.findById(Series.class, theSopranos.seriesId);

        // Assert seasons which is an eagerly loaded
        assertNotNull("Seasons should be populated because it is eagerly fetched", theSopranosCopy.seasons);
        assertTrue("Seasons should be fully populated as an ArrayList", (theSopranosCopy.seasons instanceof ArrayList));

        // Assert the episodes are lazily loaded
        firstSeason = theSopranosCopy.seasons.get(0);
        assertNotNull("The first seasons' episodes should not be null since it is lazily loaded", firstSeason.episodes);
        assertTrue("The first seasons' episodes should be LazyRelationshipCollection", (firstSeason.episodes instanceof LazyRelationshipCollection));

        // Notice when I reference an episode it is hydrated
        Episode episode = firstSeason.episodes.get(0);
    }
}
