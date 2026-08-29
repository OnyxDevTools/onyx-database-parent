package com.onyx.interactors.record

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.context.SchemaContext
import java.util.WeakHashMap

private val relationshipMutationCoordinators = WeakHashMap<SchemaContext, Any>()

/**
 * Runs one logical row/index mutation while holding every concrete record-store monitor it uses.
 *
 * The stable descriptor key gives partition moves one lock order. Ordinary saves use one target,
 * so unrelated partitions remain independently writable. RecordInteractor monitors are reentrant;
 * query CAS and the lower-level record save/delete methods may safely enter the same target again.
 */
internal fun <T> SchemaContext.withRecordMutationLocks(
    descriptors: Collection<EntityDescriptor>,
    action: () -> T,
): T {
    val targets = descriptors
        .distinctBy(::recordMutationKey)
        .sortedBy(::recordMutationKey)
        .map(::getRecordInteractor)

    fun lock(index: Int): T = if (index >= targets.size) {
        action()
    } else {
        synchronized(targets[index]) { lock(index + 1) }
    }
    return lock(0)
}

internal fun <T> SchemaContext.withRecordMutationLock(
    descriptor: EntityDescriptor,
    action: () -> T,
): T = withRecordMutationLocks(listOf(descriptor), action)

/**
 * Serializes mutations that can discover and lock related entity stores dynamically.
 *
 * The coordinator is acquired before any record monitor. This lets an ordinary save/delete retain
 * its parent monitor through relationship reference and cascade work without two opposing object
 * graphs acquiring parent/child record stores in reverse order. Entities without relationships do
 * not use this coordinator.
 */
internal fun <T> SchemaContext.withRelationshipMutationLock(action: () -> T): T {
    val coordinator = synchronized(relationshipMutationCoordinators) {
        relationshipMutationCoordinators.getOrPut(this) { Any() }
    }
    return synchronized(coordinator) { action() }
}

/** Resolves the concrete descriptor represented by a query collector reference. */
internal fun SchemaContext.descriptorForReference(
    reference: Reference,
    entityClass: Class<*>,
    baseDescriptor: EntityDescriptor,
): EntityDescriptor {
    if (reference.partition == 0L) return baseDescriptor
    val partition = requireNotNull(getPartitionWithId(reference.partition)) {
        "Partition ${reference.partition} disappeared before its record could be mutated"
    }
    return getDescriptorForEntity(entityClass, partition.value)
}

private fun recordMutationKey(descriptor: EntityDescriptor): String {
    val partition = descriptor.partition?.partitionValue.orEmpty()
    return "${descriptor.entityClass.name.length}:${descriptor.entityClass.name}:${partition.length}:$partition"
}
