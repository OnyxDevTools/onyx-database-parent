package com.onyx.application

import com.onyx.client.SSLPeer
import com.onyx.encryption.EncryptionInteractor

/**
 * Onyx Server contract.  This is the interface that all servers must implement.
 *
 *
 * <pre>
 * `
 *
 * OnyxServer server = new DatabaseServer();
 * server.setPort(8080);
 * server.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 * server.start();
 * server.join();
 *
` *
</pre> *
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
interface OnyxServer : SSLPeer {
    /**
     * Starts the database server
     * @since 1.0.0
     */
    fun start()


    /**
     * Stops the database server
     * @since 1.0.0
     */
    fun stop()

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    val isRunning: Boolean

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     * @param port Port Number
     */
    var port: Int


    var encryption: EncryptionInteractor

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    fun join()

    /**
     * Set Credentials
     * @since 1.0.0
     *
     * @param user Username
     * @param password Password
     */
    fun setCredentials(user: String, password: String)

    /**
     * The maximum number of worker threads threads.  Worker threads are used to perform operations that are not related to
     * networking
     *
     * @param maxThreads Number of io threads
     * @since 1.2.0
     */
    fun setMaxWorkerThreads(maxThreads: Int)

}
