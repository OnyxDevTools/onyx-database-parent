package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

fun IManagedEntity.getDataFile(context:SchemaContext, descriptor: EntityDescriptor = descriptor(context)) = context.getDataFile(descriptor)

fun IManagedEntity.getDataFile(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context), partitionId:Long) = context.getPartitionDataFile(descriptor, partitionId)

fun IManagedEntity.records(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context), partitionId: Long = 0L):DiskMap<Any, IManagedEntity> = getDataFile(context, descriptor, partitionId).getHashMap(descriptor.entityClass.name, descriptor.identifier!!.loadFactor.toInt()) as DiskMap<Any, IManagedEntity>
