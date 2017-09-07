package com.onyxdevtools.relationship;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Actor;
import com.onyxdevtools.relationship.entities.Movie;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

class CascadeDeferExample extends AbstractDemo
{

    @SuppressWarnings("unchecked")
    static void demo() throws OnyxException
    {
        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
                File.separatorChar + "relationship-cascade-db.oxd";
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Populate the movie data with actors //1
        final Movie starWarsMovie = new Movie();
        starWarsMovie.setTitle("Star Wars, A new Hope");

        final Actor markHamil = new Actor();
        markHamil.setActorId(1);
        markHamil.setFirstName("Mark");
        markHamil.setLastName("Hamil");

        final Actor carrieFisher = new Actor();
        carrieFisher.setActorId(2);
        carrieFisher.setFirstName("Carrie");
        carrieFisher.setLastName("Fisher");

        // Save The movie and actors
        manager.saveEntity(markHamil);
        manager.saveEntity(carrieFisher);
        manager.saveEntity(starWarsMovie);

        // The Persistence Manager did not save the actors and associated it to the movie //2
        final Movie starWarsAfterSave1 = manager.findById(Movie.class, starWarsMovie.getMovieId());
        manager.initialize(starWarsAfterSave1, "actors");
        assertEquals("Movie should not have any associated actors", 0, starWarsAfterSave1.actors.size());

        // Associate the actors to the movie.  First add the actor unique identifiers to a set //3
        final Set actorIds = new HashSet<>();
        actorIds.add(1);
        actorIds.add(2);

        manager.saveRelationshipsForEntity(starWarsMovie, "actors", actorIds);

        // The Persistence Manager did save the actors and associated it to the movie //4
        final Movie starWarsAfterSave2 = manager.findById(Movie.class, starWarsMovie.getMovieId());
        manager.initialize(starWarsAfterSave2, "actors");
        assertEquals("Movie SHOULD have associated actors", 2, starWarsAfterSave1.actors.size());

        factory.close();
    }

}
