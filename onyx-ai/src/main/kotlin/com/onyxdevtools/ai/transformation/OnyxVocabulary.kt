package com.onyxdevtools.ai.transformation

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.findById
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

@Entity(fileName = "vocabulary")
data class VocabularyEntry(
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Int = 0,
    @Index
    var token: String = "",
    @Attribute
    var frequency: Long = 0
) : ManagedEntity()

class OnyxVocabulary(databasePath: String) : Vocabulary {

    private val manager: PersistenceManager by lazy {
        EmbeddedPersistenceManagerFactory(databasePath).apply { initialize() }.persistenceManager
    }

    // ---- L1 in-memory cache ----
    private val tokenToIdCache = ConcurrentHashMap<String, Int>(16_384)
    private val idToTokenCache = ConcurrentHashMap<Int, String>(16_384)

    // Per-token striped locks
    private val lockStripes = Array(64) { ReentrantLock() }
    private fun lockFor(token: String) = lockStripes[abs(token.hashCode()) % lockStripes.size]

    // Global commit lock to serialize commits vs. writers
    private val commitLock = ReentrantLock()

    override fun getId(token: String): Int {
        tokenToIdCache[token]?.let { return it }

        // Avoid racing with commit
        val commitHeld = commitLock.tryLock()
        try {
            // Stripe lock per token to avoid duplicate inserts
            val lock = lockFor(token)
            lock.lock()
            try {
                tokenToIdCache[token]?.let { return it }

                val existing = manager.from<VocabularyEntry>()
                    .where("token" eq token)
                    .firstOrNull<VocabularyEntry>()

                val id = existing?.id ?: manager.saveEntity(VocabularyEntry(token = token)).id

                tokenToIdCache[token] = id
                idToTokenCache[id] = token
                return id
            } finally {
                lock.unlock()
            }
        } finally {
            if (commitHeld) commitLock.unlock()
        }
    }

    override fun getToken(id: Int): String? {
        idToTokenCache[id]?.let { return it }

        val token = manager.findById<VocabularyEntry>(id)?.token ?: return null

        idToTokenCache[id] = token
        tokenToIdCache[token] = id
        return token
    }

    override fun findId(token: String): Int? {
        tokenToIdCache[token]?.let { return it }

        val found = manager.from<VocabularyEntry>()
            .where("token" eq token)
            .firstOrNull<VocabularyEntry>()
            ?: return null

        tokenToIdCache[token] = found.id
        idToTokenCache[found.id] = token
        return found.id
    }

    override fun addToken(token: String) {
        getId(token)
    }

    override val size: Int
        get() = manager.from<VocabularyEntry>().count().toInt()

    /**
     * Increment token frequency; creates token if missing.
     */
    fun incrementFrequency(token: String, amount: Long = 1) {
        val id = getId(token)
        val entry = manager.findById<VocabularyEntry>(id)!!
        entry.frequency += amount
        manager.saveEntity(entry)
    }

    /**
     * Commit the vocabulary:
     *  - Keep only the top [maxTokens] by frequency (desc), tie-break by token (asc).
     *  - Delete all rows, reinsert the top set in order so SEQUENCE assigns fresh IDs.
     *  - Clear and rebuild L1 caches.
     *
     * NOTE: If you must reset the underlying sequence to start at 1, call the appropriate
     * store-specific reset here after delete (not shown).
     */
    override fun commit(maxTokens: Int) {
        commitLock.lock()
        try {
            // Snapshot all
            val all = manager.from<VocabularyEntry>().list<VocabularyEntry>()

            // Select top N deterministically
            val top = all
                .sortedWith(compareByDescending<VocabularyEntry> { it.frequency }.thenBy { it.token })
                .take(maxTokens)

            // Wipe table
            all.forEach { manager.delete(it) }

            // Optional: reset sequence here if your store supports it
            // manager.resetIdSequence(VocabularyEntry::class) // (pseudo)

            // Clear caches
            tokenToIdCache.clear()
            idToTokenCache.clear()

            // Reinsert in rank order to get sequential IDs
            top.forEach { e ->
                val saved = manager.saveEntity(VocabularyEntry(token = e.token, frequency = e.frequency))
                tokenToIdCache[e.token] = saved.id
                idToTokenCache[saved.id] = e.token
            }
        } finally {
            commitLock.unlock()
        }
    }
}
