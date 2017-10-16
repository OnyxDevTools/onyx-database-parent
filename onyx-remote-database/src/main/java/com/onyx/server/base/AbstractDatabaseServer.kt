package com.onyx.server.base

import com.onyx.client.AbstractSSLPeer

import java.util.concurrent.CountDownLatch

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This class is to abstract out the basic properties and functions of a server
 */
abstract class AbstractDatabaseServer(open val databaseLocation: String) : AbstractSSLPeer() {

    // Count down latch used to keep the application alive
    private var startStopCountDownLatch: CountDownLatch? = null

    // Server Port
    var port = 8080

    // Cluster instance unique identifier
    var instance = DEFAULT_INSTANCE

    protected var user = "admin"
    protected var password = "admin"


    /**
     * Stop the database server
     *
     * @since 1.0.0
     */
    open fun stop() {
        startStopCountDownLatch?.countDown()
    }

    /**
     * Flag to indicate whether the database is running or not
     *
     * @return Boolean flag running
     * @since 1.0.0
     */
    var isRunning: Boolean = false

    /**
     * Join Thread, in order to keep the server alive
     *
     * @since 1.0.0
     */
    fun join() {
        startStopCountDownLatch = CountDownLatch(1)
        startStopCountDownLatch!!.await()
    }

    /**
     * Set User Credentials
     *
     * @param user     Username
     * @param password Password
     * @since 1.0.0
     */
    fun setCredentials(user: String, password: String) {
        this.user = user
        this.password = password
    }

    companion object {
        private val DEFAULT_INSTANCE = "ONYX_DATABASE"
    }
}
