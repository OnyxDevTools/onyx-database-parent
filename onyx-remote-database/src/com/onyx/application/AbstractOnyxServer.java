package com.onyx.application;

import com.onyx.config.ContextFactory;
import com.onyx.exception.InitializationException;
import com.onyx.map.serializer.SocketBuffer;
import com.onyx.server.DatabaseConnectionListener;
import com.onyx.server.DatabaseHandshakeHandler;
import com.onyx.util.EncryptionUtil;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import org.xnio.Options;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static io.undertow.Handlers.resource;

/**
 * AbstractOnyxServer contains the base level operations for starting, configuring and stopping a server.
 * All servers must inherit from this class in order to extend the Onyx Remote Database.  This does not have
 * any logic that is used within the Web Database.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 *
 */
abstract class AbstractOnyxServer implements OnyxServer {

    protected int port = 8080;
    protected Undertow server = null;
    protected String location = null;
    protected String fileServerResourceLocation = System.getProperty("user.home") + File.separator +  "fileserver";

    private String credentials;

    /**
     * Server Context Factory
     * @since 1.0.0
     */
    protected ContextFactory serverContextFactory = null;

    /**
     * Server State is either started or stopped
     * @since 1.0.0
     */
    public enum ServerState {
        START,
        STOP
    }

    // Default Server State to STOP
    protected ServerState state = ServerState.STOP;

    /**
     * Start the database server
     * @since 1.0.0
     */
    public final void start()
    {
        if (state != ServerState.START)
        {
            try
            {

                server = this.configure().build();
                try
                {
                    server.start();
                } catch (Exception e)
                {
                    throw new RuntimeException(e);
                }

                LockSupport.parkNanos(50000);

                state = ServerState.START;


            } catch (Throwable t)
            {
                t.printStackTrace(System.err);
            }
        }
    }

    /**
     * Configure Server Builder
     *
     * @since 1.0.0
     * @return Server Configuration configured with path handlers for the server.
     * @throws InitializationException when failing to configure database
     */
    protected Undertow.Builder configure() throws InitializationException
    {

        SocketBuffer.initialize(null);

        // Session Manager
        final SessionManager sessionManager = new InMemorySessionManager("SESSION_MANAGER");
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();

        // Persistence Handler
        PathHandler persistenceHandler = null;

        HttpHandler baseHandler = null;

        int bufferSize = 1024;

        // Security Handler
        if(identityManager != null)
        {
            persistenceHandler = getBasePathHandlers();
            HttpHandler securityHandler = new AuthenticationCallHandler(persistenceHandler);
            securityHandler = new AuthenticationConstraintHandler(securityHandler);
            final List<AuthenticationMechanism> mechanisms = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("DATABASE REALM"));
            securityHandler = new AuthenticationMechanismsHandler(securityHandler, mechanisms);
            securityHandler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, securityHandler);
            baseHandler = new SessionAttachmentHandler(sessionManager, sessionConfig);
            ((SessionAttachmentHandler)baseHandler).setNext(securityHandler);
        }
        else
        {
            persistenceHandler = getBasePathHandlers();

            bufferSize = 512;
            baseHandler = persistenceHandler;
        }

        return Undertow.builder()
                .addHttpListener(getPort(), "0.0.0.0")
                .setHandler(baseHandler)
                .setBufferSize(bufferSize)
                .setDirectBuffers(false)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setSocketOption(Options.TCP_NODELAY, true)
                .setServerOption(Options.REUSE_ADDRESSES, true)
                .setServerOption(Options.TCP_NODELAY, true)
                .setServerOption(Options.KEEP_ALIVE, true);
    }

    /**
     * Get Base HttpHandlers()
     *
     * @since 1.0.0
     * @return Base Path Handler for Server
     */
    protected PathHandler getBasePathHandlers()
    {
        ResourceHandler fileServerHandler = resource(new FileResourceManager(new File(System.getProperty("user.home")), 100))
                .setDirectoryListingEnabled(true);


        if(identityManager != null)
        {
            return Handlers.path()
                    .addPrefixPath("/onyx", new DatabaseHandshakeHandler(new DatabaseConnectionListener(null, null, serverContextFactory)))
                    .addPrefixPath("/files", fileServerHandler);
        }
        else
        {
            return Handlers.path().addPrefixPath("/onyx", new WebSocketProtocolHandshakeHandler(new DatabaseConnectionListener(null, null, serverContextFactory)))
            .addPrefixPath("/files", fileServerHandler);
        }
    }

    /**
     * Stop the database server
     * @since 1.0.0
     */
    @Override
    public void stop()
    {
        try
        {
            server.stop();
            LockSupport.parkNanos(50000);
        } catch (Exception ignore)
        {

        } finally
        {
            state = ServerState.STOP;
        }
    }

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    @Override
    public boolean isRunning()
    {
        return (server != null && state == ServerState.START);
    }

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    @Override
    public int getPort()
    {
        return port;
    }

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     * @param port Port Number
     */
    @Override
    public void setPort(int port)
    {
        this.port = port;
    }

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    public void join()
    {
        while (isRunning())
            LockSupport.parkNanos(1000);
    }

    /**
     * Set Credentials
     * @since 1.0.0
     * @param user Username
     * @param password Password
     */
    @Override
    public final void setCredentials(String user, String password)
    {
        this.credentials = user + ":" + EncryptionUtil.encrypt(password);
    }

    protected IdentityManager identityManager = null;

    /**
     * Set authentication filter / Identity Manager
     *
     * @since 1.0.0
     * @param identityManager Security filter
     */
    public void setAuthenticationIdentityManager(IdentityManager identityManager)
    {
        this.identityManager = identityManager;
    }

    /**
     * File Server resource location getter.  This contains the directory in which the files for the server are located
     *
     * @since 1.0.0
     * @return File Server Resource Location
     */
    public String getFileServerResourceLocation() {
        return fileServerResourceLocation;
    }

    /**
     * Set File Server resource location.  Specify where files for the file server are located
     * @param fileServerResourceLocation Local File Server Resources for file server
     * @since 1.0.0
     */
    public void setFileServerResourceLocation(String fileServerResourceLocation) {
        this.fileServerResourceLocation = fileServerResourceLocation;
    }

    /**
     * Get Local Data Store location
     *
     * @since 1.0.0
     * @return Local Path to database
     */
    public String getLocation() {
        return location;
    }

    /**
     * Set Local Data Store location
     * @since 1.0.0
     * @param location Local path to database
     */
    public void setLocation(String location) {
        this.location = location;
    }
}
