package com.onyx.application

import com.onyx.cli.WebServerCommandLineParser
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.server.DatabaseIdentityManager
import com.onyx.server.JSONDatabaseMessageListener
import io.undertow.Handlers
import io.undertow.security.api.AuthenticationMechanism
import io.undertow.security.api.AuthenticationMode
import io.undertow.security.handlers.AuthenticationCallHandler
import io.undertow.security.handlers.AuthenticationConstraintHandler
import io.undertow.security.handlers.AuthenticationMechanismsHandler
import io.undertow.security.handlers.SecurityInitialHandler
import io.undertow.security.impl.BasicAuthenticationMechanism
import io.undertow.server.HttpHandler
import io.undertow.server.session.InMemorySessionManager
import io.undertow.server.session.SessionAttachmentHandler
import io.undertow.server.session.SessionCookieConfig


/**
 * Created by tosborn1 on 2/13/17.
 *
 * Tim Osborn - 02/13/2017
 *
 * This web server has no dependencies on the socket server.  It is an empty shell so that you can cluster
 * web services with a single gateway for the remote server.
 *
 * @since 1.2.0
 */
class WebDatabaseProxyServer(databaseLocation: String) : WebDatabaseServer(databaseLocation) {

    /**
     * Start the database server and the socket server.  This creates the security configuration, and the needed
     * handlers for the undertow.  It also starts and configures the socket server.
     *
     * @since 1.2.0
     */
    override fun start() {
        val remotePersistenceManagerFactory = RemotePersistenceManagerFactory(this.databaseLocation, this.instance)
        remotePersistenceManagerFactory.setCredentials(this.user, this.password)
        this.persistenceManagerFactory = remotePersistenceManagerFactory

        // Session Manager
        val sessionManager = InMemorySessionManager("SESSION_MANAGER")
        val sessionConfig = SessionCookieConfig()

        // Setup Authentication classes
        val databaseAuthenticationManager = DatabaseIdentityManager(this.persistenceManagerFactory!!.persistenceManager)

        // Persistence Handler
        val persistenceHandler = Handlers.path().addPrefixPath("/onyx", JSONDatabaseMessageListener(this.persistenceManagerFactory!!.persistenceManager, this.persistenceManagerFactory!!.schemaContext))

        // Security Handler
        var securityHandler: HttpHandler = AuthenticationCallHandler(persistenceHandler)
        securityHandler = AuthenticationConstraintHandler(securityHandler)
        val mechanisms = listOf<AuthenticationMechanism>(BasicAuthenticationMechanism("DATABASE REALM"))
        securityHandler = AuthenticationMechanismsHandler(securityHandler, mechanisms)
        securityHandler = SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, databaseAuthenticationManager, securityHandler)

        // Create the base handler
        val baseHandler = SessionAttachmentHandler(sessionManager, sessionConfig)
        baseHandler.next = securityHandler

        try {
            server = buildUndertowConfigurationWithHandler(baseHandler)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        try {
            server.start()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    /**
     * Stops the socket server and the Web Server
     * @since 1.2.0
     */
    override fun stop() {
        this.persistenceManagerFactory!!.close()
        server.stop()
    }

    companion object {

        /**
         * Run Database Server Main Method
         *
         *
         * ex:  executable /Database/Location/On/Disk 1111 8080 admin admin
         *
         * @param args Command Line Arguments
         * @throws Exception General Exception
         * @since 1.2.0
         */
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {


            val parser = WebServerCommandLineParser(args)
            val instance = WebDatabaseServer(parser.databaseLocation)
            parser.configureDatabaseWithCommandLineOptions(instance)

            instance.start()
            instance.join()
        }
    }
}

