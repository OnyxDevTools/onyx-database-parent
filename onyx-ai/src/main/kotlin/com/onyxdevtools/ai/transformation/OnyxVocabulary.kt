package com.onyxdevtools.ai.transformation

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.findById
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from

/**
 * Database entity representing a token-to-ID mapping in the vocabulary.
 *
 * This entity is stored in the Onyx Embedded Database and provides persistent
 * storage for vocabulary entries. Each entry contains a unique sequential ID
 * and the corresponding token string.
 *
 * **Database Configuration:**
 * - **File name**: "vocabulary" - stored in vocabulary.onx file
 * - **ID generation**: SEQUENCE - automatically generates sequential IDs
 * - **Indexing**: Token field is indexed for fast lookups
 *
 * The entity extends ManagedEntity to integrate with Onyx Database's
 * persistence and query capabilities.
 *
 * @property id Unique sequential identifier for the vocabulary entry.
 *              Automatically generated using SEQUENCE strategy.
 * @property token The text token string associated with this ID.
 *                Indexed for efficient queries and lookups.
 */
@Entity(fileName = "vocabulary")
data class VocabularyEntry(
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Int = 0,
    @Index
    var token: String = ""
) : ManagedEntity()

/**
 * A persistent vocabulary implementation backed by Onyx Embedded Database.
 *
 * This implementation provides durable storage for vocabulary mappings using
 * Onyx Database as the persistence layer. Unlike in-memory implementations,
 * vocabularies built with this class survive application restarts and can
 * be shared across multiple processes accessing the same database file.
 *
 * **Key Features:**
 * - **Persistent storage**: Vocabulary survives application restarts
 * - **ACID transactions**: Database-backed consistency guarantees
 * - **Fast indexed lookups**: Token field is indexed for O(log n) queries
 * - **Sequential ID assignment**: IDs are generated automatically via database sequences
 * - **Concurrent access**: Database handles concurrent read/write operations
 *
 * **Performance Characteristics:**
 * - **Token-to-ID lookup**: O(log n) via indexed queries
 * - **ID-to-token lookup**: O(1) via primary key access
 * - **Storage**: Persistent on disk with configurable caching
 * - **Memory usage**: Minimal - data stored in database, not memory
 *
 * **Use Cases:**
 * - Large vocabularies that exceed memory capacity
 * - Vocabularies shared across multiple applications
 * - Production environments requiring data durability
 * - Systems needing audit trails of vocabulary changes
 *
 * **Database Setup:**
 * The class automatically initializes an embedded database at the specified path.
 * The database file will be created if it doesn't exist, and the vocabulary
 * schema will be automatically managed by Onyx Database.
 *
 * @param databasePath The file system path where the database will be stored.
 *                     Should be a directory path where the vocabulary.onx file
 *                     will be created or accessed.
 * @see Vocabulary
 * @see MutableVocabulary
 * @see VocabularyEntry
 */
class OnyxVocabulary(databasePath: String) : Vocabulary {

    /** Lazy-initialized persistence manager for database operations */
    private val manager by lazy {
        return@lazy EmbeddedPersistenceManagerFactory(databasePath).apply {
            this.initialize()
        }.persistenceManager
    }

    /**
     * Gets or creates a vocabulary entry for the given token.
     *
     * Performs a database query to find an existing entry. If none exists,
     * creates and saves a new VocabularyEntry with an auto-generated sequential ID.
     *
     * @param token The token string to get or assign an ID for
     * @return The unique database-generated ID for this token
     */
    override fun getId(token: String): Int =
        manager.from<VocabularyEntry>().where("token" eq token).firstOrNull<VocabularyEntry>().let {
            it ?: manager.saveEntity(VocabularyEntry(token = token))
        }.id

    /**
     * Retrieves the token string for a given ID via primary key lookup.
     *
     * Uses the database's primary key index for fast O(1) retrieval.
     *
     * @param id The ID to look up
     * @return The token associated with the ID, or null if not found
     */
    override fun getToken(id: Int): String? = manager.findById<VocabularyEntry>(id)?.token

    /**
     * Finds the ID for a token without creating a new entry.
     *
     * Performs an indexed query on the token field without side effects.
     *
     * @param token The token to search for
     * @return The ID if found, null otherwise
     */
    override fun findId(token: String): Int? = manager.from<VocabularyEntry>().where("token" eq token).firstOrNull<VocabularyEntry>()?.id
    
    /**
     * Ensures a token exists in the vocabulary by calling getId().
     *
     * @param token The token to add if not already present
     */
    override fun addToken(token: String) {
        getId(token)
    }

    /**
     * Returns the total number of vocabulary entries in the database.
     *
     * Performs a COUNT query on the VocabularyEntry table.
     *
     * @return The current vocabulary size
     */
    override val size: Int
        get() = manager.from<VocabularyEntry>().count().toInt()
}
