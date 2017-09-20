package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.relationship.RelationshipReference
import com.onyx.util.ReflectionUtil

fun RelationshipReference.toManagedEntity(context: SchemaContext, clazz:Class<*>, descriptor: EntityDescriptor = context.getDescriptorForEntity(clazz, "")):IManagedEntity? {
    val entity:IManagedEntity = ReflectionUtil.instantiate(clazz) as IManagedEntity
    entity[context, descriptor, descriptor.identifier!!.name] = identifier
    val records = entity.records(context, descriptor = descriptor, partitionId = partitionId)
    return records[identifier]
}