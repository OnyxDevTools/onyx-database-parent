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
import com.onyxdevtools.ai.data.SequenceEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

@Entity(fileName = "vocabulary")
data class VocabularyEntry(
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Int = 0,
    @Index
    var token: String = ""
) : ManagedEntity()

class OnyxVocabulary(databasePath: String) : Vocabulary {

    private val manager by lazy {
        return@lazy EmbeddedPersistenceManagerFactory(databasePath).apply { initialize() }.persistenceManager
    }

    // ---- L1 in-memory cache ----
    private val tokenToIdCache = ConcurrentHashMap<String, Int>(16_384)
    private val idToTokenCache = ConcurrentHashMap<Int, String>(16_384)

    // Striped locks to prevent duplicate inserts for the same token under contention
    private val lockStripes = Array(64) { ReentrantLock() }
    private fun lockFor(token: String) = lockStripes[abs(token.hashCode()) % lockStripes.size]

    override fun getId(token: String): Int {
        // L1 hit
        tokenToIdCache[token]?.let { return it }

        // Stripe lock per token to avoid duplicate DB inserts
        val lock = lockFor(token)
        lock.lock()
        try {
            // Check cache again after acquiring the lock
            tokenToIdCache[token]?.let { return it }

            // Check DB
            val existing = manager.from<VocabularyEntry>()
                .where("token" eq token)
                .firstOrNull<VocabularyEntry>()

            val id = if (existing != null) {
                existing.id
            } else {
                manager.saveEntity(VocabularyEntry(token = token)).id
            }

            // Update caches
            tokenToIdCache[token] = id
            idToTokenCache[id] = token
            return id
        } finally {
            lock.unlock()
        }
    }

    override fun getToken(id: Int): String? {
        // L1 hit
        idToTokenCache[id]?.let { return it }

        // DB lookup
        val token = manager.findById<VocabularyEntry>(id)?.token ?: return null

        // Update caches
        idToTokenCache[id] = token
        tokenToIdCache[token] = id
        return token
    }

    override fun findId(token: String): Int? {
        // L1 hit
        tokenToIdCache[token]?.let { return it }

        // DB lookup (no create)
        val found = manager.from<VocabularyEntry>()
            .where("token" eq token)
            .firstOrNull<VocabularyEntry>()
            ?: return null

        // Update caches
        tokenToIdCache[token] = found.id
        idToTokenCache[found.id] = token
        return found.id
    }

    override fun addToken(token: String) {
        // Read-through + write-through populate
        getId(token)
    }

    override val size: Int
        get() = manager.from<VocabularyEntry>().count().toInt()

    fun addSequence(sequence: SequenceEntry) = manager.saveEntity(sequence)
}
