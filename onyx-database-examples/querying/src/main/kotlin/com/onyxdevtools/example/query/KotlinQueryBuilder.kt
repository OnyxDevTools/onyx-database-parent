package com.onyxdevtools.example.query

import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyxdevtools.example.querying.entities.Division
import com.onyxdevtools.example.querying.entities.Player
import com.onyxdevtools.example.querying.entities.Stats
import java.io.File

object KotlinQueryBuilder {

    private val factory:PersistenceManagerFactory by lazy {
        val pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
            File.separatorChar + "querying-db.oxd"

        // get an instance of the persistenceManager
        val factory = EmbeddedPersistenceManagerFactory(pathToOnyxDB)
        factory.setCredentials("onyx-user", "SavingDataIsFun!")
        factory.initialize()
        return@lazy factory
    }

    private val manager: PersistenceManager by lazy { factory.persistenceManager }

    @JvmStatic
    fun demo() {

        basicQueryExample()
        compoundQueryExample()
        findExample()
        sortExample()
        limitExample()
        lazyExample()
        updateExample()
        deleteExample()
        countExample()
        listenerExample()
        forEachExample()
        mapExample()
        filterExample()

        factory.close() // close the factory so that we can use it again

    }

    private fun basicQueryExample() =
            manager.from(Division::class).list<Division>()

    private fun compoundQueryExample() =
            manager.from(Stats::class)
                    .where(
                            ("rushingYards" lt 0)
                                    and ("passingYards" gt 3)
                                    and (("player.position" eq "QB")
                                        or ("player.firstName" cont "a")
                                    )
                    )
                    .list<Stats>()

    private fun findExample() =
            manager.from(Player::class)
                    .where(
                        ("firstName" eq "Payton")
                    )
                    .firstOrNull<Player>()

    private fun sortExample() =
            manager.from(Player::class)
                    .where(
                            ("position" eq "QB")
                    )
                    .orderBy("firstName".desc())
                    .list<Player>()

    private fun limitExample() =
            manager.from(Player::class)
                    .where(
                        ("position" eq "RB")
                    )
                    .orderBy("firstName")
                    .limit(10)
                    .list<Player>()

    private fun lazyExample() =
            manager.from(Player::class)
                    .where(
                            ("position" eq "QB")
                    )
                    .lazy<Player>()

    private fun updateExample() =
            manager.from(Stats::class)
                    .where(
                            ("player.firstName" eq "Tom")
                            and ("player.lastName" eq "Brady")
                    )
                    .set("rushingYards" to "-1000")
                    .update()

    private fun deleteExample() =
            manager.from(Player::class)
                    .where(
                        ("position" eq "QB")
                        and ("firstName" eq "Payton")
                        and ("lastName" eq "Manning")
                    )
                    .limit(1)
                    .delete()

    private fun countExample() =
            manager.from(Player::class)
                    .count()

    private fun forEachExample() =
            manager.from(Division::class).forEach<Division> { println("Division: ${it.name}")}

    private fun mapExample():List<String> =
            manager.from(Division::class).map<Division, String> { it.name }

    private fun filterExample() =
            manager.from(Division::class).filter<Division> { it.name.endsWith("West") }

    private fun listenerExample() {
        val listener = manager.from(Player::class)
                              .onItemUpdated<Player> {
                                  println("Player updated ${it.firstName} ${it.lastName}")
                              }.listen()

        val derekCarr = manager.from(Player::class).where("firstName" eq "Derick").and("lastName" eq "Carr").first<Player>()
        derekCarr.active = false
        manager.saveEntity(derekCarr)

        listener.stopListening()
    }
}