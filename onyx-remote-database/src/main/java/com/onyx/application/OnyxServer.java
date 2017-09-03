package com.onyx.application;

import com.onyx.client.SSLPeer;

/**
 * Onyx Server contract.  This is the interface that all servers must implement.
 *
 *
 * <pre>
 * <code>
 *
 *    OnyxServer server = new DatabaseServer();
 *    server.setPort(8080);
 *    server.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 *    server.start();
 *    server.join();
 *
 * </code>
 * </pre>
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
public interface OnyxServer extends SSLPeer
{
    /**
     * Starts the database server
     * @since 1.0.0
     *
     */
    void start();


    /**
     * Stops the database server
     * @since 1.0.0
     *
     */
    void stop();

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    @SuppressWarnings("unused")
    boolean isRunning();

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    @SuppressWarnings("unused")
    int getPort();

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     * @param port Port Number
     */
    @SuppressWarnings("unused")
    void setPort(int port);

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    void join();

    /**
     * Set Credentials
     * @since 1.0.0
     *
     * @param user Username
     * @param password Password
     */
    @SuppressWarnings({"UnusedParameters", "EmptyMethod", "unused"})
    void setCredentials(String user, String password);

    /**
     * The maximum number of worker threads threads.  Worker threads are used to perform operations that are not related to
     * networking
     *
     * @param maxThreads Number of io threads
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    void setMaxWorkerThreads(int maxThreads);

}
