package embedded.relationship;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyxdevtools.persist.entities.Actor;
import com.onyxdevtools.persist.entities.Movie;
import embedded.base.BaseTest;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tosborn1 on 3/26/16.
 */
@Category({ EmbeddedDatabaseTests.class })
public class CascadeDeferTest extends BaseTest
{

    @Before
    public void before() throws InitializationException, EntityException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    @Test
    public void testCascadeDefer() throws EntityException
    {
        // Populate the movie data with actors
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

        // Save The movie
        manager.saveEntity(markHamil);
        manager.saveEntity(carrieFisher);
        manager.saveEntity(starWarsMovie);

        // The Persistence Manager did not save the actors and associated it to the movie
        Movie starWarsAfterSave1 = (Movie) manager.findById(Movie.class, starWarsMovie.movieId);
        manager.initialize(starWarsAfterSave1, "actors");
        Assert.assertTrue(starWarsAfterSave1.actors.size() == 0);

        // Associate the actors to the movie
        Set actorIds = new HashSet<>();
        actorIds.add(1);
        actorIds.add(2);

        manager.saveRelationshipsForEntity(starWarsMovie, "actors", actorIds);

        // The Persistence Manager did save the actors and associated it to the movie
        Movie starWarsAfterSave2 = (Movie) manager.findById(Movie.class, starWarsMovie.movieId);
        manager.initialize(starWarsAfterSave2, "actors");
        Assert.assertTrue(starWarsAfterSave2.actors.size() == 2);
    }
}
