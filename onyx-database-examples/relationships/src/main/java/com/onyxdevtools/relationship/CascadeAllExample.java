package com.onyxdevtools.relationship;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Season;
import com.onyxdevtools.relationship.entities.Series;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by tosborn1 on 3/26/16.
 */
public class CascadeAllExample extends AbstractDemo
{
    public static void demo() throws InitializationException, EntityException, IOException
    {
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"relationship-cascade-all-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        Series spongeBobSeries = new Series();
        spongeBobSeries.seriesId = "SPONGEBOB";

        Season firstSeason = new Season(1, 1999);
        Season secondSeason = new Season(2, 2000);
        Season thirdSeason = new Season(3, 2001);
        Season fourthSeason = new Season(4, 2002);
        Season fifthSeason = new Season(5, 2003);

        spongeBobSeries.seasons = new ArrayList();
        spongeBobSeries.seasons.add(firstSeason);
        spongeBobSeries.seasons.add(secondSeason);
        spongeBobSeries.seasons.add(thirdSeason);
        spongeBobSeries.seasons.add(fourthSeason);
        spongeBobSeries.seasons.add(fifthSeason);

        // Save the series.  Notice that the cascade policy for seasons is CascadePolicy.ALL
        manager.saveEntity(spongeBobSeries);

        // Re-fetch the series so that we can validate a new copy of the series
        spongeBobSeries = (Series)manager.findById(Series.class, spongeBobSeries.seriesId);

        // Make sure that it has been cascaded properly
        assertNotNull("Sponge Bob Series should have been saved", spongeBobSeries);
        assertNotNull("Sponge Bob Seasons should have been saved", spongeBobSeries.seasons);
        assertEquals("There should have been 5 seasons saved", spongeBobSeries.seasons.size(), 5);

        // Lets remove a season just to see if the cascade delete is working properly
        spongeBobSeries.seasons.remove(4); // Remove season 5

        // Save the series again
        manager.saveEntity(spongeBobSeries);

        // Re-fetch the series so that we can validate a new copy of the series
        spongeBobSeries = (Series)manager.findById(Series.class, spongeBobSeries.seriesId);

        // Make sure that it has been cascaded properly
        assertEquals("There should have been 4 seasons saved", spongeBobSeries.seasons.size(), 4);

        factory.close();
    }


}
