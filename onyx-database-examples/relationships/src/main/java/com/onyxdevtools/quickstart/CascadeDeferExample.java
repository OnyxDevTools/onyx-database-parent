package com.onyxdevtools.quickstart;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.quickstart.entities.Actor;
import com.onyxdevtools.quickstart.entities.Movie;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tosborn1 on 3/26/16.
 */
public class CascadeDeferExample extends AbstractDemo {

    public static void demo() throws InitializationException, EntityException, IOException
    {
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"relationship-cascade-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Populate the movie data with actors //1
        Movie starWarsMovie = new Movie();
        starWarsMovie.title = "Star Wars, A new Hope";

        ArrayList<Actor> actors = new ArrayList<>();

        Actor markHamil = new Actor();
        markHamil.actorId = 1;
        markHamil.firstName = "Mark";
        markHamil.lastName = "Hamil";

        Actor carrieFisher = new Actor();
        carrieFisher.actorId = 2;
        carrieFisher.firstName = "Carrie";
        carrieFisher.lastName = "Fisher";

        actors.add(markHamil);
        actors.add(carrieFisher);

        // Save The movie and actors
        manager.saveEntity(markHamil);
        manager.saveEntity(carrieFisher);
        manager.saveEntity(starWarsMovie);

        // The Persistence Manager did not save the actors and associated it to the movie //2
        Movie starWarsAfterSave1 = (Movie) manager.findById(Movie.class, starWarsMovie.movieId);
        manager.initialize(starWarsAfterSave1, "actors");
        assertEquals("Movie should not have any associated actors", 0, starWarsAfterSave1.actors.size());

        // Associate the actors to the movie.  First add the actor unique identifiers to a set //3
        Set actorIds = new HashSet<>();
        actorIds.add(1);
        actorIds.add(2);

        manager.saveRelationshipsForEntity(starWarsMovie, "actors", actorIds);

        // The Persistence Manager did save the actors and associated it to the movie //4
        Movie starWarsAfterSave2 = (Movie) manager.findById(Movie.class, starWarsMovie.movieId);
        manager.initialize(starWarsAfterSave2, "actors");
        assertEquals("Movie SHOULD have associated actors", 2, starWarsAfterSave1.actors.size());

        factory.close();
    }

}
