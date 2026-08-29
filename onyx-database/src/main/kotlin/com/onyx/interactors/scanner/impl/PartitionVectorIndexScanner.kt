package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode

/** Executes [VectorIndexScanner] routing across every concrete entity partition. */
class PartitionVectorIndexScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : VectorIndexScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    override fun scan(): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) return super.scan()
        val (matching, descriptors) = scanAllPartitions()
        return finishScan(matching) { reference -> descriptors.getValue(reference.partition) }
    }

    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) return super.scan(existingValues)
        val (matching, descriptors) = scanAllPartitions(existingValues)
        return finishScan(matching) { reference -> descriptors.getValue(reference.partition) }
    }

    private fun scanAllPartitions(
        existingValues: Set<Reference>? = null
    ): Pair<LinkedHashSet<Reference>, Map<Long, EntityDescriptor>> {
        val currentContext = Contexts.get(contextId)!!
        val descriptors = LinkedHashMap<Long, EntityDescriptor>()
        val matching = LinkedHashSet<Reference>()
        currentContext.getAllPartitions(requireNotNull(query.entityType)).forEach { partition ->
            val partitionDescriptor = currentContext.getDescriptorForEntity(query.entityType, partition.value)
            descriptors[partition.index] = partitionDescriptor
            matching += scanDescriptor(partitionDescriptor, partition.index, existingValues)
        }
        return matching to descriptors
    }
}
