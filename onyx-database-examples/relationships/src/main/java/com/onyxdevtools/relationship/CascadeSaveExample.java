package com.onyxdevtools.relationship;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Episode;
import com.onyxdevtools.relationship.entities.Season;
import com.onyxdevtools.relationship.entities.Series;

import java.io.File;
import java.util.ArrayList;

class CascadeSaveExample extends AbstractDemo
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

        Series spongeBobSeries = new Series();
        spongeBobSeries.setSeriesId("SPONGEBOB");

        Season firstSeason = new Season(1, 1999);

        spongeBobSeries.setSeasons(new ArrayList<>());
        spongeBobSeries.getSeasons().add(firstSeason);

        Episode pilotEpisode = new Episode();
        pilotEpisode.setEpisodeId("SpongeBob - S01E01");
        pilotEpisode.setEpisodeNumber(1);

        firstSeason.setEpisodes(new ArrayList<>());
        firstSeason.getEpisodes().add(pilotEpisode);

        // Save the series.  Notice that the cascade policy for seasons is CascadePolicy.ALL
        // and the cascade policy for episodes is CascadePolicy.SAVE
        // therefore the episode should be persisted and the entire object graph should be persisted.
        manager.saveEntity(spongeBobSeries);

        // Re-fetch the series so that we can validate a new copy of the series
        spongeBobSeries = manager.findById(Series.class, spongeBobSeries.getSeriesId());

        // Make sure that it has been cascaded properly
        assertNotNull("Sponge Bob Series should have been saved", spongeBobSeries);
        assertNotNull("Sponge Bob Seasons should have been saved", spongeBobSeries.getSeasons());
        assertEquals("The pilot episode should have been saved",  "SpongeBob - S01E01", spongeBobSeries.getSeasons().get(0).getEpisodes().get(0).getEpisodeId());

        factory.close();
    }
}
