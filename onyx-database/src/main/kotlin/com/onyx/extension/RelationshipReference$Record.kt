package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
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
    val entity:IManagedEntity = ReflectionUtil.instantiate(clazz) as IManagedEntity
    entity[context, descriptor, descriptor.identifier!!.name] = identifier
    val records = entity.records(context, descriptor = descriptor, partitionId = partitionId)
    return records[identifier]
}