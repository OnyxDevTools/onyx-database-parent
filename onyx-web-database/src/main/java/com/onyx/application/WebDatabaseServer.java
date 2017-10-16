package com.onyx.application;

import com.onyx.application.impl.DatabaseServer;
import com.onyx.cli.WebServerCommandLineParser;
import com.onyx.server.*;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

/**
 * Created by tosborn1 on 2/13/17.
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
public class WebDatabaseServer extends DatabaseServer
{
    // Web Server
    Undertow server;

    // Port for web service
    private int webServicePort = 8080;

    public WebDatabaseServer(@NotNull String databaseLocation) {
        super(databaseLocation);
    }

    /**
     * Run Database Server Main Method
     * <p>
     * ex:  executable /Database/Location/On/Disk 1111 8080 admin admin
     *
     * @param args Command Line Arguments
     * @throws Exception General Exception
     * @since 1.2.0
     */
    public static void main(String[] args) throws Exception {
        WebServerCommandLineParser parser = new WebServerCommandLineParser(args);
        final WebDatabaseServer instance = new WebDatabaseServer(parser.getDatabaseLocation());
        parser.configureDatabaseWithCommandLineOptions(instance);

        instance.start();
        instance.join();
    }

    /**
     * Start the database server and the socket server.  This creates the security configuration, and the needed
     * handlers for the undertow.  It also starts and configures the socket server.
     *
     * @since 1.2.0
     */
    @Override
    public void start()
    {
        super.start();

        // Session Manager
        final SessionManager sessionManager = new InMemorySessionManager("SESSION_MANAGER");
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();

        // Setup Authentication classes
        DatabaseIdentityManager databaseAuthenticationManager = new DatabaseIdentityManager(this.getPersistenceManagerFactory().getPersistenceManager());

        // Persistence Handler
        final PathHandler persistenceHandler = Handlers.path().addPrefixPath("/onyx", new JSONDatabaseMessageListener(this.getPersistenceManagerFactory().getPersistenceManager(), this.getPersistenceManagerFactory().getSchemaContext()));

        // Security Handler
        HttpHandler securityHandler = new AuthenticationCallHandler(persistenceHandler);
        securityHandler = new AuthenticationConstraintHandler(securityHandler);
        final List<AuthenticationMechanism> mechanisms = Collections.singletonList(new BasicAuthenticationMechanism("DATABASE REALM"));
        securityHandler = new AuthenticationMechanismsHandler(securityHandler, mechanisms);
        securityHandler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, databaseAuthenticationManager, securityHandler);

        // Create the base handler
        SessionAttachmentHandler baseHandler = new SessionAttachmentHandler(sessionManager, sessionConfig);
        baseHandler.setNext(securityHandler);

        try {
            server = buildUndertowConfigurationWithHandler(baseHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try
        {
            server.start();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops the socket server and the Web Server
     * @since 1.2.0
     */
    @Override
    public void stop()
    {
        super.stop();
        server.stop();
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
    Undertow buildUndertowConfigurationWithHandler(HttpHandler baseHandler)
    {
        // Build the server configuration
        if (useSSL())
        {
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance(getProtocol());
                sslContext.init(createKeyManagers(getSslKeystoreFilePath(), getSslStorePassword(), getSslKeystorePassword()), createTrustManagers(getSslTrustStoreFilePath(), getSslStorePassword()), new SecureRandom());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            server = Undertow.builder()
                    .addHttpsListener(webServicePort, "0.0.0.0", sslContext)
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build();
        }
        else
        {
            server = Undertow.builder()
                    .addHttpListener(webServicePort, "0.0.0.0")
                    .setHandler(baseHandler)
                    .setBufferSize(4096)
                    .setDirectBuffers(false)
                    .build();
        }

        return server;
    }

    /**
     * Private helper method to determine if SSL is being used.
     * @return Whether the keystore file path is populated
     */
    private boolean useSSL()
    {
        return (getSslKeystoreFilePath() != null && getSslKeystoreFilePath().length() > 0);
    }

    /**
     * Get The Web service port.  This defaults to 8080.
     * @since 1.2.0
     * @return Web Service Port
     */
    @SuppressWarnings("unused")
    public int getWebServicePort() {
        return webServicePort;
    }

    /**
     * Set the web server port. This is different than the #setPort method.  That is used by the socket server.
     * @since 1.2.0
     * @param webServicePort Integer value of valid port
     */
    public void setWebServicePort(int webServicePort) {
        this.webServicePort = webServicePort;
    }
}
