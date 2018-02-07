package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Get Data file for this entity where it is stored or should be stored
 *
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's metadata
 * @since 2.0.0
 */
fun IManagedEntity.getDataFile(context:SchemaContext, descriptor: EntityDescriptor = descriptor(context)) = context.getDataFile(descriptor)

/**
 * Get Data file for this entity where it is stored or should be stored with a specific partition id
 *
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's metadata
 * @param partitionId SystemPartitionEntry primary key
 * @since 2.0.0
 */
fun IManagedEntity.getDataFile(context: SchemaContext, partitionId:Long = partitionId(context), descriptor: EntityDescriptor = descriptor(context)) = context.getPartitionDataFile(descriptor, partitionId)

/**
 * Get record map of the entities that are stored for this matching entity
 * @param context Schema context outlines where the data files are
 * @param descriptor Entity's metadata
 * @param partitionId SystemPartitionEntry primary key
 * @since 2.0.0
 */
fun IManagedEntity.records(context: SchemaContext, partitionId: Long = partitionId(context), descriptor: EntityDescriptor = descriptor(context)):DiskMap<Any, IManagedEntity> = getDataFile(context, partitionId, descriptor).getHashMap(descriptor.identifier!!.type, descriptor.entityClass.name, descriptor.identifier!!.loadFactor.toInt())
