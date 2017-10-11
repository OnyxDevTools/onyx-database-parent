package com.onyx.application;

import com.onyx.application.impl.DatabaseServer;
import com.onyx.cli.WebServerCommandLineParser;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.server.DatabaseIdentityManager;
import com.onyx.server.JSONDatabaseMessageListener;
import io.undertow.Handlers;
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

import java.util.Collections;
import java.util.List;


/**
 * Created by tosborn1 on 2/13/17.
 *
 * Tim Osborn - 02/13/2017
 *
 * This web server has no dependencies on the socket server.  It is an empty shell so that you can cluster
 * web services with a single gateway for the remote server.
 *
 * @since 1.2.0
 *
 */
public class WebDatabaseProxyServer extends WebDatabaseServer
{

    public WebDatabaseProxyServer(String databaseLocation) {
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
        WebServerCommandLineParser parser = new WebServerCommandLineParser();
        final DatabaseServer instance = parser.buildDatabaseWithCommandLineOptions(args);

        instance.start();
        instance.join();
    }

    /**
     * Start the database server and the socket server.  This creates the security configuration, and the needed
     * handlers for the undertow.  It also starts and configures the socket server.
     *
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    @Override
    public void start()
    {
        PersistenceManagerFactory remotePersistenceManagerFactory = new RemotePersistenceManagerFactory(this.getDatabaseLocation(), this.getInstance());
        remotePersistenceManagerFactory.setCredentials(this.getUser(), this.getPassword());
        this.setPersistenceManagerFactory(remotePersistenceManagerFactory);

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
    @SuppressWarnings("unused")
    @Override
    public void stop()
    {
        this.getPersistenceManagerFactory().close();
        server.stop();
    }
}

