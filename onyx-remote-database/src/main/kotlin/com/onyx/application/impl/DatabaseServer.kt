package com.onyx.application.impl

import com.onyx.application.OnyxServer
import com.onyx.network.ssl.SSLPeer
import com.onyx.network.auth.AuthenticationManager
import com.onyx.entity.SystemUser
import com.onyx.entity.SystemUserRole
import com.onyx.persistence.context.impl.ServerSchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.ServerPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.network.auth.impl.DefaultAuthenticationManager
import com.onyx.cli.CommandLineParser
import com.onyx.network.rmi.OnyxRMIServer
import com.onyx.interactors.encryption.impl.DefaultEncryptionInteractor
import com.onyx.interactors.encryption.EncryptionInteractor
import com.onyx.persistence.IManagedEntity

/**
 * Base Database Server Application.
 *
 *
 * All servers must inherit from this class in order to extend the Onyx Remote Database.  This does not have
 * any logic that is used within the Web Database.
 *
 *
 *
 * DatabaseServer server1 = new DatabaseServer();
 * server1.setPort(8080);
 * server1.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 * server1.start();
 * server1.join();
 *
 *
 * To invoke command line
 * java -cp path_to_my_classes(.jar):onyx-remote-database-(0.0.1)-alpha.jar com.onyx.application.impl.DatabaseServer -l /path/to/database/databaseLocation -port=8080
 *
 * e.x
 * java -cp /Users/Tim Osborn/Dropbox/OnyxSandbox/onyxdb-parent/OnyxDatabaseTests/target/classes/:onyx-remote-database-0.0.1-alpha.jar com.onyx.application.impl.DatabaseServer -l /Users/Tim Osborn/Desktop/database1 -port=8080
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
open class DatabaseServer(override val databaseLocation:String) : AbstractDatabaseServer(databaseLocation), OnyxServer, SSLPeer {

    override var encryption: EncryptionInteractor = DefaultEncryptionInteractor

    // RMI Server.  This is the underlying network io server
    private lateinit var rmiServer: OnyxRMIServer

    protected var persistenceManagerFactory: PersistenceManagerFactory? = null
    private var authenticationManager: AuthenticationManager? = null

    /**
     * Start the database socket server
     *
     * @since 1.0.0
     */
    override fun start() {
        if (!isRunning) {
            this.persistenceManagerFactory = ServerPersistenceManagerFactory(this.databaseLocation)
            this.persistenceManagerFactory?.setCredentials(this.user, this.password)
            this.persistenceManagerFactory?.initialize()

            // Create a default user
            val user = SystemUser()
            user.username = this.user
            user.password = encryption.encrypt(this.password)
            user.role = SystemUserRole.ROLE_ADMIN

            // Default User and password
            this.persistenceManagerFactory?.persistenceManager?.saveEntity<IManagedEntity>(user)
            this.authenticationManager = DefaultAuthenticationManager(persistenceManagerFactory!!.persistenceManager, encryption)

            // Create the RMI Server
            this.rmiServer = OnyxRMIServer()
            this.rmiServer.port = port
            this.copySSLPeerTo(rmiServer)
            this.registerServices()

            this.rmiServer.start()

            // Set the push publisher within the schema context so the query caching mechanism can
            // tell what push clients to send updates to.
            (this.persistenceManagerFactory?.schemaContext as ServerSchemaContext).setPushPublisher(rmiServer)

            isRunning = true
        }
    }

    /**
     * Register services.  This method registers all of the proxy objects and makes them public
     */
    private fun registerServices() {
        // Register the Persistence Manager
        rmiServer.register(PERSISTENCE_MANAGER_SERVICE, this.persistenceManagerFactory!!.persistenceManager, PersistenceManager::class.java)
        rmiServer.register(AUTHENTICATION_MANAGER_SERVICE, this.authenticationManager!!, AuthenticationManager::class.java)
    }

    /**
     * Stop the database server
     *
     * @since 1.0.0
     */
    override fun stop() {
        rmiServer.stop()
        if (persistenceManagerFactory != null) {
            persistenceManagerFactory!!.close()
        }
    }

    /**
     * Join Server.  Have it suspend on a daemon thread
     *
     * @since 1.2.0
     */
    override fun join() = rmiServer.join()

    /**
     * Get persistence manager
     *
     * @return the underlying persistence manager
     * @since 1.2.3
     */
    val persistenceManager: PersistenceManager
        get() = this.persistenceManagerFactory!!.persistenceManager

    companion object {

        val PERSISTENCE_MANAGER_SERVICE = "1"
        val AUTHENTICATION_MANAGER_SERVICE = "2"

        /**
         * Run Database Server Main Method
         *
         *
         * ex:  executable /Database/Location/On/Disk 8080 admin admin
         *
         * @param args Command Line Arguments
         * @throws Exception General Exception
         * @since 1.0.0
         */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @Throws(Exception::class)
        @JvmStatic
        open fun main(args: Array<String>) {

            val commandLineParser = CommandLineParser(args)
            val instance = DatabaseServer(commandLineParser.databaseLocation)
            commandLineParser.configureDatabaseWithCommandLineOptions(instance)

            instance.start()
            instance.join()
        }
    }
}


