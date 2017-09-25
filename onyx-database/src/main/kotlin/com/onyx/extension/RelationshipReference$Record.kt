package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.interactors.relationship.data.RelationshipReference
import com.onyx.util.ReflectionUtil

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
    val entity:IManagedEntity = ReflectionUtil.instantiate(clazz)
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

fun RelationshipReference.referenceID(context: SchemaContext, clazz: Class<*>, defaultDescriptor: EntityDescriptor? = null):Long {
    if(referenceId == 0L)
        referenceId = recordInteractor(context, clazz, defaultDescriptor).getReferenceId(identifier!!)
    return referenceId
}

fun <T : Any?> RelationshipReference.attribute(context: SchemaContext, clazz:Class<*>, name:String, defaultDescriptor: EntityDescriptor? = null):T {
    val recordInteractor = recordInteractor(context, clazz, defaultDescriptor)
    val descriptor = descriptor(context, clazz, defaultDescriptor)
    @Suppress("UNCHECKED_CAST")
    return recordInteractor.getAttributeWithReferenceId(descriptor.attributes[name]!!.field, referenceID(context, clazz, descriptor)) as T
}