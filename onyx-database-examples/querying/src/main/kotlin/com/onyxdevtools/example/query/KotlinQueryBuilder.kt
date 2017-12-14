package com.onyxdevtools.example.query

import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyxdevtools.example.querying.entities.Division
import com.onyxdevtools.example.querying.entities.Player
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

        getDivisions()
        getAFCDivision()
        getBroncosReceivers()
        getBroncoPlayers()

        factory.close() // close the factory so that we can use it again

    }

    private fun getDivisions() = manager.from(Division::class).list<Division>()

    private fun getAFCDivision():Division = manager.from(Division::class).where("name" startsWith "AFC").first()

    private fun getBroncosReceivers():List<Player> =
            manager.from(Player::class)
                    .where( ((("team.teamName" eq "Broncos")
                            or ("team.teamName" eq "Raiders"))
                            or (
                                ("position" eq "WR")
                                        or (("position" eq "QB") and ("team.teamName" eq "Patriots"))
                            )
                            )).and("stats.season.year" lte -1)
                    .lazy<Player>().filter { it.position == "QB" }

    private fun getBroncoPlayers():List<Player> =
            manager.from(Player::class)
                    .where( ("team.teamName" eq "Broncos") and !("position" eq "WR") )
                    .list()

}