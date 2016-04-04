package com.onyxdevtools.relationship;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Episode;
import com.onyxdevtools.relationship.entities.Season;
import com.onyxdevtools.relationship.entities.Series;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by tosborn1 on 3/26/16.
 */
public class CascadeSaveExample extends AbstractDemo
{
    public static void demo() throws InitializationException, EntityException, IOException
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

        Series spongeBobSeries = new Series();
        spongeBobSeries.seriesId = "SPONGEBOB";

        Season firstSeason = new Season(1, 1999);

        spongeBobSeries.seasons = new ArrayList();
        spongeBobSeries.seasons.add(firstSeason);

        Episode pilotEpisode = new Episode();
        pilotEpisode.episodeId = "SpongeBob - S01E01";
        pilotEpisode.episodeNumber = 1;

        firstSeason.episodes = new ArrayList();
        firstSeason.episodes.add(pilotEpisode);

        // Save the series.  Notice that the cascade policy for seasons is CascadePolicy.ALL
        // and the cascade policy for episodes is CascadePolicy.SAVE
        // therefore the episode should be persisted and the entire object graph should be persisted.
        manager.saveEntity(spongeBobSeries);

        // Re-fetch the series so that we can validate a new copy of the series
        spongeBobSeries = (Series)manager.findById(Series.class, spongeBobSeries.seriesId);

        // Make sure that it has been cascaded properly
        assertNotNull("Sponge Bob Series should have been saved", spongeBobSeries);
        assertNotNull("Sponge Bob Seasons should have been saved", spongeBobSeries.seasons);
        assertEquals("The pilot episode should have been saved",  "SpongeBob - S01E01", spongeBobSeries.seasons.get(0).episodes.get(0).episodeId);

        factory.close();
    }
}
