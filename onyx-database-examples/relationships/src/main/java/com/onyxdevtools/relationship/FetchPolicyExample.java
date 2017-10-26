package com.onyxdevtools.relationship;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.collections.LazyRelationshipCollection;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Actor;
import com.onyxdevtools.relationship.entities.Episode;
import com.onyxdevtools.relationship.entities.Season;
import com.onyxdevtools.relationship.entities.Series;

import java.io.File;
import java.util.ArrayList;

@SuppressWarnings("ALL")
class FetchPolicyExample extends AbstractDemo
{
    static void demo() throws OnyxException
    {
        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"relationship-cascade-save-db.oxd";

        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Populate some test data
        Series theSopranos = new Series();
        theSopranos.setSeriesId("SOPRANOS");

        Season firstSeason = new Season(1, 1999);
        Season secondSeason = new Season(2, 2000);
        Season thirdSeason = new Season(3, 2001);
        Season fourthSeason = new Season(4, 2002);

        // Add the seasons to the sopranos
        theSopranos.setSeasons(new ArrayList<>());
        theSopranos.getSeasons().add(firstSeason);
        theSopranos.getSeasons().add(secondSeason);
        theSopranos.getSeasons().add(thirdSeason);
        theSopranos.getSeasons().add(fourthSeason);

        firstSeason.setEpisodes(new ArrayList<>());
        firstSeason.getEpisodes().add(new Episode("s01e01", 1));
        firstSeason.getEpisodes().add(new Episode("s01e02", 2));
        firstSeason.getEpisodes().add(new Episode("s01e03", 3));
        firstSeason.getEpisodes().add(new Episode("s01e04", 4));
        firstSeason.getEpisodes().add(new Episode("s01e05", 5));

        secondSeason.setEpisodes(new ArrayList<>());
        secondSeason.getEpisodes().add(new Episode("s02e01", 6));
        secondSeason.getEpisodes().add(new Episode("s03e02", 7));
        secondSeason.getEpisodes().add(new Episode("s04e03", 8));
        secondSeason.getEpisodes().add(new Episode("s05e04", 9));
        secondSeason.getEpisodes().add(new Episode("s06e05", 10));

        //...

        Actor james = new Actor();
        james.setFirstName("James");
        james.setLastName("Gandolfini");

        Actor steve = new Actor();
        james.setFirstName("Steve");
        james.setLastName("Buscemi");

        theSopranos.getSeasons().stream().filter(season -> season.getEpisodes() != null).forEach(season -> {
            for (Episode episode : season.getEpisodes()) {
                episode.setActors(new ArrayList<>());
                episode.getActors().add(james);
                episode.getActors().add(steve);
            }
        });

        // Save the Series.  Note: This will persist the entire object graph
        manager.saveEntity(theSopranos);

        // Fetch a new copy of the entity so that we can illustrate how eager relationships are fetched
        Series theSopranosCopy = manager.findById(Series.class, theSopranos.getSeriesId());

        // Assert seasons which is an eagerly loaded
        assert theSopranosCopy != null;
        assertNotNull("Seasons should be populated because it is eagerly fetched", theSopranosCopy.getSeasons());
        assertTrue("Seasons should be fully populated as an ArrayList", (theSopranosCopy.getSeasons() instanceof ArrayList));

        // Assert the episodes are lazily loaded
        firstSeason = theSopranosCopy.getSeasons().get(0);
        assertNotNull("The first seasons' episodes should not be null since it is lazily loaded", firstSeason.getEpisodes());
        assertTrue("The first seasons' episodes should be LazyRelationshipCollection", (firstSeason.getEpisodes() instanceof LazyRelationshipCollection));

        // Notice when I reference an episode it is hydrated
        Episode episode = firstSeason.getEpisodes().get(0);
        assertTrue("Actors should be hydrated", episode.getActors().size() > 0);
    }
}
