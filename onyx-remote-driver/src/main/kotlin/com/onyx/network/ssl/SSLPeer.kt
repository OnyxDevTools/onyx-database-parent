package com.onyx.network.ssl

import com.onyx.network.auth.impl.NetworkPeer
import java.security.KeyStore
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * Created by Tim Osborn on 2/13/17.
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
    fun copySSLPeerTo(peer: SSLPeer) {
        peer.protocol = protocol
        peer.sslStorePassword = sslStorePassword
        peer.sslKeystoreFilePath = sslKeystoreFilePath
        peer.sslKeystorePassword = sslKeystorePassword
        peer.sslTrustStoreFilePath = sslTrustStoreFilePath
        peer.sslTrustStorePassword = sslTrustStorePassword
    }

    /**
     * Create Key Managers
     *
     * @param filepath File path of JKS file
     * @param keystorePassword JSK file password
     * @param keyPassword Store password
     * @return Array of Key managers
     * @since 1.2.0
     * @throws Exception Invalid SSL Settings and or JKS files
     */
    @Throws(Exception::class)
    fun createKeyManagers(filepath: String, keystorePassword: String, keyPassword: String): Array<KeyManager> {
        val keyStore = KeyStore.getInstance("JKS")
        val keyStoreIS = NetworkPeer::class.java.classLoader.getResourceAsStream(filepath)
        keyStoreIS.use {
            keyStore.load(it, keystorePassword.toCharArray())
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, keyPassword.toCharArray())
        return kmf.keyManagers
    }

    /**
     * Create Trust managers
     * @param filepath Trust store JKS file path
     * @param trustStorePassword Password for the JKS File
     * @return Array of trust managers
     * @throws Exception Invalid SSK Settings or JKS file
     *
     * @since 1.2.0
     */
    @Throws(Exception::class)
    fun createTrustManagers(filepath: String, trustStorePassword: String): Array<TrustManager> {
        val trustStore = KeyStore.getInstance("JKS")
        val trustStoreIS = NetworkPeer::class.java.classLoader.getResourceAsStream(filepath)
        trustStoreIS.use {
            trustStore.load(it, trustStorePassword.toCharArray())
        }
        val trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustFactory.init(trustStore)
        return trustFactory.trustManagers
    }
}
