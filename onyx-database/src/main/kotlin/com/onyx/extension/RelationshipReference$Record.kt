package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.extension.common.instance
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.relationship.data.RelationshipReference

/**
 * Retrieve an entity based on this relationship reference information
 *
 * @param context Schema context outlines where the data files are
 * @param clazz Entity type
 * @param descriptor Entity's metadata
 *
 * @since 2.0.0
 */
fun RelationshipReference.toManagedEntity(context: SchemaContext, clazz:Class<*>, descriptor: EntityDescriptor = context.getDescriptorForEntity(clazz, "")):IManagedEntity? {
    val entity:IManagedEntity = clazz.instance(context.contextId)
    entity[context, descriptor, descriptor.identifier!!.name] = identifier
    val records = entity.records(context, descriptor = descriptor, partitionId = partitionId)
    return records[identifier]
}

fun RelationshipReference.descriptor(context: SchemaContext, clazz: Class<*>, defaultDescriptor: EntityDescriptor?):EntityDescriptor =
    if(defaultDescriptor != null && ((partitionId != 0L && defaultDescriptor.partition?.partitionValue != "") || partitionId == 0L))
        defaultDescriptor
    else if(partitionId == 0L)
        context.getDescriptorForEntity(clazz, "")
    else {
        val partition = context.getPartitionWithId(partitionId)
        context.getDescriptorForEntity(clazz, partition!!.value)
    }

fun RelationshipReference.recordInteractor(context: SchemaContext, clazz: Class<*>, defaultDescriptor: EntityDescriptor? = null):RecordInteractor {
    val descriptor = descriptor(context, clazz, defaultDescriptor)
    return descriptor.recordInteractor()
}