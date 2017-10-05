package com.onyx.persistence.factory.impl

import com.onyx.exception.InitializationException
import com.onyx.extension.common.catchAll
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.encryption.DefaultEncryptionInteractor
import com.onyx.encryption.EncryptionInteractor

import java.io.*
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets

/**
 * Persistence manager factory for an embedded Java based database.
 *
 * This is responsible for configuring a database that does persist to disk and is not accessible to external API or Network calls.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory("/MyDatabaseLocation");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in embedded database
 *
 * or ...
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory("/MyDatabaseLocation");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager();
 *
 * factory.close(); //Close the in memory database
 *
 * or... Kotlin
 *
 * val factory = EmbeddedPersistenceManagerFactory("/MyDatabaseLocation")
 * factory.setCredentials("username", "password")
 * factory.initialize()
 *
 * val manager = factory.persistenceManager
 * factory.close()
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
open class EmbeddedPersistenceManagerFactory @JvmOverloads constructor(override val databaseLocation: String, val instance: String = databaseLocation) : PersistenceManagerFactory {

    override var encryption: EncryptionInteractor = DefaultEncryptionInteractor

    /**
     * Constructor that ensures safe shutdown
     * @since 1.0.0
     */
    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() = close()
        })
    }

    // region Properties

    private var fileChannelLock: FileChannel? = null
    private var lock: java.nio.channels.FileLock? = null

    protected var user = "admin"
    protected var password = "admin"

    // Enable history journaling ot keep a transaction history
    var isEnableJournaling = false

    // endregion

    //region Override Properties

    @Suppress("LeakingThis")
    override var schemaContext: SchemaContext = DefaultSchemaContext(instance, databaseLocation)

    override val credentials: String by lazy { this.user + ":" + encryption.encrypt(this.password) }

    // Setup Persistence Manager

    override val persistenceManager: PersistenceManager by lazy<PersistenceManager> {
        val manager = EmbeddedPersistenceManager(schemaContext)
        manager.isJournalingEnabled = this.isEnableJournaling
        manager.context = schemaContext
        manager.context.systemPersistenceManager = manager
        return@lazy manager
    }

    // endregion

    // region Override Methods

    /**
     * Set Credentials. Set username and password
     *
     * @since 1.0.0
     * @param user Set username
     * @param password Set Password
     */
    override fun setCredentials(user: String, password: String) {
        this.user = user
        this.password = password
    }

    /**
     * Initialize the database connection and storage mechanisms
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials or a lock on the database already exists.
     */
    @Throws(InitializationException::class)
    override fun initialize() = try {

        // Ensure the database file exists
        val databaseDirectory = File(this.databaseLocation)

        if (!databaseDirectory.exists()) {
            databaseDirectory.mkdirs()
            createCredentialsFile()
        }

        acquireLock()

        if (!databaseDirectory.canWrite()) {
            releaseLock()
            throw InitializationException(InitializationException.DATABASE_FILE_PERMISSION_ERROR)
        }

        if (!checkCredentials()) {
            releaseLock()
            throw InitializationException(InitializationException.INVALID_CREDENTIALS)
        }

        this.persistenceManager
        schemaContext.start()
    } catch (e: OverlappingFileLockException) {
        releaseLock()
        throw InitializationException(InitializationException.DATABASE_LOCKED)
    } catch (e: IOException) {
        releaseLock()
        throw InitializationException(InitializationException.UNKNOWN_EXCEPTION, e)
    }

    /**
     * Safe shutdown of database
     * @since 1.0.0
     */
    override fun close() {
        schemaContext.shutdown()
        releaseLock()
    }

    // endregion

    // region Private Methods

    /**
     * Acquire Database Lock
     *
     * @since 1.0.0
     * @throws IOException Error acquiring database lock
     */
    private fun acquireLock() {
        val lockFile = File(this.databaseLocation + "/lock")

        if (!lockFile.exists()) {
            lockFile.createNewFile()
        }

        fileChannelLock = RandomAccessFile(lockFile, "rw").channel
        lock = fileChannelLock!!.tryLock()
    }

    /**
     * Release Database Lock
     *
     * @since 1.0.0
     */
    private fun releaseLock() {
        if (lock != null) {
            try {
                lock!!.release()
            } catch (e: ClosedChannelException){} finally {
                catchAll {
                    fileChannelLock!!.close()
                }
            }
        }
    }

    /**
     * Check to see if credentials in the database match configuration
     *
     * @since 1.0.0
     * @return Indicator to see if the factory's credentials are valid
     */
    @Throws(InitializationException::class)
    private fun checkCredentials(): Boolean {
        val databaseFile = File(databaseLocation)
        if (!databaseFile.exists()) {
            return true
        }
        return try {
            // Read the credentials and compare
            val credFile = File(databaseLocation + File.separator + CREDENTIALS_FILE)
            val credentials = String(readContentIntoByteArray(credFile), StandardCharsets.UTF_16)
            credentials == encryptCredentials()
        } catch (e: IOException) {
            throw InitializationException(InitializationException.UNKNOWN_EXCEPTION, e)
        }
    }

    /**
     * Encrypt Credentials
     *
     * @since 1.0.0
     * @return Encrypted Credentials
     */
    @Throws(InitializationException::class)
    private fun encryptCredentials(): String = try {
        encryption.encrypt(user + password)!!
    } catch (e: Exception) {
        throw InitializationException(InitializationException.UNKNOWN_EXCEPTION, e)
    }

    /**
     * Create Credentials File
     *
     * @throws InitializationException Cannot create or write to credentials file
     * @since 1.0.0
     */
    @Throws(InitializationException::class)
    private fun createCredentialsFile() {
        var fileStream: FileOutputStream? = null
        try {
            //create a temporary file
            val credentialsFile = File(databaseLocation + File.separator + CREDENTIALS_FILE)
            credentialsFile.parentFile.mkdirs()
            credentialsFile.createNewFile()
            fileStream = FileOutputStream(credentialsFile)
            fileStream.write(encryptCredentials().toByteArray(StandardCharsets.UTF_16))
        } catch (e: IOException) {
            throw InitializationException(InitializationException.UNKNOWN_EXCEPTION, e)
        } finally {
            // Close the fileStream regardless of what happens...
            fileStream?.close()
        }
    }

    // endregion

    companion object {

        private val CREDENTIALS_FILE = "tmp"

        @JvmField
        val DEFAULT_INSTANCE = "ONYX_DATABASE"

        /**
         * Helper method for reading they contents of a file into a byte array
         *
         * @param file File to read
         * @return byte array of contents
         * @throws IOException Failure to read file
         */
        private fun readContentIntoByteArray(file: File): ByteArray {
            val fileInputStream = FileInputStream(file)
            val bFile = ByteArray(file.length().toInt())
            //convert file into array of bytes
            fileInputStream.read(bFile)
            fileInputStream.close()
            return bFile
        }
    }
}