package com.onyx.client

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Contract for SSL Peer communication
 */
interface SSLPeer {

    /**
     * SSL Protocol defaults to "TLSv1.2"
     * @since 2.0.0 Added to SSL Peer
     */
    var protocol:String

    /**
     * Set for SSL Store Password.  Note, this is different than Keystore Password
     * @since 1.2.0
     */
    var sslStorePassword:String?

    /**
     * Set Keystore file path.  This should contain the location of the JKS Keystore file
     * @since 1.2.0
     */
    var sslKeystoreFilePath: String?

    /**
     * Set for SSL KeysStore Password.
     * @since 1.2.0
     */
    var sslKeystorePassword: String?

    /**
     * Set Trust store file path.  Location of the trust store JKS File.  This should contain
     * a file of the trusted sites that can access your secure endpoint
     * @since 1.2.0
     */
    var sslTrustStoreFilePath: String?

    /**
     * Trust store password
     * @since 1.2.0
     */
    var sslTrustStorePassword: String?

    /**
     * Default implementation of the copy.  Helper method used to copy the SSLPeer properties
     *
     * @since 2.0.0
     */
    fun copySSLPeerTo(peer:SSLPeer) {
        peer.protocol = protocol
        peer.sslStorePassword = sslStorePassword
        peer.sslKeystoreFilePath = sslKeystoreFilePath
        peer.sslKeystorePassword = sslKeystorePassword
        peer.sslTrustStoreFilePath = sslTrustStoreFilePath
        peer.sslTrustStorePassword = sslTrustStorePassword
    }
}
