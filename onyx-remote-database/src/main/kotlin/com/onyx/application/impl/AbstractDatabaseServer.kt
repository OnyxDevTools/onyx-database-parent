package com.onyx.application.impl

import com.onyx.network.ssl.impl.AbstractSSLPeer

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This class is to abstract out the basic properties and functions of a server
 */
abstract class AbstractDatabaseServer(open val databaseLocation: String) : AbstractSSLPeer() {

    // Server Port
    var port = 8080

    // Cluster instance unique identifier
    var instance = DEFAULT_INSTANCE

    protected var user = "admin"
    protected var password = "admin"

    /**
     * Flag to indicate whether the database is running or not
     *
     * @return Boolean flag running
     * @since 1.0.0
     */
    var isRunning: Boolean = false

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
