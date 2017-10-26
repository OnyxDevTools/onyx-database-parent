package embedded.relationship

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyxdevtools.server.entities.Actor
import com.onyxdevtools.server.entities.Movie
import embedded.base.BaseTest
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.ArrayList
import java.util.HashSet

/**
 * Created by Tim Osborn on 3/26/16.
 */
@Category(EmbeddedDatabaseTests::class)
class CascadeDeferTest : BaseTest() {

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class)
    fun testCascadeDefer() {
        // Populate the movie data with actors
        val starWarsMovie = Movie()
        starWarsMovie.title = "Star Wars, A new Hope"

        val actors = ArrayList<Actor>()

        val markHamil = Actor()
        markHamil.actorId = 1
        markHamil.firstName = "Mark"
        markHamil.lastName = "Hamil"

        val carrieFisher = Actor()
        carrieFisher.actorId = 2
        carrieFisher.firstName = "Carrie"
        carrieFisher.lastName = "Fisher"

        actors.add(markHamil)
        actors.add(carrieFisher)

        // Save The movie
        manager.saveEntity<IManagedEntity>(markHamil)
        manager.saveEntity<IManagedEntity>(carrieFisher)
        manager.saveEntity<IManagedEntity>(starWarsMovie)

        // The Persistence Manager did not save the actors and associated it to the movie
        val starWarsAfterSave1 = manager.findById<IManagedEntity>(Movie::class.java, starWarsMovie.movieId) as Movie?
        manager.initialize(starWarsAfterSave1!!, "actors")
        Assert.assertTrue(starWarsAfterSave1.actors!!.size == 0)

        // Associate the actors to the movie
        val actorIds = HashSet<Any>()
        actorIds.add(1)
        actorIds.add(2)

        manager.saveRelationshipsForEntity(starWarsMovie, "actors", actorIds)

        // The Persistence Manager did save the actors and associated it to the movie
        val starWarsAfterSave2 = manager.findById<IManagedEntity>(Movie::class.java, starWarsMovie.movieId) as Movie?
        manager.initialize(starWarsAfterSave2!!, "actors")
        Assert.assertTrue(starWarsAfterSave2.actors!!.size == 2)
    }
}
