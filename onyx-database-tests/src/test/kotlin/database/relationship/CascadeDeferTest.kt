package database.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import dev.onyx.server.entities.Actor
import dev.onyx.server.entities.Movie
import database.base.DatabaseBaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.ArrayList
import java.util.HashSet
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class CascadeDeferTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
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
        assertTrue(starWarsAfterSave1.actors!!.isEmpty(), "Relationships should not have been saved because it is deferred")

        // Associate the actors to the movie
        val actorIds = HashSet<Any>()
        actorIds.add(1)
        actorIds.add(2)

        manager.saveRelationshipsForEntity(starWarsMovie, "actors", actorIds)

        // The Persistence Manager did save the actors and associated it to the movie
        val starWarsAfterSave2 = manager.findById<IManagedEntity>(Movie::class.java, starWarsMovie.movieId) as Movie?
        manager.initialize(starWarsAfterSave2!!, "actors")
        assertEquals(2, starWarsAfterSave2.actors!!.size, "Deferred relationships should have persisted 2 entities")
    }

    companion object {
        /**
         * Relationship deferral is un-supported for web persistence manager
         */
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(CacheManagerFactory::class, EmbeddedPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)

    }
}