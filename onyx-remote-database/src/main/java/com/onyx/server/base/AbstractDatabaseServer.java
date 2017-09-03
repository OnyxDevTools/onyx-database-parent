package com.onyx.server.base;

import com.onyx.client.AbstractSSLPeer;

import java.util.concurrent.CountDownLatch;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This class is to abstract out the basic properties and functions of a server
 */
public abstract class AbstractDatabaseServer extends AbstractSSLPeer {

    private static final String DEFAULT_INSTANCE = "ONYX_DATABASE";

    // Count down latch used to keep the application alive
    private CountDownLatch startStopCountDownLatch;

    // Server Port
    protected int port = 8080;

    // Server State
    protected ServerState state = ServerState.STOPPED;

    // Cluster instance unique identifier
    protected String instance = DEFAULT_INSTANCE;

    // Username
    protected String user = "admin";

    // User password
    protected String password = "admin";

    // Maximum number of threads that process data
    protected int maxWorkerThreads = 16;

    // Location to find database store.
    protected String location;

    /**
     * Stop the database server
     *
     * @since 1.0.0
     */
    public void stop() {
        try {

            if (startStopCountDownLatch != null) {
                startStopCountDownLatch.countDown();
            }
        } catch (Exception ignore) {
        } finally {
            state = ServerState.STOPPED;
        }
    }

    /**
     * Flag to indicate whether the database is running or not
     *
     * @return Boolean flag running
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public boolean isRunning() {
        return (state == ServerState.RUNNING);
    }

    /**
     * Get Database port
     *
     * @return Port Number
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public int getPort() {
        return port;
    }

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @param port Port Number
     * @since 1.0.0
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Join Thread, in order to keep the server alive
     *
     * @since 1.0.0
     */
    public void join() {
        try {
            startStopCountDownLatch = new CountDownLatch(1);
            startStopCountDownLatch.await();
        } catch (InterruptedException ignore) {
        }
    }

    /**
     * Setter for Max Number of worker threads
     *
     * @param maxThreads Number of io threads
     * @since 1.2.0
     */
    public void setMaxWorkerThreads(int maxThreads) {
        this.maxWorkerThreads = maxThreads;
    }

    /**
     * Set Database Location
     *
     * @param databaseLocation Local location of the database
     * @since 1.0.0
     */
    public void setDatabaseLocation(String databaseLocation) {
        this.location = databaseLocation;
    }

    /**
     * Set User Credentials
     *
     * @param user     Username
     * @param password Password
     * @since 1.0.0
     */
    public void setCredentials(String user, String password) {
        this.user = user;
        this.password = password;
    }

    /**
     * Get instance name
     * @return Get the instance name of the database
     */
    @SuppressWarnings("unused")
    public String getInstance() {
        return instance;
    }

    /**
     * Set the instance name of the database
     * @param instance Unique instance name
     */
    public void setInstance(String instance) {
        this.instance = instance;
    }
}
