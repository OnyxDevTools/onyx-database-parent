package com.onyx.interactors.record.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.data.PutResult
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.extension.common.ClassMetadata
import com.onyx.extension.common.castTo
import com.onyx.interactors.record.RecordInteractor
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This implementation of the record controller will create a new sequence if the id was not defined
 */
class SequenceRecordInteractor(entityDescriptor: EntityDescriptor, context: SchemaContext) : DefaultRecordInteractor(entityDescriptor, context), RecordInteractor {
    private val sequenceValue = AtomicLong(0)
    private var metadata: MutableMap<Byte, Number>
    private val sequenceLock = DefaultClosureLock()

    init {
        val dataFile = context.getDataFile(entityDescriptor)
        metadata = dataFile.getHashMap(ClassMetadata.BYTE_TYPE, METADATA_MAP_NAME + entityDescriptor.entityClass.name)

        // Initialize the sequence value
        sequenceValue.set((metadata[LAST_SEQUENCE_VALUE]?:0L).toLong())
    }

    /**
     * Save an entity
     *
     * @param entity Entity to save
     * @return Pair of existing reference id and new identifier value
     * @throws OnyxException Error saving entity
     *
     * @since 1.2.3 Added an optimization so it does not do a check if it exist before
     * putting if there are no listeners for persist
     * @since 2.0.0 Optimized to return the old reference value
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun save(entity: IManagedEntity): PutResult {
        autoIncrementSequence(entity)
        return super.save(entity)
    }

    /**
     * Automatically increment the identifier value if it is undefined.  If it is, ensure the maximum sequence value
     * is less than or equal to the id value so it skips unused sequences and does not mess up with the counting.
     *
     * @param entity Entity to determine identifier
     * @since 2.0.0 Ensured this is compatible with any kind of number.
     */
    private fun autoIncrementSequence(entity:IManagedEntity) {
        var identifierValue:Number = entity.identifier(context) as Number? ?: 0L

        sequenceLock.perform {
            when {
                identifierValue.toLong() == 0L -> {
                    identifierValue = sequenceValue.incrementAndGet().castTo(entityDescriptor.identifier!!.type) as Number
                    entity[context, entityDescriptor, entityDescriptor.identifier!!.name] = identifierValue
                    metadata.put(LAST_SEQUENCE_VALUE, identifierValue)
                }
                identifierValue.toLong() > sequenceValue.get() -> {
                    sequenceValue.set(identifierValue.toLong())
                    metadata.put(LAST_SEQUENCE_VALUE, identifierValue)
                }
                else -> {
                }
            }
        }
    }

    companion object {
        private const val LAST_SEQUENCE_VALUE = 1.toByte()
        private const val METADATA_MAP_NAME = "_meta_"
    }
}
