package com.onyxdevtools.relationship;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Actor;
import com.onyxdevtools.relationship.entities.Movie;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tosborn1 on 3/28/16.
 */
public class ManyToManyExample extends AbstractDemo
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

        PersistenceManager persistenceManager = factory.getPersistenceManager();

        // Define Harrison Ford Actor
        Actor harrisonFordActor = new Actor();
        harrisonFordActor.setFirstName("Harrison");
        harrisonFordActor.setLastName("Ford");

        // Define Mark Hamill Actor
        Actor markHamillActor = new Actor();
        markHamillActor.setFirstName("Mark");
        markHamillActor.setLastName("Hamill");

        // Create Star Wars Movie
        Movie starWarsMovie = new Movie();
        starWarsMovie.setTitle("A New Hope");

        // Create Indiana Jones Movie
        Movie indianaJonesMovie = new Movie();
        indianaJonesMovie.setTitle("Raiders of the Lost Ark");

        // Set relationship for Star Wars Movie to its actors
        List<Actor> starWarsActors = new ArrayList<>();
        starWarsActors.add(harrisonFordActor);
        starWarsActors.add(markHamillActor);
        starWarsMovie.setActors(starWarsActors);

        // Set relationship for Star Wars Movie to its actors
        List<Actor> indianaJonesActors = new ArrayList<>();
        indianaJonesActors.add(harrisonFordActor);
        indianaJonesMovie.setActors(indianaJonesActors);

        // Optional.  You do not need to set the inverse relationships if you define CascadePolicy.SAVE or CascadePolicy.ALL
        List<Movie> harrisonFordsMovies = new ArrayList<>();
        harrisonFordsMovies.add(starWarsMovie);
        harrisonFordsMovies.add(indianaJonesMovie);
        harrisonFordActor.setMovies(harrisonFordsMovies);

        List<Movie> markHamillMovies = new ArrayList<>();
        markHamillMovies.add(starWarsMovie);
        markHamillActor.setMovies(markHamillMovies);

        // Persist the movies
        persistenceManager.saveEntity(starWarsMovie);
        persistenceManager.saveEntity(indianaJonesMovie);

        // If you cascade save, you would not need to do this
        persistenceManager.saveEntity(markHamillActor);
        persistenceManager.saveEntity(harrisonFordActor);

        harrisonFordActor = (Actor)persistenceManager.find(harrisonFordActor);
        persistenceManager.initialize(harrisonFordActor, "movies");
        System.out.println("Harrison Ford has been in "
                + harrisonFordActor.getMovies().size()
                + " movies including "
                + harrisonFordActor.getMovies().get(0).getTitle()
                + " and "
                + harrisonFordActor.getMovies().get(1).getTitle()
                + "!");

        factory.close();
    }
}
