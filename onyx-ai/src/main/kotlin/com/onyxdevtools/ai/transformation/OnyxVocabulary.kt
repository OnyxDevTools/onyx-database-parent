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

@Entity(fileName = "vocabulary")
data class VocabularyEntry(
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Int = 0,
    @Index
    var token: String = ""
) : ManagedEntity()

/**
 * A vocabulary that is stored in an Onyx Embedded Database.
 */
class OnyxVocabulary(databasePath: String) : Vocabulary {

    private val manager by lazy {
        return@lazy EmbeddedPersistenceManagerFactory(databasePath).apply {
            this.initialize()
        }.persistenceManager
    }

    override fun getId(token: String): Int =
        manager.from<VocabularyEntry>().where("token" eq token).firstOrNull<VocabularyEntry>().let {
            it ?: manager.saveEntity(VocabularyEntry(token = token))
        }.id

    override fun getToken(id: Int): String? = manager.findById<VocabularyEntry>(id)?.token

    override fun findId(token: String): Int? = manager.from<VocabularyEntry>().where("token" eq token).firstOrNull<VocabularyEntry>()?.id
    override val size: Int
        get() = manager.from<VocabularyEntry>().count().toInt()
}
