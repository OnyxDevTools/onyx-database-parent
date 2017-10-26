package com.onyx.application.impl

import com.onyx.cli.WebServerCommandLineParser
import com.onyx.server.*
import io.undertow.Handlers
import io.undertow.Undertow
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

import javax.net.ssl.SSLContext
import java.security.SecureRandom

/**
 * Created by Tim Osborn on 2/13/17.
 *
 * Tim Osborn - 02/13/2017
 *
 * This server implementation extends the base server implementation.  In addition to that it spins up a web server that
 * will run the RestFul Web Services so that it may be utilized by any language.
 *
 * This was extracted from the Remote Server in order to simplify the remote server.  It also was migrated off in order
 * to reduce the amount of dependencies on the remote server.
 *
 * Lastly, the client code was moved out because there is no need for it.  It is recommended to use the swagger
 * generated Restful Web Service clients rather than the built in client.  That code still exist but has been moved
 * to the unit test project along with all its crappy dependencies such as Spring :(.
 *
 */
open class WebDatabaseServer(databaseLocation: String) : DatabaseServer(databaseLocation) {
    // Web Server
    lateinit var server: Undertow

    // Port for web service
    /**
     * Get The Web service port.  This defaults to 8080.
     * @since 1.2.0
     */
    var webServicePort = 8080

    /**
     * Start the database server and the socket server.  This creates the security configuration, and the needed
     * handlers for the undertow.  It also starts and configures the socket server.
     *
     * @since 1.2.0
     */
    override fun start() {
        super.start()

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

        server = try {
            buildUndertowConfigurationWithHandler(baseHandler)
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
        super.stop()
        server.stop()
    }

    /**
     * Build Undertow server and define its handler
     * In addition to that it determines whether the server should be setup for SSL or regular HTTP
     *
     * @param baseHandler HTTP Handler
     *
     * @return Undertow Server Instance
     * @since 1.2.0
     */
    open protected fun buildUndertowConfigurationWithHandler(baseHandler: HttpHandler): Undertow {
        // Build the server configuration
        if (useSSL()) {
            val sslContext: SSLContext
            try {
                sslContext = SSLContext.getInstance(protocol)
                sslContext.init(createKeyManagers(sslKeystoreFilePath!!, sslStorePassword!!, sslKeystorePassword!!), createTrustManagers(sslTrustStoreFilePath!!, sslStorePassword!!), SecureRandom())
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            server = Undertow.builder()
                    .addHttpsListener(webServicePort, "0.0.0.0", sslContext)
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build()
        } else {
            server = Undertow.builder()
                    .addHttpListener(webServicePort, "0.0.0.0")
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build()
        }

        return server
    }

    /**
     * Private helper method to determine if SSL is being used.
     * @return Whether the keystore file path is populated
     */
    private fun useSSL(): Boolean = sslKeystoreFilePath != null && sslKeystoreFilePath!!.isNotEmpty()

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
        open fun main(args: Array<String>) {
            val parser = WebServerCommandLineParser(args)
            val instance = WebDatabaseServer(parser.databaseLocation)
            parser.configureDatabaseWithCommandLineOptions(instance)

            instance.start()
            instance.join()
        }
    }
}
