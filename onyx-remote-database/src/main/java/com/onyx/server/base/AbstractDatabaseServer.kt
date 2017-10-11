package com.onyx.server.base

import com.onyx.client.AbstractSSLPeer

import java.util.concurrent.CountDownLatch

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This class is to abstract out the basic properties and functions of a server
 */
abstract class AbstractDatabaseServer(open val databaseLocation:String) : AbstractSSLPeer() {

    protected var user = "admin"
    protected var password = "admin"

    // Count down latch used to keep the application alive
    private var startStopCountDownLatch: CountDownLatch? = null

    // Cluster instance unique identifier
    var instance:String = DEFAULT_INSTANCE
    var maxWorkerThreads = 16

    /**
     * Stop the database server
     *
     * @since 1.0.0
     */
    open fun stop() {
        if (startStopCountDownLatch != null) {
            startStopCountDownLatch!!.countDown()
        }
    }

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
